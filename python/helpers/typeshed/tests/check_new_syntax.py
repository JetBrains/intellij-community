#!/usr/bin/env python3

import ast
import sys
from itertools import chain
from pathlib import Path

STUBS_SUPPORTING_PYTHON_2 = frozenset(
    path.parent for path in Path("stubs").rglob("METADATA.toml") if "python2 = true" in path.read_text().splitlines()
)


def check_new_syntax(tree: ast.AST, path: Path) -> list[str]:
    errors = []
    python_2_support_required = any(directory in path.parents for directory in STUBS_SUPPORTING_PYTHON_2)

    class UnionFinder(ast.NodeVisitor):
        def visit_Subscript(self, node: ast.Subscript) -> None:
            if isinstance(node.value, ast.Name):
                if node.value.id == "Union" and isinstance(node.slice, ast.Tuple):
                    new_syntax = " | ".join(ast.unparse(x) for x in node.slice.elts)
                    errors.append(f"{path}:{node.lineno}: Use PEP 604 syntax for Union, e.g. `{new_syntax}`")
                if node.value.id == "Optional":
                    new_syntax = f"{ast.unparse(node.slice)} | None"
                    errors.append(f"{path}:{node.lineno}: Use PEP 604 syntax for Optional, e.g. `{new_syntax}`")

            self.generic_visit(node)

    class OldSyntaxFinder(ast.NodeVisitor):
        def visit_AnnAssign(self, node: ast.AnnAssign) -> None:
            UnionFinder().visit(node.annotation)

        def visit_arg(self, node: ast.arg) -> None:
            if node.annotation is not None:
                UnionFinder().visit(node.annotation)

        def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
            if node.returns is not None:
                UnionFinder().visit(node.returns)
            self.generic_visit(node)

        def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> None:
            if node.returns is not None:
                UnionFinder().visit(node.returns)
            self.generic_visit(node)

    class ObjectClassdefFinder(ast.NodeVisitor):
        def visit_ClassDef(self, node: ast.ClassDef) -> None:
            if any(isinstance(base, ast.Name) and base.id == "object" for base in node.bases):
                errors.append(
                    f"{path}:{node.lineno}: Do not inherit from `object` explicitly, "
                    f"as all classes implicitly inherit from `object` in Python 3"
                )
            self.generic_visit(node)

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

    if not python_2_support_required:
        ObjectClassdefFinder().visit(tree)

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
            tree = ast.parse(f.read())
        errors.extend(check_new_syntax(tree, path))

    if errors:
        print("\n".join(errors))
        sys.exit(1)


if __name__ == "__main__":
    main()
