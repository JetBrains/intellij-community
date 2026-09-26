def format_table(headers: list[str], rows: list[list[str]]) -> str:
    col_widths = [len(h) for h in headers]
    for row in rows:
        for i, cell in enumerate(row):
            col_widths[i] = max(col_widths[i], len(cell))

    def fmt_row(cells: list[str]) -> str:
        return "| " + " | ".join(c.ljust(col_widths[i]) for i, c in enumerate(cells)) + " |"

    header_line = fmt_row(headers)
    separator = "|-" + "-|-".join("-" * w for w in col_widths) + "-|"
    data_lines = [fmt_row(row) for row in rows]
    return "\n".join([header_line, separator, *data_lines])


class TableBuilder:
    def __init__(self, headers: list[str]) -> None:
        self.headers = headers
        self._rows: list[list[str]] = []

    def add_row(self, *cells: str) -> "TableBuilder":
        self._rows.append(list(cells))
        return self

    def build(self) -> str:
        return format_table(self.headers, self._rows)


class CsvFormatter:
    def __init__(self, delimiter: str = ",") -> None:
        self.delimiter = delimiter

    def format(self, headers: list[str], rows: list[list[str]]) -> str:
        lines = [self.delimiter.join(headers)]
        for row in rows:
            lines.append(self.delimiter.join(row))
        return "\n".join(lines)
