# 中文文本纠错对比 Demo

并排对比两种本地中文纠错方案，肉眼看准确率和速度差异：

| 方案 | 模型 | 镜像内大小 | CPU 单次耗时 | 擅长 |
|------|------|-----------|------------|------|
| 专用 CSC | macbert4csc-base | ~400 MB | 100~500 ms | 错字、错词、形近音近字 |
| 通用小 LLM | Qwen2.5-3B-Instruct (Q4_K_M) | ~2 GB | 2~8 s | 错句、语病、标点、给修改理由 |

部署形态：Docker Compose 一次拉起 4 个容器，跨平台一致（Windows / macOS / Linux）。

## 系统要求

- Docker Desktop（Windows、macOS）或 Docker Engine（Linux）
- 至少 8 GB 可用内存（推荐 16 GB）
- 首次启动需要联网下载约 3~4 GB 模型与依赖
- 端口 7860 / 8000 / 11434 未被占用

## 快速开始

```bash
docker compose up -d
```

首次启动包含两个阶段：
1. 构建 `csc-service` 与 `web-ui` 镜像（macbert4csc 模型会被内嵌进镜像），约 5~10 分钟。
2. `ollama-init` 容器自动拉取 LLM 模型，约 3~10 分钟（视网速）。

待 `ollama-init` 退出且状态为 `Exited (0)` 即表示模型就绪。然后访问：

**http://localhost:7860**

## 端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| web-ui | 7860 | Gradio 对比界面，浏览器访问 |
| csc-service | 8000 | macbert4csc REST API（POST /correct） |
| ollama | 11434 | Ollama API（兼容 OpenAI 风格） |

如果端口冲突，编辑 `docker-compose.yml` 中对应服务的 `ports` 映射。

## 切换模型

默认使用 `qwen2.5:3b`。若机器是 8 GB 内存的轻薄本，建议改用 1.5B 版本：

```bash
# 方式 1：复制 .env.example 为 .env 并修改 LLM_MODEL
cp .env.example .env

# 方式 2：临时通过环境变量切换
LLM_MODEL=qwen2.5:1.5b docker compose up -d
```

修改后执行 `docker compose up -d` 会自动重新拉模型。可选值参见 https://ollama.com/library/qwen2.5

## 常用命令

```bash
docker compose ps                       # 查看各容器状态
docker compose logs -f web-ui           # 查看 Web UI 日志
docker compose logs -f ollama-init      # 查看模型拉取进度
docker compose restart web-ui           # 重启某个服务
docker compose down                     # 停止全部服务
docker compose down -v                  # 停止并删除模型/数据卷
```

## 调用 API（不通过 UI）

CSC 服务：

```bash
curl -X POST http://localhost:8000/correct \
  -H "Content-Type: application/json" \
  -d '{"text": "今天新情很好，去公园散布。"}'
```

LLM 服务（Ollama 原生 API）：

```bash
curl -X POST http://localhost:11434/api/generate \
  -H "Content-Type: application/json" \
  -d '{"model": "qwen2.5:3b", "prompt": "请校对：今天新情很好。", "stream": false}'
```

## 常见问题

**Q: `ollama-init` 卡在 Pulling 阶段或失败**
A: 网络问题。`docker compose logs ollama-init` 查看进度。可手动重试：`docker compose up -d ollama-init`。国内网络可以考虑配置 Docker 镜像加速。

**Q: Web UI 报"调用失败：Connection refused"**
A: 后端没就绪。确认 `docker compose ps` 中 `csc-service` 是 `healthy`，`ollama-init` 是 `Exited (0)`。

**Q: LLM 响应太慢，超过 30 秒**
A: 切换到 1.5B 模型（见上文）；或确认 CPU 没被其他任务占满。

**Q: macOS Apple Silicon 上启动报架构错误**
A: 本项目所用镜像均为多架构（amd64 + arm64），Docker Desktop 默认会选择正确架构。如出现问题，确认 Docker Desktop 已升级到最新版。

**Q: Windows 上启动 Docker Desktop 占用内存太大**
A: 在 Docker Desktop 设置 → Resources 里调整内存限额（建议至少 8 GB 给 Docker）。

## 目录结构

```
.
├── docker-compose.yml      # 编排 4 个容器
├── .env.example            # 模型选择等可调参数
├── csc-service/            # macbert4csc + FastAPI
│   ├── Dockerfile          # 镜像内预下载模型，离线可用
│   ├── requirements.txt
│   └── app.py
└── web-ui/                 # Gradio 对比界面
    ├── Dockerfile
    ├── requirements.txt
    └── app.py
```
