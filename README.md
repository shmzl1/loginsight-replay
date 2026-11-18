# LogInsight 应用故障回放台

开源软件生态与开发技术 · 四人课程项目。开发者遇到校园选课失败时，先从入口日志定位失败请求，再补入名额服务日志，按 requestId 回放调用经过并核对原文。[GitHub 仓库](https://github.com/shmzl1/loginsight-replay) · [系统设计初稿](docs/DESIGN.docx)

## 快速开始

环境为 JDK 17、Maven 3.9。以下命令均从仓库根目录执行。

```sh
mvn -B -ntp clean verify
```

main 初始基线提供四个模块、样例和 CI 配置。此阶段业务测试数为 0，尚无可启动页面。契约分支已在 JDK 17 通过 5 项 JUnit 测试，Checkstyle 为 0 违规。测试覆盖服务发现与输出、格式缺失、重名、坏行和空白请求标识。工作流在推送 main 和面向 main 的 PR 上执行同一命令，包含编译、JUnit 和针对生产、测试代码的 Checkstyle。[工作流运行](https://github.com/shmzl1/loginsight-replay/actions/workflows/verify.yml)

## 样例与人工核对

先导入 [campus.txt](samples/campus.txt)，再启用插件并导入 [quota.jsonl](samples/quota.jsonl)。两份文件都是虚构的 UTF-8 数据。文本每行前四个空格分隔字段依次为时间、请求、服务和级别，剩余内容为消息。JSONL 每行一个对象，字段见设计初稿。

| 导入数据 | 08:00 UTC 分钟内 ERROR 数 | R-17 时间线（文件和物理行号） |
| --- | ---: | --- |
| 仅基础文本 | 1 | campus:1 → campus:3 → campus:5 |
| 两份日志 | 2 | campus:1 → campus:3 → quota:1 → quota:2 → campus:5 |

两次都应筛出一个失败请求 R-17。R-18 只含 campus 第 2、4 行。选中“获取连接超时”应显示 quota.jsonl 第 2 行完整原文。上方错误分布、中央时间线与右侧深色原文面板都由导入数据驱动。

## 独立插件接入计划

1. 安装父 POM 和公开 API：`mvn -N install`、`mvn -pl api install`。
2. C 在 plugins/jsonl 实现 `org.loginsight.api.LogParser`，以 jsonl 为格式标识，在 `META-INF/services/org.loginsight.api.LogParser` 登记 public 无参实现的全名。插件依赖相同版本 API（provided）及自身 JSON 依赖。
3. 独立打包：`mvn -f plugins/jsonl/pom.xml clean package`。将插件 JAR 及必要依赖放入计划中的发行目录 plugins/。启动器将 lib/* 与 plugins/* 加入类路径，API 由宿主提供。
4. 重启后重新导入两份样例。移出插件及专用依赖再重启，验证文本仍可用、jsonl 缺失有明确提示。核对核心源码及宿主制品在两次运行间一致。设计允许重启，无需重打包宿主。正式插件与发行入口待 C、D 完成。

## 分工与合并规则

| 成员 | 代码与验证责任 | 建议审查对象 |
| --- | --- | --- |
| A（组长） | api、SPI 发现与选择、输入调度与插件错误约定；基础 CI，最终制品核对与发布 | C |
| B | core 内置文本、坏行、请求关联、跨来源排序、错误分布与失败请求筛选；业务断言 | A |
| C | 独立 JSONL JAR、字段映射、SPI 注册、插件测试；与 A/D 联验禁用和失败 | D |
| D | app 本地 Web 入口、导入和回放界面、原文定位、端到端测试与发行启动器 | B |

课程允许 2—4 人，本组四人聚焦一个核心场景、一种 SPI 机制和一个独立扩展。四个分工 Issue 为本组安排。成员接受邀请后使用本人账号，从更新的 main 建分支，关联问题提交 PR。审查人针对行为、失败处理、边界、接口或测试留下具体意见；作者修改或说明依据，审查人复核后由组长或获授权维护者合并。每人至少提交一项实质代码或测试并审查他人一次，能解释完整流程及一项他人接口。上述审查安排待实际执行。

### 成员贡献与 AI 核验

| 姓名/学号（本人补充） | 本人改动及 Commit/PR | 审查他人的改动及结果 | 关键决策与 AI 核验 |
| --- | --- | --- | --- |
| A | 初始化；feat/parser-contract 提供公开模型与 SPI 选择 | 待实际审查 | 采用 AI 建议，将测试提供者仅注册在 test 资源中。以 JDK 17 运行 clean verify，5 项断言测试通过，生产 JAR 单独核查。 |
| B | 待本人填写 | 待实际审查 | 待本人填写 |
| C | 待本人填写 | 待实际审查 | 待本人填写 |
| D | 待本人填写 | 待实际审查 | 待本人填写 |

使用 AI 的成员记录一条建议、采用与否、验证方法及结论；实际未使用者填“未使用”。最终在临时副本改错一处解析器选择或事件排序，确认断言失败，恢复后通过。

## 许可、限制与最终交付

采用 [MIT](LICENSE)，便于教学复用及独立编写插件，分发时保留版权与许可声明。JUnit 5（EPL-2.0）用于测试，Maven 插件（Apache-2.0）用于构建，Checkstyle（LGPL-2.1-or-later）用于检查。发布时按实际分发依赖保留各自 LICENSE、版权和适用的 NOTICE。

本期采用小文件、内存分析和本地网页，范围以带 requestId 的 UTF-8 日志为限。实时采集、数据库、登录、云部署和热加载均超出范围。正式发布前提交版本说明，核对干净工作区、构建和测试，并实际运行发行物，再为最终 Commit 打 Tag。最终 ZIP 的 project/ 含隐藏 .git、源码、独立扩展、测试、样例、依赖、CI、README、LICENSE 与 docs/DESIGN.docx；并列 release/ 含验证过的制品和 RELEASE.md。记录完整 Commit、Tag 与目标完整 SHA、制品 SHA-256、启动命令、实测结果、限制和 RELEASE/HOLD 决定。提交前在新目录解压复验。
