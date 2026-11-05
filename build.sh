#!/usr/bin/env bash
# Compila o projeto para out/ e, opcionalmente, executa a demo ou os testes.
# Uso: ./build.sh [run|test]
set -euo pipefail
cd "$(dirname "$0")"

MAIN=br.com.sidsidoso.cache.Main
TESTES=br.com.sidsidoso.cache.Testes

rm -rf out
mkdir -p out
find src/main/java src/test/java -name '*.java' | sed 's/.*/"&"/' > out/fontes.txt
javac --release 21 -encoding UTF-8 -Xlint:all -Werror -d out @out/fontes.txt
rm -f out/fontes.txt

case "${1:-}" in
    run)  java -cp out "$MAIN" ;;
    test) java -cp out "$TESTES" ;;
    "")   echo "Compilado em out/" ;;
    *)    echo "uso: $0 [run|test]" >&2; exit 2 ;;
esac
