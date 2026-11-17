package com.visionqc.backend.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 文件存储服务：负责文件系统层面的读写，与数据库事务解耦。
 *
 * 一致性设计（任务书第十节）：
 * - 上传先写 staging 暂存目录，数据库记录以 STAGED 状态提交；
 * - 提交后执行原子 rename 到正式目录，再把状态更新为 ACTIVE；
 * - 任一步失败：数据库标记 FAILED、清理暂存文件，由定时任务兜底清理孤儿文件。
 * - 正式文件路径 = {root}/final/{batchId}/{fileKey}，fileKey 为服务端 UUID，杜绝路径穿越。
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    private final Path stagingDir;
    private final Path finalDir;

    public FileStorageService(@Value("${app.storage-root}") String storageRoot) {
        Path root = Paths.get(storageRoot).toAbsolutePath().normalize();
        this.stagingDir = root.resolve("staging");
        this.finalDir = root.resolve("final");
    }

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(stagingDir);
        Files.createDirectories(finalDir);
        log.info("Storage initialized: staging={}, final={}", stagingDir, finalDir);
    }

    /** 保存上传流到暂存文件，返回暂存路径；调用方负责后续校验 */
    public Path saveToStaging(InputStream in, String tempName) throws IOException {
        Path temp = stagingDir.resolve(tempName).normalize();
        assertUnder(temp, stagingDir);
        Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
        return temp;
    }

    /**
     * 只读取图片头部的宽高，不做整图解码。
     * 用于在解码前执行像素上限校验，避免小体积压缩图触发超大内存分配。
     */
    public int[] readImageSize(Path file) {
        try (javax.imageio.stream.ImageInputStream iis = ImageIO.createImageInputStream(file.toFile())) {
            if (iis == null) {
                return null;
            }
            java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * 完整解码一次，确认文件不是“只有合法头部、数据损坏”的伪造图片。
     * 只在像素上限校验通过后调用，内存占用有上界。
     */
    public boolean decodeMatches(Path file, int expectedWidth, int expectedHeight) {
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            return img != null && img.getWidth() == expectedWidth && img.getHeight() == expectedHeight;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * 读取 JPEG 的 EXIF Orientation（1~8）。PNG 或无 EXIF 返回 1。
     * Java ImageIO 不会自动旋转，浏览器却会按 EXIF 显示；方向不为 1 时坐标会与显示内容错位，
     * 因此当前策略是明确拒绝，提示用户先转正后再上传，而不是静默接受。
     */
    public int readExifOrientation(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            int b0 = in.read();
            int b1 = in.read();
            if (b0 != 0xFF || b1 != 0xD8) {
                return 1; // 不是 JPEG
            }
            while (true) {
                int marker = readMarker(in);
                if (marker < 0 || marker == 0xDA || marker == 0xD9) {
                    return 1; // 到图像数据/文件尾仍未找到 EXIF
                }
                if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                    continue; // 无长度字段的独立标记
                }
                int length = readU16(in, true);
                if (length < 2) {
                    throw new IOException("非法 JPEG 段长度");
                }
                if (marker == 0xE1) {
                    byte[] payload = in.readNBytes(length - 2);
                    if (payload.length >= 6 && payload[0] == 'E' && payload[1] == 'x'
                            && payload[2] == 'i' && payload[3] == 'f'
                            && payload[4] == 0 && payload[5] == 0) {
                        int orientation = parseTiffOrientation(payload);
                        return orientation;
                    }
                } else {
                    skipFully(in, length - 2);
                }
            }
        }
    }

    private int readMarker(InputStream in) throws IOException {
        int b;
        do {
            b = in.read();
            if (b < 0) return -1;
        } while (b != 0xFF);
        do {
            b = in.read();
            if (b < 0) return -1;
        } while (b == 0xFF);
        return b;
    }

    private int readU16(InputStream in, boolean bigEndian) throws IOException {
        int a = in.read();
        int b = in.read();
        if (a < 0 || b < 0) throw new IOException("EXIF 数据不完整");
        return bigEndian ? (a << 8) | b : (b << 8) | a;
    }

    private int readU16(byte[] data, int offset, boolean bigEndian) throws IOException {
        if (offset + 2 > data.length) throw new IOException("EXIF 越界");
        int a = data[offset] & 0xFF;
        int b = data[offset + 1] & 0xFF;
        return bigEndian ? (a << 8) | b : (b << 8) | a;
    }

    private int readU32(byte[] data, int offset, boolean bigEndian) throws IOException {
        if (offset + 4 > data.length) throw new IOException("EXIF 越界");
        int a = data[offset] & 0xFF;
        int b = data[offset + 1] & 0xFF;
        int c = data[offset + 2] & 0xFF;
        int d = data[offset + 3] & 0xFF;
        return bigEndian
                ? (a << 24) | (b << 16) | (c << 8) | d
                : (d << 24) | (c << 16) | (b << 8) | a;
    }

    private int parseTiffOrientation(byte[] payload) throws IOException {
        byte[] tiff = java.util.Arrays.copyOfRange(payload, 6, payload.length);
        if (tiff.length < 8) throw new IOException("EXIF TIFF 头不完整");
        boolean bigEndian;
        if (tiff[0] == 'I' && tiff[1] == 'I') {
            bigEndian = false;
        } else if (tiff[0] == 'M' && tiff[1] == 'M') {
            bigEndian = true;
        } else {
            throw new IOException("EXIF 字节序非法");
        }
        if (readU16(tiff, 2, bigEndian) != 42) throw new IOException("EXIF TIFF 标识非法");
        int ifd0 = readU32(tiff, 4, bigEndian);
        if (ifd0 < 0 || ifd0 + 2 > tiff.length) throw new IOException("EXIF IFD 偏移非法");
        int count = readU16(tiff, ifd0, bigEndian);
        for (int i = 0; i < count; i++) {
            int entry = ifd0 + 2 + i * 12;
            if (entry + 12 > tiff.length) break;
            int tag = readU16(tiff, entry, bigEndian);
            if (tag == 0x0112) {
                int type = readU16(tiff, entry + 2, bigEndian);
                int components = readU32(tiff, entry + 4, bigEndian);
                if (type == 3 && components >= 1) {
                    int orientation = readU16(tiff, entry + 8, bigEndian);
                    return (orientation >= 1 && orientation <= 8) ? orientation : 1;
                }
            }
        }
        return 1;
    }

    private void skipFully(InputStream in, int bytes) throws IOException {
        int remaining = bytes;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() < 0) throw new IOException("JPEG 段数据不完整");
                remaining--;
            } else {
                remaining -= (int) skipped;
            }
        }
    }

    /**
     * 识别真实图片格式（不信客户端 Content-Type / 扩展名）。
     * 返回 jpg / png，无法识别返回 null。
     */
    public String detectImageFormat(Path file) {
        try (javax.imageio.stream.ImageInputStream iis = ImageIO.createImageInputStream(file.toFile())) {
            if (iis == null) {
                return null;
            }
            java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            javax.imageio.ImageReader reader = readers.next();
            try {
                String fmt = reader.getFormatName().toLowerCase();
                if (fmt.startsWith("jpeg") || fmt.equals("jpg")) {
                    return "jpg";
                }
                if (fmt.equals("png")) {
                    return "png";
                }
                return null;
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return null;
        }
    }

    public String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    digest.update(buf, 0, n);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * 暂存文件原子移动到正式目录。目标目录不存在则创建。
     * 原子 rename 在同一文件系统内有效（staging 与 final 均在 storage-root 下）。
     */
    public Path promoteToFinal(Path staged, Long batchId, String fileKey) throws IOException {
        Path dir = finalDir.resolve(String.valueOf(batchId)).normalize();
        assertUnder(dir, finalDir);
        Files.createDirectories(dir);
        Path target = dir.resolve(fileKey).normalize();
        assertUnder(target, finalDir);
        Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }

    /** 按业务相对路径（batchId/fileKey）解析正式文件，任何越界路径直接拒绝 */
    public Path resolveFinal(Long batchId, String fileKey) {
        Path target = finalDir.resolve(String.valueOf(batchId)).resolve(fileKey).normalize();
        assertUnder(target, finalDir);
        return target;
    }

    public void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Delete file failed: {}", path, e);
        }
    }

    /** 防路径穿越：解析后的路径必须仍位于允许的根目录内 */
    private void assertUnder(Path path, Path root) {
        if (!path.normalize().startsWith(root)) {
            throw new IllegalArgumentException("Illegal storage path: " + path);
        }
    }

    public Path getStagingDir() {
        return stagingDir;
    }
}
