#!/usr/bin/env bash
set -euo pipefail

# 서버 배포 파일만 생성. 서버의 실행 프로세스와 기존 점수 파일은 건드리지 않음
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_DIR"
BUILD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/tetris-server-build.XXXXXX")"
trap 'rm -rf "$BUILD_DIR"' EXIT
mkdir -p out "$BUILD_DIR/classes"

# Java 9 이상은 API까지 Java 8 기준으로 확인하고, JDK 8은 자체 컴파일러 사용
case "$(javac -version 2>&1)" in
    "javac 1.8."*) COMPAT=(-source 8 -target 8) ;;
    *) COMPAT=(--release 8) ;;
esac
find src -name '*.java' -print0 | xargs -0 javac "${COMPAT[@]}" -encoding UTF-8 -d "$BUILD_DIR/classes"
jar cfe out/tetris-server.jar backend.network.ScoreServer -C "$BUILD_DIR/classes" .
echo "서버 배포 파일: $PROJECT_DIR/out/tetris-server.jar"
