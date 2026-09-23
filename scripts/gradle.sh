#!/usr/bin/env bash
# Runs the Gradle wrapper on JDK 25.
# Uses the host JVM when it is 25+; otherwise runs inside eclipse-temurin:25-jdk,
# sharing the Docker socket so Testcontainers-based tests keep working.
# Set GRADLE_IN_DOCKER=1 to force the container.
#
# Usage: scripts/gradle.sh <tasks...>    e.g. scripts/gradle.sh clean test
set -euo pipefail

cd "$(dirname "$0")/.."

host_java_major() {
  command -v java >/dev/null 2>&1 || { echo 0; return; }
  java -version 2>&1 | sed -nE '1s/.*version "([0-9]+).*/\1/p'
}

if [[ "${GRADLE_IN_DOCKER:-0}" != "1" && "$(host_java_major)" -ge 25 ]]; then
  exec ./gradlew "$@"
fi

gradle_cache="${GRADLE_DOCKER_CACHE:-$HOME/.gradle-docker}"
mkdir -p "$gradle_cache"

exec docker run --rm \
  --user "$(id -u):$(id -g)" \
  --group-add "$(stat -c %g /var/run/docker.sock)" \
  -e HOME=/tmp \
  -e GRADLE_USER_HOME=/gradle \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  --add-host host.docker.internal:host-gateway \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$gradle_cache":/gradle \
  -v "$PWD":/project -w /project \
  eclipse-temurin:25-jdk ./gradlew "$@" --no-daemon
