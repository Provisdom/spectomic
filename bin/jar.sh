#!/bin/bash

set -euo pipefail

clojure -Spom

mkdir -p extra/META-INF
cp pom.xml extra/META-INF

clojure -A:jar