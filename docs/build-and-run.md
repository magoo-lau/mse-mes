# 构建与运行

## 构建

```sh
# 前置（跨仓顺序）：mse-framework 仓 → mse-modules 仓（ai-api/open-api）→ 本仓
sh build.sh                          # mvn install -DskipTests
MSE_RUN_ALL_TESTS=1 sh build.sh      # 放开测试
sh build-ui.sh                       # mes-ui + mes-mobile-ui
```

## 本地运行（微服务形态）

```sh
# 1) 基础设施（framework 仓单一真源）
cd <兄弟目录>/mse-framework/docker && docker compose up -d mysql redis nacos

# 2) 导入本服务配置（mse-mes.yaml，端口 8098）
CONFIG_DIR=<本仓>/docker/nacos/config sh <framework仓>/docker/nacos/init-configs.sh

# 3) 启动
java -jar mes-boot/target/mes-boot-1.0.0-SNAPSHOT.jar    # 8098
```

网关 mse-gateway（48080）基线路由已含 `lb://mse-mes`；前端子应用经 `/sub-apps/mes/`（PC）与 `/m-apps/mes/`（移动）挂载。

## 镜像与 K8s

```sh
docker build -f mes-boot/Dockerfile -t mse/mes-boot:1.0.0 .    # context=本仓根
# 快速通道：本机已 package 后用 framework 仓 mse-boot/Dockerfile.prebuilt
kubectl apply -f <framework仓>/mse-boot/k8s/configmap.yaml
kubectl apply -f mes-boot/k8s/mes.yaml
```

## 前端

```sh
sh build-ui.sh mes-ui            # PC 子应用 --base=/sub-apps/mes/
sh build-ui.sh mes-mobile-ui     # 移动子应用 --base=/m-apps/mes/
```

UI 的 mse-ui-shared 为 npm git 依赖（独立仓 `github:magoo-lau/mse-ui-shared#release/1.0.0`；根 .npmrc 已放行 allow-git，npm ci 原生安装）；dev proxy `/api` → 网关 48080。

## MQ / AI 依赖（可选）

- RocketMQ：framework 仓 compose 起 namesrv/broker，Nacos 基线放开对应段（mes-boot 的 mq-starter 块默认注释）。
- AI 智能体/工具：mse-modules 仓 mse-ai 在线即自动登记（周期重推）；`mes.ai.mode` 开关（off/auto/platform/direct）。
