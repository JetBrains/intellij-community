import dataclasses
import os.path
import textwrap
import types
from pathlib import Path

import tensorflow

# Also sys.getsitepackages()
_packaging_root = Path(tensorflow.__file__).parent.parent
# These names are not explicitly exported in tensorflow/__init__.py
_ignored_modules = ['tsl', 'dtensor', 'tools']


@dataclasses.dataclass
class TensorFlowModule:
    name: str
    importable: bool


def guess_module_own_qname(module: types.ModuleType) -> str | None:
    import_path = Path(module.__file__)
    if not import_path.is_relative_to(_packaging_root):
        return None
    import_path = import_path.relative_to(_packaging_root)
    if import_path.name == "__init__.py":
        import_path = import_path.parent
    else:
        import_path = import_path.with_suffix("")
    return str(import_path).strip(os.sep).replace(os.sep, ".")


def collect_tensorflow_submodules() -> list[TensorFlowModule]:
    result = []
    for attr_name in dir(tensorflow):
        if attr_name.startswith('_') or attr_name in _ignored_modules:
            continue
        attr_value = getattr(tensorflow, attr_name)
        if not isinstance(attr_value, types.ModuleType):
            continue
        if not Path(attr_value.__file__).is_relative_to(_packaging_root):
            continue
        try:
            __import__(f"tensorflow.{attr_name}")
        except ModuleNotFoundError:
            importable = False
        else:
            importable = True
        result.append(TensorFlowModule(name=attr_name, importable=importable))
    return result


def generate_attr_resolve_test(submodules: list[TensorFlowModule]) -> str:
    attr_references = [f"print(tf.{m.name}.__name__)" for m in submodules]
    return textwrap.dedent(f"""
    # Generated with inspect_tf_submodules.py for tensorflow {tensorflow.__version__}.
    import tensorflow as tf
    """) + "\n".join(attr_references)


def generate_import_resolve_test(submodules: list[TensorFlowModule]) -> str:
    module_imports = []
    for m in submodules:
        if m.importable:
            module_import = textwrap.dedent(f"""\
            import tensorflow.{m.name}
            print(tensorflow.{m.name}.__name__)\
            """)
        else:
            module_import = textwrap.dedent(f"""\
            try:
                import tensorflow.{m.name}
                print(tensorflow.{m.name}.__name__)
                assert False
            except ModuleNotFoundError:
                pass\
            """)
        module_imports.append(module_import)

    return textwrap.dedent(f"""
    # Generated with inspect_tf_submodules.py for tensorflow {tensorflow.__version__}.
    """) + "\n".join(module_imports)


def main():
    submodules = collect_tensorflow_submodules()
    submodules.sort(key=lambda x: x.name)
    print(generate_attr_resolve_test(submodules))
    print(generate_import_resolve_test(submodules))


if __name__ == '__main__':
    main()
