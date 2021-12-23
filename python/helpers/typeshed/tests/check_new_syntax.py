#!/usr/bin/env python3

import ast
import sys
from itertools import chain
from pathlib import Path

STUBS_SUPPORTING_PYTHON_2 = frozenset(
    path.parent for path in Path("stubs").rglob("METADATA.toml") if "python2 = true" in path.read_text().splitlines()
)

CONTEXT_MANAGER_ALIASES = {"ContextManager": "AbstractContextManager", "AsyncContextManager": "AbstractAsyncContextManager"}
CONTEXTLIB_ALIAS_ALLOWLIST = frozenset({Path("stdlib/contextlib.pyi"), Path("stdlib/typing_extensions.pyi")})

IMPORTED_FROM_TYPING_NOT_TYPING_EXTENSIONS = frozenset(
    {"ClassVar", "Type", "NewType", "overload", "Text", "Protocol", "runtime_checkable", "NoReturn"}
)

IMPORTED_FROM_COLLECTIONS_ABC_NOT_TYPING_EXTENSIONS = frozenset(
    {"Awaitable", "Coroutine", "AsyncIterable", "AsyncIterator", "AsyncGenerator"}
)

# The values in the mapping are what these are called in `collections`
IMPORTED_FROM_COLLECTIONS_NOT_TYPING_EXTENSIONS = {
    "Counter": "Counter",
    "Deque": "deque",
    "DefaultDict": "defaultdict",
    "OrderedDict": "OrderedDict",
    "ChainMap": "ChainMap",
}


def check_new_syntax(tree: ast.AST, path: Path) -> list[str]:
    errors = []
    python_2_support_required = any(directory in path.parents for directory in STUBS_SUPPORTING_PYTHON_2)

    def unparse_without_tuple_parens(node: ast.AST) -> str:
        if isinstance(node, ast.Tuple) and node.elts:
            return ast.unparse(node)[1:-1]
        return ast.unparse(node)

    def is_dotdotdot(node: ast.AST) -> bool:
        return isinstance(node, ast.Constant) and node.s is Ellipsis

    def add_contextlib_alias_error(node: ast.ImportFrom | ast.Attribute, alias: str) -> None:
        errors.append(f"{path}:{node.lineno}: Use `contextlib.{CONTEXT_MANAGER_ALIASES[alias]}` instead of `typing.{alias}`")

    class OldSyntaxFinder(ast.NodeVisitor):
        def __init__(self, *, set_from_collections_abc: bool) -> None:
            self.set_from_collections_abc = set_from_collections_abc

        def visit_Subscript(self, node: ast.Subscript) -> None:
            if isinstance(node.value, ast.Name):
                if node.value.id == "Union" and isinstance(node.slice, ast.Tuple):
                    new_syntax = " | ".join(ast.unparse(x) for x in node.slice.elts)
                    errors.append(f"{path}:{node.lineno}: Use PEP 604 syntax for Union, e.g. `{new_syntax}`")
                if node.value.id == "Optional":
                    new_syntax = f"{ast.unparse(node.slice)} | None"
                    errors.append(f"{path}:{node.lineno}: Use PEP 604 syntax for Optional, e.g. `{new_syntax}`")
                if node.value.id in {"List", "FrozenSet"}:
                    new_syntax = f"{node.value.id.lower()}[{ast.unparse(node.slice)}]"
                    errors.append(f"{path}:{node.lineno}: Use built-in generics, e.g. `{new_syntax}`")
                if not self.set_from_collections_abc and node.value.id == "Set":
                    new_syntax = f"set[{ast.unparse(node.slice)}]"
                    errors.append(f"{path}:{node.lineno}: Use built-in generics, e.g. `{new_syntax}`")
                if node.value.id == "Deque":
                    new_syntax = f"collections.deque[{ast.unparse(node.slice)}]"
                    errors.append(f"{path}:{node.lineno}: Use `collections.deque` instead of `typing.Deque`, e.g. `{new_syntax}`")
                if node.value.id == "Dict":
                    new_syntax = f"dict[{unparse_without_tuple_parens(node.slice)}]"
                    errors.append(f"{path}:{node.lineno}: Use built-in generics, e.g. `{new_syntax}`")
                if node.value.id == "DefaultDict":
                    new_syntax = f"collections.defaultdict[{unparse_without_tuple_parens(node.slice)}]"
                    errors.append(
                        f"{path}:{node.lineno}: Use `collections.defaultdict` instead of `typing.DefaultDict`, "
                        f"e.g. `{new_syntax}`"
                    )
                # Tuple[Foo, ...] must be allowed because of mypy bugs
                if node.value.id == "Tuple" and not (
                    isinstance(node.slice, ast.Tuple) and len(node.slice.elts) == 2 and is_dotdotdot(node.slice.elts[1])
                ):
                    new_syntax = f"tuple[{unparse_without_tuple_parens(node.slice)}]"
                    errors.append(f"{path}:{node.lineno}: Use built-in generics, e.g. `{new_syntax}`")

            self.generic_visit(node)

    # This doesn't check type aliases (or type var bounds, etc), since those are not
    # currently supported
    #
    # TODO: can use built-in generics in type aliases
    class AnnotationFinder(ast.NodeVisitor):
        def __init__(self) -> None:
            self.set_from_collections_abc = False

        def old_syntax_finder(self) -> OldSyntaxFinder:
            """Convenience method to create an `OldSyntaxFinder` instance with the correct state"""
            return OldSyntaxFinder(set_from_collections_abc=self.set_from_collections_abc)

        def visit_ImportFrom(self, node: ast.ImportFrom) -> None:
            if node.module == "collections.abc":
                imported_classes = node.names
                if any(cls.name == "Set" for cls in imported_classes):
                    self.set_from_collections_abc = True

            elif node.module == "typing_extensions":
                for imported_object in node.names:
                    imported_object_name = imported_object.name
                    if imported_object_name in IMPORTED_FROM_TYPING_NOT_TYPING_EXTENSIONS:
                        errors.append(
                            f"{path}:{node.lineno}: "
                            f"Use `typing.{imported_object_name}` instead of `typing_extensions.{imported_object_name}`"
                        )
                    elif imported_object_name in IMPORTED_FROM_COLLECTIONS_ABC_NOT_TYPING_EXTENSIONS:
                        errors.append(
                            f"{path}:{node.lineno}: "
                            f"Use `collections.abc.{imported_object_name}` or `typing.{imported_object_name}` "
                            f"instead of `typing_extensions.{imported_object_name}`"
                        )
                    elif imported_object_name in IMPORTED_FROM_COLLECTIONS_NOT_TYPING_EXTENSIONS:
                        errors.append(
                            f"{path}:{node.lineno}: "
                            f"Use `collections.{IMPORTED_FROM_COLLECTIONS_NOT_TYPING_EXTENSIONS[imported_object_name]}` "
                            f"or `typing.{imported_object_name}` instead of `typing_extensions.{imported_object_name}`"
                        )
                    elif imported_object_name in CONTEXT_MANAGER_ALIASES:
                        if python_2_support_required:
                            errors.append(
                                f"{path}:{node.lineno}: "
                                f"Use `typing.{imported_object_name}` instead of `typing_extensions.{imported_object_name}`"
                            )
                        else:
                            errors.append(
                                f"{path}:{node.lineno}: Use `contextlib.{CONTEXT_MANAGER_ALIASES[imported_object_name]}` "
                                f"instead of `typing_extensions.{imported_object_name}`"
                            )

            elif not python_2_support_required and path not in CONTEXTLIB_ALIAS_ALLOWLIST and node.module == "typing":
                for imported_class in node.names:
                    imported_class_name = imported_class.name
                    if imported_class_name in CONTEXT_MANAGER_ALIASES:
                        add_contextlib_alias_error(node, imported_class_name)

            self.generic_visit(node)

        if not python_2_support_required and path not in CONTEXTLIB_ALIAS_ALLOWLIST:

            def visit_Attribute(self, node: ast.Attribute) -> None:
                if isinstance(node.value, ast.Name) and node.value.id == "typing" and node.attr in CONTEXT_MANAGER_ALIASES:
                    add_contextlib_alias_error(node, node.attr)
                self.generic_visit(node)

        def visit_AnnAssign(self, node: ast.AnnAssign) -> None:
            self.old_syntax_finder().visit(node.annotation)

        def visit_arg(self, node: ast.arg) -> None:
            if node.annotation is not None:
                self.old_syntax_finder().visit(node.annotation)

        def visit_FunctionDef(self, node: ast.FunctionDef) -> None:
            if node.returns is not None:
                self.old_syntax_finder().visit(node.returns)
            self.generic_visit(node)

        def visit_AsyncFunctionDef(self, node: ast.AsyncFunctionDef) -> None:
            if node.returns is not None:
                self.old_syntax_finder().visit(node.returns)
            self.generic_visit(node)

    class IfFinder(ast.NodeVisitor):
        def visit_If(self, node: ast.If) -> None:
            if isinstance(node.test, ast.Compare) and ast.unparse(node.test).startswith("sys.version_info < ") and node.orelse:
                new_syntax = "if " + ast.unparse(node.test).replace("<", ">=", 1)
                errors.append(
                    f"{path}:{node.lineno}: When using if/else with sys.version_info, "
                    f"put the code for new Python versions first, e.g. `{new_syntax}`"
                )
            self.generic_visit(node)

    AnnotationFinder().visit(tree)
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
