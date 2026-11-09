#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
model_dir="${LOCAL_MODEL_DIR:-$project_dir/.models}"
model_path="$model_dir/qwen2.5-1.5b-instruct-q4_k_m.gguf"
expected_sha256="6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
model_url="https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf"

if ! command -v llama-server >/dev/null 2>&1; then
  echo "找不到 llama-server。macOS 可以先运行：brew install llama.cpp" >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "找不到 curl，请先安装 curl。" >&2
  exit 1
fi
if command -v shasum >/dev/null 2>&1; then
  checksum() { shasum -a 256 "$1" | awk '{print $1}'; }
elif command -v sha256sum >/dev/null 2>&1; then
  checksum() { sha256sum "$1" | awk '{print $1}'; }
else
  echo "找不到 shasum 或 sha256sum，无法校验模型完整性。" >&2
  exit 1
fi

mkdir -p "$model_dir"
if [ ! -f "$model_path" ]; then
  echo "首次启动需要下载约 1.04 GiB 的模型，请稍候。"
  temporary_model="$(mktemp "$model_dir/.qwen-download.XXXXXXXX")"
  trap 'rm -f "$temporary_model"' EXIT
  curl -fL --retry 2 --connect-timeout 15 "$model_url" -o "$temporary_model"
  if [ "$(checksum "$temporary_model")" != "$expected_sha256" ]; then
    echo "下载文件校验失败，请重试。" >&2
    exit 1
  fi
  mv "$temporary_model" "$model_path"
  trap - EXIT
fi
if [ "$(checksum "$model_path")" != "$expected_sha256" ]; then
  echo "现有模型文件校验失败：$model_path" >&2
  echo "请检查或手动移走该文件后重试。" >&2
  exit 1
fi

cat <<'MSG'
本地模型正在启动。请保持此窗口打开。
在另一个终端启动后端前设置：
  export OPENAI_BASE_URL=http://127.0.0.1:8081/v1
  export OPENAI_MODEL=qwen-local
  unset OPENAI_API_KEY
MSG
exec llama-server --model "$model_path" --alias qwen-local \
  --host 127.0.0.1 --port 8081 --ctx-size 4096 --threads 3 --parallel 1 \
  --n-gpu-layers 0 --cors-origins localhost
