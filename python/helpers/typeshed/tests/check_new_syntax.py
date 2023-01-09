#!/usr/bin/env python3

from __future__ import annotations

import ast
import sys
from itertools import chain
from pathlib import Path


def check_new_syntax(tree: ast.AST, path: Path, stub: str) -> list[str]:
    errors = []
    sourcelines = stub.splitlines()

    class AnnotationUnionFinder(ast.NodeVisitor):
        def visit_Subscript(self, node: ast.Subscript) -> None:
            if isinstance(node.value, ast.Name):
                if node.value.id == "Union" and isinstance(node.slice, ast.Tuple):
                    new_syntax = " | ".join(ast.unparse(x) for x in node.slice.elts)
                    errors.append(f"{path}:{node.lineno}: Use PEP 604 syntax for Union, e.g. `{new_syntax}`")
                if node.value.id == "Optional":
                    new_syntax = f"{ast.unparse(node.slice)} | None"
                    errors.append(f"{path}:{node.lineno}: Use PEP 604 syntax for Optional, e.g. `{new_syntax}`")

            self.generic_visit(node)

    class NonAnnotationUnionFinder(ast.NodeVisitor):
        def visit_Subscript(self, node: ast.Subscript) -> None:
            if isinstance(node.value, ast.Name):
                nodelines = sourcelines[(node.lineno - 1) : node.end_lineno]
                for line in nodelines:
                    # A hack to workaround various PEP 604 bugs in mypy
                    if any(x in line for x in {"tuple[", "Callable[", "type["}):
                        return None
                if node.value.id == "Union" and isinstance(node.slice, ast.Tuple):
                    new_syntax = " | ".join(ast.unparse(x) for x in node.slice.elts)
                    errors.append(f"{path}:{node.lineno}: Use PEP 604 syntax for Union, e.g. `{new_syntax}`")
                elif node.value.id == "Optional":
                    new_syntax = f"{ast.unparse(node.slice)} | None"
                    errors.append(f"{path}:{node.lineno}: Use PEP 604 syntax for Optional, e.g. `{new_syntax}`")

            self.generic_visit(node)

    class OldSyntaxFinder(ast.NodeVisitor):
        def visit_AnnAssign(self, node: ast.AnnAssign) -> None:
            AnnotationUnionFinder().visit(node.annotation)

        def visit_arg(self, node: ast.arg) -> None:
            if node.annotation is not None:
                AnnotationUnionFinder().visit(node.annotation)

        def _visit_function(self, node: ast.FunctionDef | ast.AsyncFunctionDef) -> None:
            if node.returns is not None:
                AnnotationUnionFinder().visit(node.returns)
            self.generic_visit(node)

        def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
            self._visit_function(node)

        def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> None:
            self._visit_function(node)

        def visit_Assign(self, node: ast.Assign) -> None:
            NonAnnotationUnionFinder().visit(node.value)

        def visit_ClassDef(self, node: ast.ClassDef) -> None:
            for base in node.bases:
                NonAnnotationUnionFinder().visit(base)

    class ObjectClassdefFinder(ast.NodeVisitor):
        def visit_ClassDef(self, node: ast.ClassDef) -> None:
            if any(isinstance(base, ast.Name) and base.id == "object" for base in node.bases):
                errors.append(
                    f"{path}:{node.lineno}: Do not inherit from `object` explicitly, "
                    f"as all classes implicitly inherit from `object` in Python 3"
                )
            self.generic_visit(node)

    class TextFinder(ast.NodeVisitor):
        def visit_ImportFrom(self, node: ast.ImportFrom) -> None:
            if node.module == "typing" and any(thing.name == "Text" for thing in node.names):
                errors.append(f"{path}:{node.lineno}: Use `str` instead of `typing.Text` in a Python-3-only stub.")

        def visit_Attribute(self, node: ast.Attribute) -> None:
            if isinstance(node.value, ast.Name) and node.value.id == "typing" and node.attr == "Text":
                errors.append(f"{path}:{node.lineno}: Use `str` instead of `typing.Text` in a Python-3-only stub.")

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

    ObjectClassdefFinder().visit(tree)
    if path != Path("stdlib/typing_extensions.pyi"):
        TextFinder().visit(tree)

    OldSyntaxFinder().visit(tree)
    IfFinder().visit(tree)
    return errors


def main() -> None:
    errors = []
    for path in chain(Path("stdlib").rglob("*.pyi"), Path("stubs").rglob("*.pyi")):
        if "@python2" in path.parts:
            continue
        if Path("stubs/protobuf/google/protobuf") in path.parents:  # TODO: fix protobuf stubs
            continue

        with open(path) as f:
            stub = f.read()
            tree = ast.parse(stub)
        errors.extend(check_new_syntax(tree, path, stub))

    if errors:
        print("\n".join(errors))
        sys.exit(1)


if __name__ == "__main__":
    main()
