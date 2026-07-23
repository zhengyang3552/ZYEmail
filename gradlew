#!/bin/sh
# Gradle wrapper script - generates gradle wrapper and runs gradle
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "ANDROID_HOME or ANDROID_SDK_ROOT environment variable not set."
    echo "Please set it to the path of your Android SDK."
    exit 1
fi

GRADLE_HOME="$HOME/.gradle/wrapper/dists/gradle-8.2-bin"

# Download gradle if not exists
if [ ! -d "$GRADLE_HOME" ]; then
    echo "Downloading Gradle 8.2..."
    mkdir -p "$GRADLE_HOME"
    GRADLE_ZIP="$HOME/.gradle/wrapper/dists/gradle-8.2-bin.zip"
    if [ ! -f "$GRADLE_ZIP" ]; then
        curl -L "https://services.gradle.org/distributions/gradle-8.2-bin.zip" -o "$GRADLE_ZIP"
        unzip -q "$GRADLE_ZIP" -d "$GRADLE_HOME"
    fi
fi

# Run gradle
GRADLE_BIN=$(find "$GRADLE_HOME" -name "gradle" -type f 2>/dev/null | head -1)
if [ -z "$GRADLE_BIN" ]; then
    GRADLE_BIN="$GRADLE_HOME/gradle-8.2/bin/gradle"
fi

exec "$GRADLE_BIN" "$@"
