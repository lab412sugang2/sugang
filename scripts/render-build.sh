#!/usr/bin/env bash
set -euo pipefail

./gradlew clean bootJar -x test --no-daemon
