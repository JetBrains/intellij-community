import sys

from formatlib import format_table
from formatlib.table import TableBuilder
from mathlib import mean, median
from mathlib.stats import DataSeries, stdev, FooBar



class AnalysisResult:
    def __init__(self, series: DataSeries) -> None:
        self.series = series

    def as_table(self) -> str:
        builder = TableBuilder(["Statistic", "Value"])
        builder.add_row("Mean", f"{self.series.mean():.2f}")
        builder.add_row("Median", f"{self.series.median():.2f}")
        builder.add_row("Stdev", f"{self.series.stdev():.2f}")
        return builder.build()


class BatchAnalyzer:
    def __init__(self) -> None:
        self._results: list[AnalysisResult] = []

    def add(self, name: str, values: list[float]) -> None:
        self._results.append(AnalysisResult(DataSeries(name, values)))

    def summary(self) -> str:
        parts = [r.as_table() for r in self._results]
        return "\n\n".join(parts)


def main() -> None:
    numbers = [float(x) for x in sys.argv[1:]]
    if not numbers:
        print("Usage: analyzer <number> ...")
        sys.exit(1)

    result = AnalysisResult(DataSeries("input", numbers))
    print(result.as_table())
