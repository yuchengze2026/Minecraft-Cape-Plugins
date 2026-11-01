#!/usr/bin/env sh
set -eu
build_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec mvn -f "$build_root/pom.xml" clean verify "$@"
