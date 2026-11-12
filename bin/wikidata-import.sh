#!/usr/bin/env sh
set -eu

dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

if [ -f "$dir/tools.jar" ]; then
    jar="$dir/tools.jar"
else
    jar="$dir/../target/tools.jar"
fi

if [ ! -f "$jar" ]; then
    echo "tools.jar not found, run ./mvnw package first" >&2
    exit 1
fi

exec java ${JAVA_OPTS:-} -jar "$jar" "$@"
