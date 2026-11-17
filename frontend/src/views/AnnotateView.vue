<template>
  <el-card class="workbench">
    <template #header>
      <div class="header">
        <el-page-header @back="goBack" :content="`缺陷标注工作台 · 任务 #${taskId}`" />
        <div class="status">
          <el-tag v-if="task" :type="editable ? 'success' : 'warning'">
            {{ task.status }}{{ editable ? '（可编辑）' : '（只读）' }}
          </el-tag>
          <span v-if="image" class="meta">原图 {{ image.width }}×{{ image.height }}</span>
          <span class="meta">标注 {{ rectCount }} 个</span>
        </div>
      </div>
    </template>

    <div class="toolbar">
      <el-button-group>
        <el-button @click="zoomBy(0.2)" title="放大">＋</el-button>
        <el-button @click="zoomBy(-0.2)" title="缩小">－</el-button>
        <el-button @click="fitView">适应窗口</el-button>
        <el-button @click="resetZoom">100%</el-button>
      </el-button-group>
      <span class="zoom">{{ Math.round(zoomPercent) }}%</span>
      <el-button :type="drawMode ? 'danger' : 'primary'" :disabled="!editable" @click="toggleDraw">
        {{ drawMode ? '退出绘制' : '绘制矩形' }}
      </el-button>
      <el-button :type="panMode ? 'warning' : 'default'" @click="panMode = !panMode">
        {{ panMode ? '退出拖动' : '拖动图片' }}
      </el-button>
      <el-button type="danger" plain :disabled="!activeRect" @click="deleteActive">删除选中</el-button>

      <el-select v-model="activeCategoryId" placeholder="缺陷类别" style="width: 150px"
                 :disabled="!activeRect || !editable" @change="applyCategory">
        <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
      <el-input v-model="activeNote" placeholder="选中标注后填写备注" style="width: 220px"
                :disabled="!activeRect || !editable" @change="applyNote" />

      <el-button type="success" :loading="saving" :disabled="!editable" @click="save">保存标注</el-button>
      <el-button @click="loadAnnotations" :disabled="!editable">重新加载</el-button>

      <el-button v-if="task && ['ASSIGNED','REJECTED'].includes(task.status)" type="warning"
                 :loading="acting" @click="startWork">
        {{ task.status === 'REJECTED' ? '继续修改' : '开始标注' }}
      </el-button>
      <el-button v-if="task && task.status === 'IN_PROGRESS'" type="primary"
                 :loading="acting" @click="submitReview">提交审核</el-button>
      <el-tag v-if="task && task.status === 'SUBMITTED'" type="warning">已提交，等待审核</el-tag>
      <el-tag v-if="task && task.status === 'APPROVED'" type="success">审核已通过</el-tag>
    </div>

    <div class="body">
      <div class="canvas-wrap" ref="wrapEl" v-loading="loading">
        <canvas ref="canvasEl"></canvas>
        <div v-if="!loading && errors" class="error-tip">{{ errors }}</div>
      </div>

      <div class="side">
        <h4>标注列表</h4>
        <el-empty v-if="!rectList.length" description="还没有标注框" :image-size="60" />
        <div v-for="(item, idx) in rectList" :key="item.id" class="rect-item"
             :class="{ active: item.id === activeRect?.id }" @click="selectRect(item)">
          <div class="rect-title">
            <span>#{{ idx + 1 }} {{ categoryName(item.categoryId) }}</span>
            <el-button link type="danger" :disabled="!editable" @click.stop="removeById(item.id)">删除</el-button>
          </div>
          <div class="rect-sub">
            x={{ item.xNorm.toFixed(3) }}, y={{ item.yNorm.toFixed(3) }},
            w={{ item.wNorm.toFixed(3) }}, h={{ item.hNorm.toFixed(3) }}
          </div>
          <div v-if="item.note" class="rect-note">{{ item.note }}</div>
        </div>

        <el-divider />
        <div class="tips">
          <p>操作说明：</p>
          <p>1. 滚轮缩放，按住 Alt 或开启"拖动图片"平移</p>
          <p>2. 点击"绘制矩形"后在图上拖拽画框</p>
          <p>3. 选中框后可拖动/缩放，Delete 删除</p>
          <p>4. 坐标按原图归一化存库，刷新后精确恢复</p>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Canvas, FabricImage, Rect, Point } from 'fabric'
import { getTask, listTaskAnnotations, saveTaskAnnotations, listDefectCategories, startTask, submitTask } from '../api/task'
import { getImage, imageFileUrl } from '../api/image'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.params.taskId)

const wrapEl = ref()
const canvasEl = ref()
const loading = ref(false)
const saving = ref(false)
const acting = ref(false)
const errors = ref('')
const task = ref(null)
const image = ref(null)
const categories = ref([])
const rectList = ref([])
const rectCount = computed(() => rectList.value.length)
const zoomPercent = ref(100)
const drawMode = ref(false)
const panMode = ref(false)
const activeRect = ref(null)
const activeCategoryId = ref(null)
const activeNote = ref('')
const editable = computed(() => task.value && ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'REJECTED'].includes(task.value.status))

let canvas = null
let baseImage = null
let drawing = false
let startPoint = null
let currentRect = null
let panning = false
let lastPan = null
let spaceDown = false
let suppressNoteSync = false

const categoryMap = computed(() => Object.fromEntries(categories.value.map((c) => [c.id, c.name])))

function categoryName(id) {
  return categoryMap.value[id] || `类别#${id}`
}

function goBack() {
  router.back()
}

function initCanvas() {
  canvas = new Canvas(canvasEl.value, {
    backgroundColor: '#202225',
    preserveObjectStacking: true,
    selection: true
  })
  resizeCanvas()
  bindEvents()
}

function resizeCanvas() {
  if (!canvas || !wrapEl.value) return
  const w = Math.max(wrapEl.value.clientWidth, 400)
  const h = Math.max(wrapEl.value.clientHeight, 400)
  canvas.setDimensions({ width: w, height: h })
}

function bindEvents() {
  canvas.on('mouse:wheel', (opt) => {
    const e = opt.e
    e.preventDefault()
    e.stopPropagation()
    const factor = 1 - e.deltaY / 500
    let zoom = canvas.getZoom() * factor
    zoom = Math.min(Math.max(zoom, 0.05), 5)
    canvas.zoomToPoint(new Point(e.offsetX, e.offsetY), zoom)
    zoomPercent.value = zoom * 100
  })

  canvas.on('mouse:down', (opt) => {
    const e = opt.e
    if (panMode.value || e.altKey || e.button === 1) {
      panning = true
      lastPan = { x: e.clientX, y: e.clientY }
      canvas.selection = false
      canvas.discardActiveObject()
      suppressNoteSync = true
      setActive(null)
      suppressNoteSync = false
      return
    }
    if (drawMode.value && editable.value && !opt.target) {
      startDraw(opt)
    }
  })

  canvas.on('mouse:move', (opt) => {
    const e = opt.e
    if (panning && lastPan) {
      const vpt = canvas.viewportTransform
      vpt[4] += e.clientX - lastPan.x
      vpt[5] += e.clientY - lastPan.y
      canvas.setViewportTransform(vpt)
      lastPan = { x: e.clientX, y: e.clientY }
      return
    }
    if (drawing && currentRect) {
      const p = clampScenePoint(scenePointFromOpt(opt))
      const left = Math.min(startPoint.x, p.x)
      const top = Math.min(startPoint.y, p.y)
      currentRect.set({
        left,
        top,
        width: Math.abs(p.x - startPoint.x),
        height: Math.abs(p.y - startPoint.y)
      })
      currentRect.setCoords()
      canvas.requestRenderAll()
    }
  })

  canvas.on('mouse:up', () => {
    if (panning) {
      panning = false
      lastPan = null
      canvas.selection = true
      return
    }
    if (drawing && currentRect) {
      finishDraw()
    }
  })

  canvas.on('selection:created', (opt) => onSelection(opt.selected?.[0]))
  canvas.on('selection:updated', (opt) => onSelection(opt.selected?.[0]))
  canvas.on('selection:cleared', () => {
    if (!suppressNoteSync) setActive(null)
  })

  canvas.on('object:modified', (opt) => {
    if (opt.target && opt.target !== baseImage) {
      clampObject(opt.target)
      syncRectList()
    }
  })

  window.addEventListener('keydown', onKeyDown)
  window.addEventListener('keyup', onKeyUp)
  window.addEventListener('resize', resizeCanvas)
}

function onKeyDown(e) {
  if (e.code === 'Space') {
    spaceDown = true
    canvas.defaultCursor = 'grab'
  }
  if ((e.key === 'Delete' || e.key === 'Backspace') && activeRect.value && editable.value) {
    e.preventDefault()
    deleteActive()
  }
}

function onKeyUp(e) {
  if (e.code === 'Space') {
    spaceDown = false
    canvas.defaultCursor = 'default'
  }
}

function scenePointFromOpt(opt) {
  // Fabric v6/v7 事件对象自带 scenePoint（图像坐标系，不含缩放/平移）
  if (opt?.scenePoint) return { x: opt.scenePoint.x, y: opt.scenePoint.y }
  const e = opt?.e || opt
  const r = canvas.upperCanvasEl.getBoundingClientRect()
  const vpt = canvas.viewportTransform
  return {
    x: (e.clientX - r.left - vpt[4]) / vpt[0],
    y: (e.clientY - r.top - vpt[5]) / vpt[3]
  }
}

function clampScenePoint(p) {
  if (!image.value) return p
  return {
    x: Math.min(Math.max(p.x, 0), image.value.width),
    y: Math.min(Math.max(p.y, 0), image.value.height)
  }
}

function startDraw(opt) {
  const p = clampScenePoint(scenePointFromOpt(opt))
  startPoint = p
  drawing = true
  currentRect = new Rect({
    left: p.x,
    top: p.y,
    width: 0,
    height: 0,
    fill: 'rgba(231, 76, 60, 0.12)',
    stroke: '#e74c3c',
    strokeWidth: 2,
    strokeUniform: true,
    lockRotation: true,
    selectable: false,
    evented: false,
    cornerSize: 10,
    transparentCorners: false,
    cornerColor: '#e74c3c',
    borderColor: '#e74c3c'
  })
  canvas.add(currentRect)
  canvas.requestRenderAll()
}

function finishDraw() {
  drawing = false
  if (!currentRect) return
  const rect = currentRect
  currentRect = null
  if (rect.width < 4 || rect.height < 4) {
    canvas.remove(rect)
    canvas.requestRenderAll()
    return
  }
  clampObject(rect)
  rect.set({
    selectable: true,
    evented: true,
    categoryId: categories.value[0]?.id ?? null,
    note: ''
  })
  rect.setCoords()
  canvas.setActiveObject(rect)
  canvas.requestRenderAll()
  drawMode.value = false
  onSelection(rect)
  syncRectList()
}

function clampObject(obj) {
  if (!image.value) return
  const w = obj.width * obj.scaleX
  const h = obj.height * obj.scaleY
  let left = obj.left
  let top = obj.top
  if (left < 0) left = 0
  if (top < 0) top = 0
  if (left + w > image.value.width) left = image.value.width - w
  if (top + h > image.value.height) top = image.value.height - h
  obj.set({ left, top })
  obj.setCoords()
}

function onSelection(obj) {
  if (!obj || obj === baseImage) {
    setActive(null)
    return
  }
  setActive(obj)
  suppressNoteSync = true
  activeCategoryId.value = obj.categoryId ?? (categories.value[0]?.id ?? null)
  activeNote.value = obj.note || ''
  suppressNoteSync = false
}

function setActive(obj) {
  activeRect.value = obj || null
  if (!obj) {
    activeCategoryId.value = null
    activeNote.value = ''
  }
}

function applyCategory(id) {
  if (!activeRect.value) return
  activeRect.value.categoryId = id
  syncRectList()
}

function applyNote() {
  if (suppressNoteSync || !activeRect.value) return
  activeRect.value.note = activeNote.value
  syncRectList()
}

function deleteActive() {
  if (!activeRect.value) return
  canvas.remove(activeRect.value)
  setActive(null)
  canvas.discardActiveObject()
  canvas.requestRenderAll()
  syncRectList()
}

function removeById(id) {
  const obj = canvas.getObjects().find((o) => o.id === id && o !== baseImage)
  if (obj) {
    canvas.remove(obj)
    if (activeRect.value?.id === id) setActive(null)
    canvas.discardActiveObject()
    canvas.requestRenderAll()
    syncRectList()
  }
}

function selectRect(item) {
  const obj = canvas.getObjects().find((o) => o.id === item.id)
  if (obj) {
    canvas.setActiveObject(obj)
    canvas.requestRenderAll()
    onSelection(obj)
  }
}

/** 给每个矩形分配稳定 id（仅前端使用），并同步右侧列表 */
let rectSeq = 1
function syncRectList() {
  if (!canvas || !image.value) return
  const items = canvas.getObjects()
    .filter((o) => o !== baseImage && o.type === 'rect')
    .map((o) => {
      if (!o.id) o.id = rectSeq++
      return {
        id: o.id,
        categoryId: o.categoryId,
        note: o.note || '',
        xNorm: o.left / image.value.width,
        yNorm: o.top / image.value.height,
        wNorm: (o.width * o.scaleX) / image.value.width,
        hNorm: (o.height * o.scaleY) / image.value.height
      }
    })
  rectList.value = items
}

function zoomBy(delta) {
  const zoom = Math.min(Math.max(canvas.getZoom() * (1 + delta), 0.05), 5)
  canvas.zoomToPoint(new Point(canvas.getWidth() / 2, canvas.getHeight() / 2), zoom)
  zoomPercent.value = zoom * 100
}

function resetZoom() {
  canvas.setViewportTransform([1, 0, 0, 1, 0, 0])
  zoomPercent.value = 100
}

function fitView() {
  if (!canvas || !image.value) return
  const cw = canvas.getWidth()
  const ch = canvas.getHeight()
  const scale = Math.min(cw / image.value.width, ch / image.value.height) * 0.95
  canvas.setViewportTransform([
    scale, 0, 0, scale,
    (cw - image.value.width * scale) / 2,
    (ch - image.value.height * scale) / 2
  ])
  zoomPercent.value = scale * 100
}

function toggleDraw() {
  drawMode.value = !drawMode.value
  if (drawMode.value) {
    panMode.value = false
    canvas.selection = false
    canvas.discardActiveObject()
    setActive(null)
    canvas.defaultCursor = 'crosshair'
  } else {
    canvas.selection = true
    canvas.defaultCursor = 'default'
  }
}

async function loadImage() {
  if (!task.value) return
  image.value = await getImage(task.value.imageId)
  const img = await FabricImage.fromURL(imageFileUrl(image.value.id))
  img.set({ left: 0, top: 0, originX: 'left', originY: 'top', selectable: false, evented: false })
  baseImage = img
  canvas.add(img)
  canvas.sendObjectToBack(img)
  fitView()
  canvas.requestRenderAll()
}

function addRectFromAnnotation(a) {
  if (!image.value) return
  const rect = new Rect({
    left: a.xNorm * image.value.width,
    top: a.yNorm * image.value.height,
    width: a.wNorm * image.value.width,
    height: a.hNorm * image.value.height,
    fill: 'rgba(231, 76, 60, 0.12)',
    stroke: '#e74c3c',
    strokeWidth: 2,
    strokeUniform: true,
    lockRotation: true,
    cornerSize: 10,
    transparentCorners: false,
    cornerColor: '#e74c3c',
    borderColor: '#e74c3c'
  })
  rect.set({ categoryId: a.categoryId, note: a.note || '', id: rectSeq++ })
  canvas.add(rect)
  rect.setCoords()
}

async function loadAnnotations() {
  if (!task.value) return
  // 清空旧矩形（保留底图）
  canvas.getObjects().forEach((o) => {
    if (o !== baseImage) canvas.remove(o)
  })
  setActive(null)
  const list = await listTaskAnnotations(taskId)
  list.forEach(addRectFromAnnotation)
  canvas.requestRenderAll()
  syncRectList()
  canvas.discardActiveObject()
}

async function save() {
  if (!editable.value) return false
  const items = canvas.getObjects()
    .filter((o) => o !== baseImage && o.type === 'rect')
    .map((o) => ({
      categoryId: o.categoryId || categories.value[0]?.id,
      x: round6(o.left / image.value.width),
      y: round6(o.top / image.value.height),
      w: round6((o.width * o.scaleX) / image.value.width),
      h: round6((o.height * o.scaleY) / image.value.height),
      imgWidth: image.value.width,
      imgHeight: image.value.height,
      note: o.note || ''
    }))
  if (items.some((i) => !i.categoryId)) {
    ElMessage.warning('存在未选择缺陷类别的标注框')
    return false
  }
  if (items.some((i) => i.x + i.w > 1.000001 || i.y + i.h > 1.000001 || i.w <= 0 || i.h <= 0)) {
    ElMessage.warning('存在越界标注框，请调整后再保存')
    return false
  }
  saving.value = true
  try {
    await saveTaskAnnotations(taskId, items)
    ElMessage.success(`已保存 ${items.length} 个标注`)
    await loadAnnotations()
    return true
  } finally {
    saving.value = false
  }
}

/** 提交/通过后锁定画布上的矩形，避免用户误以为还能编辑 */
function applyReadOnly() {
  if (!canvas) return
  canvas.getObjects().forEach((o) => {
    if (o !== baseImage) o.set({ selectable: editable.value, evented: editable.value })
  })
  if (!editable.value) {
    canvas.discardActiveObject()
    setActive(null)
  }
  canvas.requestRenderAll()
}

async function startWork() {
  try {
    await ElMessageBox.confirm('开始后任务进入 IN_PROGRESS，可绘制并保存标注。', '开始标注', { type: 'info' })
  } catch { return }
  acting.value = true
  try {
    task.value = await startTask(taskId, { version: task.value.version, comment: '开始处理' })
    applyReadOnly()
    ElMessage.success('任务已进入标注中')
  } finally {
    acting.value = false
  }
}

async function submitReview() {
  if (!rectCount.value) {
    ElMessage.warning('至少需要一个标注框才能提交审核')
    return
  }
  const saved = await save()
  if (!saved) return
  try {
    await ElMessageBox.confirm(
      `将提交 ${rectCount.value} 个标注框进入审核。提交后需等待审核，不能直接修改。`,
      '提交审核', { type: 'warning' })
  } catch { return }
  acting.value = true
  try {
    task.value = await submitTask(taskId, { version: task.value.version, comment: '标注完成，提交审核' })
    applyReadOnly()
    ElMessage.success('已提交审核')
  } finally {
    acting.value = false
  }
}

function round6(v) {
  return Math.round(v * 1e6) / 1e6
}

async function boot() {
  loading.value = true
  errors.value = ''
  try {
    task.value = await getTask(taskId)
    categories.value = await listDefectCategories()
    await nextTick()
    initCanvas()
    await loadImage()
    await loadAnnotations()
    applyReadOnly()
  } catch (e) {
    errors.value = '加载失败：' + (e?.response?.data?.message || e.message || '未知错误')
  } finally {
    loading.value = false
  }
}

onMounted(boot)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeyDown)
  window.removeEventListener('keyup', onKeyUp)
  window.removeEventListener('resize', resizeCanvas)
  if (canvas) {
    canvas.dispose()
    canvas = null
  }
})
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; }
.status { display: flex; align-items: center; gap: 12px; }
.meta { color: #909399; font-size: 13px; }
.toolbar { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 12px; }
.zoom { color: #606266; width: 56px; text-align: center; }
.body { display: flex; gap: 16px; height: calc(100vh - 330px); min-height: 480px; }
.canvas-wrap {
  flex: 1; position: relative; background: #202225; border-radius: 6px; overflow: hidden; min-width: 400px;
}
.canvas-wrap canvas { display: block; }
.error-tip { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: #f56c6c; }
.side { width: 300px; overflow-y: auto; border-left: 1px solid #ebeef5; padding-left: 12px; }
.side h4 { margin: 0 0 8px; }
.rect-item { border: 1px solid #ebeef5; border-radius: 6px; padding: 8px; margin-bottom: 8px; cursor: pointer; }
.rect-item.active { border-color: #409eff; background: #f0f7ff; }
.rect-title { display: flex; justify-content: space-between; font-size: 13px; }
.rect-sub { color: #909399; font-size: 12px; margin-top: 2px; }
.rect-note { color: #606266; font-size: 12px; margin-top: 4px; }
.tips { color: #909399; font-size: 12px; line-height: 1.8; }
</style>
