#!/usr/bin/env bash
set -e -o pipefail

cd /usr/share/fortishield-indexer/bin/

/usr/local/bin/docker-entrypoint.sh | tee > /usr/share/fortishield-indexer/logs/console.log
