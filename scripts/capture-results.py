#!/usr/bin/env python3
"""Capture real Java execution logs and render readable evidence pages for screenshots."""
import datetime
import html
import pathlib
import subprocess
import xml.etree.ElementTree as ET
from zoneinfo import ZoneInfo

ROOT = pathlib.Path(__file__).resolve().parents[1]
LOGS = ROOT / "docs" / "logs"
PAGES = ROOT / "docs" / "evidence-pages"
LOGS.mkdir(parents=True, exist_ok=True)
PAGES.mkdir(parents=True, exist_ok=True)
timestamp = datetime.datetime.now(ZoneInfo("Asia/Shanghai")).strftime("%Y-%m-%d %H:%M:%S +08:00")


def page(filename, title, command, output):
    content = f"""<!doctype html>
<html lang="zh-CN"><meta charset="utf-8"><title>{html.escape(title)}</title>
<style>
*{{box-sizing:border-box}}body{{margin:0;padding:32px;background:#f2f5f8;color:#15283c;font-family:-apple-system,BlinkMacSystemFont,'PingFang SC',sans-serif}}
main{{max-width:1200px;margin:0 auto;background:white;border:1px solid #d5dee7;border-radius:14px;overflow:hidden}}
header{{padding:24px 30px;background:#173a55;color:white}}h1{{margin:0 0 8px;font-size:25px}}
header p{{margin:0;font-size:14px;color:#d4e7f6}}.command{{padding:18px 30px;border-bottom:1px solid #d5dee7;font:16px 'SFMono-Regular',Menlo,monospace;background:#f6f9fc}}
pre{{margin:0;padding:24px 30px;font:16px/1.4 Menlo,'PingFang SC',monospace;white-space:pre-wrap;overflow-wrap:anywhere}}
footer{{padding:14px 30px;border-top:1px solid #d5dee7;font-size:13px;color:#526575}}
@media print{{body{{padding:0}}}}
</style><main><header><h1>{html.escape(title)}</h1><p>作业1 · JDBC工具的设计与开发 · 实际执行日志</p></header>
<div class="command">{html.escape(command)}</div><pre>{html.escape(output)}</pre>
<footer>采集时间：{timestamp} · 数据库：H2 / DateTest · 完整日志见 docs/logs/</footer></main></html>"""
    (PAGES / filename).write_text(content, encoding="utf-8")


for scope, title in [("student", "学生表：五个方法运行结果"), ("college", "学院表：五个方法运行结果")]:
    command = ["java", "-jar", "target/jdbc-tool-homework-1.0.0.jar", "--h2", "--" + scope]
    result = subprocess.run(command, cwd=ROOT, text=True, encoding="utf-8", capture_output=True)
    output = result.stdout + result.stderr
    if result.returncode != 0:
        raise RuntimeError(output)
    (LOGS / (scope + ".txt")).write_text("$ " + " ".join(command) + "\n" + output, encoding="utf-8")
    page(scope + ".html", title, "$ " + " ".join(command), output)

reports = list((ROOT / "target" / "surefire-reports").glob("TEST-*.xml"))
if not reports:
    raise RuntimeError("请先执行 mvn clean package，生成测试报告后再采集。")
totals = dict(tests=0, failures=0, errors=0, skipped=0)
lines = ["Maven / JUnit 测试报告（从 target/surefire-reports/TEST-*.xml 读取）", ""]
for report in reports:
    suite = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.attrib.get(key, 0))
    lines.append("测试类：" + suite.attrib["name"])
    lines.append("Tests run: {tests}, Failures: {failures}, Errors: {errors}, Skipped: {skipped}".format(**suite.attrib))
    lines.append("")
    for test in suite.findall("testcase"):
        passed = not any(test.find(kind) is not None for kind in ("failure", "error", "skipped"))
        lines.append(("[PASS] " if passed else "[CHECK] ") + test.attrib["name"])
if totals["failures"] or totals["errors"] or totals["skipped"]:
    raise RuntimeError("测试存在失败、错误或跳过，请检查原报告。")
lines.extend(["", "合计：" + "Tests run: {tests}, Failures: {failures}, Errors: {errors}, Skipped: {skipped}".format(**totals)])
test_output = "\n".join(lines)
(LOGS / "tests.txt").write_text(test_output + "\n", encoding="utf-8")
page("tests.html", "Maven：集成测试结果", "$ mvn clean package", test_output)
test_page = PAGES / "tests.html"
test_page.write_text(test_page.read_text(encoding="utf-8").replace("font:16px/1.4 Menlo", "font:13px/1.4 Menlo"), encoding="utf-8")
print("已从真实 Java 执行与 JUnit 报告生成日志和截图页面。")
