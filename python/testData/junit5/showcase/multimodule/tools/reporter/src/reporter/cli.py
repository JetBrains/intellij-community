import sys

from formatlib import format_table
from formatlib.table import <caret>


class Report:
    def __init__(self, title: str) -> None:
        self.title = title
        self._entries: list[tuple[str, str]] = []

    def add_entry(self, key: str, value: str) -> None:
        self._entries.append((key, value))

    def render(self) -> str:
        builder = TableBuilder(["Key", "Value"])
        for k, v in self._entries:
            builder.add_row(k, v)
        return f"=== {self.title} ===\n{builder.build()}"


class ReportExporter:
    def __init__(self, delimiter: str = ",") -> None:
        self._formatter = CsvFormatter(delimiter)

    def export(self, report: Report) -> str:
        headers = ["Key", "Value"]
        rows = [[k, v] for k, v in report._entries]
        return self._formatter.format(headers, rows)


def main() -> None:
    pairs = sys.argv[1:]
    if not pairs:
        print("Usage: reporter <key=value> ...")
        sys.exit(1)

    report = Report("Report")
    for pair in pairs:
        key, _, value = pair.partition("=")
        report.add_entry(key, value)
    print(report.render())
