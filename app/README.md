# 本地回放台

在仓库根目录执行 `mvn -B -ntp clean verify`。启动后打开终端显示的本地地址，默认是 `http://127.0.0.1:8080`。

| 模式 | Windows PowerShell | macOS / Linux |
| --- | --- | --- |
| 基础文本 | `./app/replay.cmd` | `sh app/replay.sh` |
| 启用 JSONL | `./app/replay.cmd 8080 plugins/jsonl/target/loginsight-jsonl-0.1.0-SNAPSHOT.jar` | `sh app/replay.sh 8080 plugins/jsonl/target/loginsight-jsonl-0.1.0-SNAPSHOT.jar` |

启动器的第一个参数是端口，第二个参数是可选的插件 JAR 路径。省略插件参数并重启，即可停用 JSONL。应用只监听回环地址，导入记录保存在进程内存中，重启后请重新导入。单次导入请求的 JSON 编码后上限为 8 MiB。遇到坏行时整份文件不入库，页面显示文件名和物理行号。

1. 导入 `samples/campus.txt`，点击 08:00 UTC 的错误柱，再选 `R-17`。时间线有 3 项，点击最后一项可核对 `campus.txt` 第 5 行。
2. 启用插件并重启，依次导入 `samples/campus.txt` 和 `samples/quota.jsonl`。同一时段有 2 条 ERROR，`R-17` 时间线有 5 项。点击“获取连接超时”，核对 `quota.jsonl` 第 2 行完整原文。
3. 用省略插件参数的命令重启。导入 JSONL 应显示缺少解析器，导入基础文本仍可正常回放。

构建后在仓库根目录运行 `python app/tests/replay_e2e.py`。测试从打包后的 Java 入口启动独立的进程，通过 HTTP 检查错误统计、失败请求、事件顺序、来源原文、插件启停和坏行提示。
