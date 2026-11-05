"""Writes the result of the running CPython for each snippet into cpython.tsv.

Run the script once with each CPython version that the test covers, for example
`python3.15 update_cpython.py`. The script replaces the column of the running
minor version, or adds it, and keeps the columns of the other versions. It drops
the rows of deleted snippets. A new snippet gets an empty cell in the columns of
the other versions, so run the script with each of them.

The result is `OK`, or the class and the message of the `SyntaxError` that
`compile(snippet, "<p>", "exec")` raises. A warning goes after `OK` in brackets.
A tab or a line break in a message is written as `\\t`, `\\n` or `\\r`.
"""

import pathlib
import platform
import sys
import warnings

HERE = pathlib.Path(__file__).resolve().parent
SNIPPETS = HERE / "snippets"
RESULTS = HERE / "cpython.tsv"


def compile_result(source: str) -> str:
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        try:
            compile(source, "<p>", "exec")
        except SyntaxError as e:
            return f"{type(e).__name__}: {e.msg}"
    if not caught:
        return "OK"
    return "OK  [" + "; ".join(f"{w.category.__name__}: {w.message}" for w in caught) + "]"


def cell(text: str) -> str:
    return text.replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")


def minor_version(column: str) -> str:
    return ".".join(column.removeprefix("CPython ").split(".")[:2])


def main() -> None:
    columns: list[str] = []
    table: dict[str, dict[str, str]] = {}
    if RESULTS.exists():
        lines = [line for line in RESULTS.read_text(encoding="utf-8").split("\n") if line]
        columns = lines[0].split("\t")[1:]
        for line in lines[1:]:
            fields = line.split("\t")
            table[fields[0]] = dict(zip(columns, fields[1:]))

    column = f"CPython {platform.python_version()}"
    minor = f"{sys.version_info.major}.{sys.version_info.minor}"
    columns = [c for c in columns if minor_version(c) != minor] + [column]
    columns.sort(key=lambda c: tuple(int(part) for part in minor_version(c).split(".")))

    ids = sorted(path.stem for path in SNIPPETS.glob("*.py"))
    rows = []
    for snippet_id in ids:
        source = (SNIPPETS / f"{snippet_id}.py").read_bytes().decode("utf-8")
        results = table.get(snippet_id, {})
        results[column] = cell(compile_result(source))
        rows.append([snippet_id] + [results.get(c, "") for c in columns])

    with RESULTS.open("w", encoding="utf-8", newline="\n") as out:
        out.write("\t".join(["# id"] + columns) + "\n")
        for row in rows:
            out.write("\t".join(row) + "\n")

    missing = [row[0] for row in rows if "" in row[1:]]
    if missing:
        print(f"Empty cells for {len(missing)} snippets, run the script with the other versions: {', '.join(missing)}")


if __name__ == "__main__":
    main()
