#!/usr/bin/env sh
set -eu

DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi

WRAPPER_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
PROP_FILE="$DIR/gradle/wrapper/gradle-wrapper.properties"

if [ ! -f "$WRAPPER_JAR" ]; then
    mkdir -p "$DIR/gradle/wrapper"
    DIST_URL="$(grep -E '^distributionUrl=' "$PROP_FILE" | cut -d'=' -f2- | sed 's/\\:/:/g')"
    GRADLE_VER="$(echo "$DIST_URL" | sed -n 's/.*gradle-\([0-9.]*\)-.*/\1/p')"
    GRADLE_VER="${GRADLE_VER:-8.9}"
    CACHE_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/gradle-${GRADLE_VER}-bin"
    mkdir -p "$CACHE_DIR"
    ZIP_PATH="$CACHE_DIR/gradle-${GRADLE_VER}-bin.zip"
    UNPACK_DIR="$CACHE_DIR/unpacked"
    if [ ! -d "$UNPACK_DIR/gradle-${GRADLE_VER}" ]; then
        curl -fsSL "$DIST_URL" -o "$ZIP_PATH"
        mkdir -p "$UNPACK_DIR"
        unzip -q "$ZIP_PATH" -d "$UNPACK_DIR"
    fi
    exec "$UNPACK_DIR/gradle-${GRADLE_VER}/bin/gradle" "$@"
fi

exec java -Xmx1536m -jar "$WRAPPER_JAR" "$@"
