#!/usr/bin/env python3

from __future__ import annotations

import ast
import sys
from itertools import chain
from pathlib import Path


def check_new_syntax(tree: ast.AST, path: Path, stub: str) -> list[str]:
    errors: list[str] = []

    class IfFinder(ast.NodeVisitor):
        def visit_If(self, node: ast.If) -> None:
            if (
                isinstance(node.test, ast.Compare)
                and ast.unparse(node.test).startswith("sys.version_info < ")
                and node.orelse
                and not (len(node.orelse) == 1 and isinstance(node.orelse[0], ast.If))  # elif statement (#6728)
            ):
                new_syntax = "if " + ast.unparse(node.test).replace("<", ">=", 1)
                errors.append(
                    f"{path}:{node.lineno}: When using if/else with sys.version_info, "
                    f"put the code for new Python versions first, e.g. `{new_syntax}`"
                )
            self.generic_visit(node)

    class PEP570Finder(ast.NodeVisitor):
        def __init__(self) -> None:
            self.lineno: int | None = None

        def _visit_function(self, node: ast.FunctionDef | ast.AsyncFunctionDef) -> None:
            old_lineno = self.lineno
            self.lineno = node.lineno
            self.generic_visit(node)
            self.lineno = old_lineno

        visit_FunctionDef = visit_AsyncFunctionDef = _visit_function

        def visit_arguments(self, node: ast.arguments) -> None:
            if node.posonlyargs:
                assert isinstance(self.lineno, int)
                errors.append(
                    f"{path}:{self.lineno}: PEP-570 syntax cannot be used in typeshed yet. "
                    f"Prefix parameter names with `__` to indicate positional-only parameters"
                )
            self.generic_visit(node)

    IfFinder().visit(tree)
    PEP570Finder().visit(tree)
    return errors


def main() -> None:
    errors: list[str] = []
    for path in chain(Path("stdlib").rglob("*.pyi"), Path("stubs").rglob("*.pyi")):
        with open(path, encoding="UTF-8") as f:
            stub = f.read()
            tree = ast.parse(stub)
        errors.extend(check_new_syntax(tree, path, stub))

    if errors:
        print("\n".join(errors))
        sys.exit(1)


if __name__ == "__main__":
    main()
