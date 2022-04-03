#!/bin/bash

clojure -M:test -m kaocha.runner "$@"
