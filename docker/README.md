# docker/ —— MES 镜像资产与服务配置

本目录只含 MES 自有内容。**基础设施与共享 Nacos 基线配置的单一真源在 mse-framework 仓 `docker/`**。

## 内容

- `nacos/config/mse-mes.yaml`：本服务专属 Data ID 留档（端口 8098 / 业务参数）
- `mes-boot/Dockerfile`：源码多阶段构建（context=**本仓根**，MODULE=mes-boot，PORT=8098）
- `mes-boot/k8s/mes.yaml`：Deployment + Service 清单（复用 framework 仓 `mse-boot/k8s/configmap.yaml` 的 mse-config）

## 本地运行（微服务形态）

1. **基础设施**：`cd <兄弟目录>/mse-framework/docker && docker compose up -d mysql redis nacos`
2. **导入本服务配置**：`CONFIG_DIR=<本仓>/docker/nacos/config sh <framework仓>/docker/nacos/init-configs.sh`
3. **构建并启动**：`sh build.sh` 后 `java -jar mes-boot/target/mes-boot-1.0.0-SNAPSHOT.jar`（8098）
4. 网关 mse-gateway（48080）基线路由已含 `lb://mse-mes`。

前置跨仓依赖：mse-framework 仓与 mse-modules 仓（ai-api / open-api）先各自 `sh build.sh`。

## 镜像构建

```sh
docker build -f mes-boot/Dockerfile -t mse/mes-boot:1.0.0 .
# 快速通道：本机已 mvn package 后用 framework 仓 mse-boot/Dockerfile.prebuilt（context=模块目录）
```

K8s：`kubectl apply -f mes-boot/k8s/mes.yaml`（先 apply framework 仓 configmap）。
