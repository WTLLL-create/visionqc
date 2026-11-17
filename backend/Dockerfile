# ---- 构建阶段：Maven + JDK17 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# 先拷 pom 单独下载依赖，利用 Docker 层缓存加速后续构建
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# ---- 运行阶段：仅 JRE，镜像更小 ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV TZ=Asia/Shanghai
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
