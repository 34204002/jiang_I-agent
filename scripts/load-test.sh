#!/usr/bin/env bash
# =============================================================================
# 并发压测：并发对话，收敛服务端埋点 [SSE_FRAME][TTFT][RAG_TIME][TOOL_TIME]
# 与客户端首帧/总耗时的 p50/p95。
#
# 前置（必须重启后端才生效）：
#   1) 已加载耗时埋点  —— ChatService / ToolRegistry 的 nanoTime 日志
#   2) 日志已落盘      —— application-dev.yml 配了 logging.file.name
#      （IDEA 运行目录 = 项目根时，日志在 logs/jiang-i-agent.log）
#   3) MySQL/Redis/Qdrant/Neo4j 正常；知识库有文档时 RAG 出"命中"样本
#
# 用法：
#   bash scripts/load-test.sh [并发数]          # 默认 8
#   BASE=http://localhost:8080 bash scripts/load-test.sh 10
#   LOG_FILE=logs/jiang-i-agent.log bash scripts/load-test.sh 6
#
# 说明：每次运行注册一个一次性压测账号（loadtest_<时间戳>），会话留在库里无碍。
#       RAG 无文档时测的是"无命中"检索耗时，有文档后重跑即有命中样本。
# =============================================================================
set -uo pipefail

BASE="${BASE:-http://localhost:8080}"
N="${1:-8}"
TO=180
LOG_FILE="${LOG_FILE:-logs/jiang-i-agent.log}"
OUTDIR="$(mktemp -d)"
NOW=$(date +%F-%H%M%S)
USER="loadtest_${NOW}"
PASS="loadtest123"

# 混入不同类型请求，确保 RAG / 工具路径都被触发（ASCII 防终端编码坑）
prompts=(
  "Hello, please introduce yourself briefly."
  "What is the exact current time? Please check the clock."
  "Create a todo item for me: buy milk tonight."
  "Compare the customs of Spring Festival and Mid-Autumn Festival."
  "What time is it right now?"
  "Round 3.14159 to the nearest integer."
  "Create a todo item: prepare for the interview tomorrow morning."
  "Give me some tips for an evening run."
)

# ----------------------------------------------------------------------
# 工具函数：对已排序的数值文件算 p50/p95/max
# ----------------------------------------------------------------------
percentile() {
  local f="$1" n p50 p95 mx
  n=$(wc -l < "$f" | tr -d ' ')
  [ "$n" -eq 0 ] && { echo -e "n=0\tp50=-   p95=-   max=-"; return; }
  read -r p50 p95 mx < <(awk -v n="$n" '{a[NR]=$1}
      END{n50=int(n*0.5);  if(n50<1)n50=1;
          n95=int(n*0.95); if(n95<1)n95=1;
          print a[n50], a[n95], a[n]}' "$f")
  printf "n=%-3s p50=%-6s p95=%-6s max=%-6s" "$n" "${p50}ms" "${p95}ms" "${mx}ms"
}

# ----------------------------------------------------------------------
echo "== 环境 =="
echo "BASE=$BASE  N=$N  LOG_FILE=$LOG_FILE"
mkdir -p logs

# curl 计时格式文件（time_starttransfer = 首帧，time_total = 全量）
CFMT="$(mktemp)"
printf 'start=%%{time_starttransfer} total=%%{time_total} code=%%{http_code}\n' > "$CFMT"

# 后端就绪探测
probe=$(curl -s -m 5 -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
        -d '{"username":"probe","password":"probe"}' "$BASE/api/auth/login" 2>/dev/null || true)
[ "$probe" = "000" ] || [ -z "$probe" ] && {
  echo "!! 后端不可达（$BASE）。请先启动后端，并【重启】以加载埋点 + 日志落盘。"
  exit 1
}

# 日志落盘校验
[ -f "$LOG_FILE" ] || {
  echo "!! 找不到日志文件 $LOG_FILE —— 后端未用带 logging.file.name 的配置重启。"
  echo "   （IDEA 运行目录若不在项目根，用 LOG_FILE=<实际路径> 指定）"
  exit 1
}

echo ""
echo "== 鉴权 =="
curl -s -m 10 -o /dev/null -H 'Content-Type: application/json' \
     -d "{\"username\":\"$USER\",\"password\":\"$PASS\",\"nickname\":\"loadtest\"}" \
     "$BASE/api/auth/register" >/dev/null || true
login=$(curl -s -m 10 -H 'Content-Type: application/json' \
        -d "{\"username\":\"$USER\",\"password\":\"$PASS\"}" "$BASE/api/auth/login" || true)
TOKEN=$(printf '%s' "$login" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
[ -z "$TOKEN" ] && { echo "!! 登录失败: $login"; exit 1; }
echo "账号=$USER  登录 OK"

# 记录日志起点行号（只统计本次增量）
LOG_START=$(wc -l < "$LOG_FILE")

echo ""
echo "== 并发 $N 个对话（SSE 流式）=="
TS=$(date +%s%3N)
for i in $(seq 1 "$N"); do
  p="${prompts[$(( (i - 1) % ${#prompts[@]} ))]}"
  curl -sN -m "$TO" \
    -o "$OUTDIR/body.$i" \
    -w "@$CFMT" \
    -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"message\":\"$p\",\"conversationId\":null}" \
    "$BASE/api/chat/stream" > "$OUTDIR/meta.$i" &
done
wait
TE=$(date +%s%3N)
ok=0; for m in "$OUTDIR"/meta.*; do grep -q ' code=200' "$m" && ok=$((ok+1)); done
echo "已返回 $N 个请求（HTTP200=$ok），总耗时 $((TE - TS))ms"

# ----------------------------------------------------------------------
echo ""
echo "== 服务端埋点（本次样本）=="
sleep 2   # 等文件 appender flush
tail -n "+$((LOG_START + 1))" "$LOG_FILE" > "$OUTDIR/inc.log" 2>/dev/null || true
awk '
  /\[TTFT\]/       {match($0, /延迟 ([0-9]+)ms/, a);  print "TTFT", a[1]}
  /\[SSE_FRAME\]/  {match($0, /准备 ([0-9]+)ms /, a); print "FRAME", a[1]}
  /\[RAG_TIME\]/   {match($0, /ms=([0-9]+)/, a);      print "RAG", a[1]}
  /\[TOOL_TIME\]/  {match($0, /ms=([0-9]+)/, a);      print "TOOL", a[1]}
' "$OUTDIR/inc.log" > "$OUTDIR/tags.log"

for m in TTFT FRAME RAG TOOL; do
  f="$OUTDIR/$m.nums"
  awk -v m="$m" '$1==m{print $2}' "$OUTDIR/tags.log" | sort -n > "$f"
  [ "$(wc -l < "$f" | tr -d ' ')" -eq 0 ] && { echo "$m    本次无样本"; continue; }
  echo "$m  $(percentile "$f")"
done

# ----------------------------------------------------------------------
echo ""
echo "== 客户端计时（首帧 / 总耗时）=="
grep -h ' code=200' "$OUTDIR"/meta.* > "$OUTDIR/ok.meta" 2>/dev/null || true
for m in start total; do
  f="$OUTDIR/c.$m"
  sed -n "s/.*${m}=\([0-9.]*\).*/\1/p" "$OUTDIR/ok.meta" |
      awk '{printf "%d\n", $1 * 1000}' | sort -n > "$f"
  [ "$(wc -l < "$f" | tr -d ' ')" -eq 0 ] && continue
  echo "client_$m  $(percentile "$f")"
done

echo ""
echo "== 复核：手动收敛最近 20 条 =="
grep -E '\[(TTFT|RAG_TIME|TOOL_TIME)\]' "$OUTDIR/inc.log" | tail -n 20

echo ""
echo "脚本结束。把这屏 p95 数字摘出来即可写进简历/README（注明：本机单实例、N 并发、某环境）。"
rm -rf "$OUTDIR" "$CFMT"