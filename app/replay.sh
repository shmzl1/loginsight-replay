#!/bin/sh
set -eu
app_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
exec java -cp "$app_dir/target/loginsight-app-0.1.0-SNAPSHOT.jar:$app_dir/target/lib/*" org.loginsight.app.ReplayServer "$@"
