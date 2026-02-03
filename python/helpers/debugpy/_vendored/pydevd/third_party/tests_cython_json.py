import Cython
from cython_json import source_to_dict
import pytest
import json


def test_dump_ast_error():
    as_dict = source_to_dict("x = [a  10]")
    errors = as_dict["errors"]
    assert len(errors) == 1
    error = errors[0]
    assert error["__node__"] == "CompileError"
    assert error["line"] == 1
    assert error["col"] == 8
    assert "Expected" in error["message_only"]


def test_dump_error():
    contents = """
from distutils import sysconfig
"""
    if isinstance(contents, bytes):
        contents = contents.decode("utf-8")
    source_to_dict(contents)


def test_dump_class():
    contents = """
class A:pass
"""
    if isinstance(contents, bytes):
        contents = contents.decode("utf-8")
    source_to_dict(contents)


def test_comp():
    contents = """
{i: j for i, j in a}
"""
    if isinstance(contents, bytes):
        contents = contents.decode("utf-8")
    source_to_dict(contents)


def test_global():
    contents = """
def method():
  global b
  b = 10
"""
    if isinstance(contents, bytes):
        contents = contents.decode("utf-8")
    source_to_dict(contents)


# def test_dump_custom():
#     with open(r'X:\cython\tests\compile\buildenv.pyx', 'r') as stream:
#         contents = stream.read().decode('utf-8')
#     source_to_dict(contents)


def test_dump_ast():
    data = source_to_dict("x = [a, 10]")
    assert not data["errors"]
    assert data["ast"]["stats"] == [
        {
            "__node__": "SingleAssignment",
            "rhs": {
                "__node__": "List",
                "line": 1,
                "args": [
                    {"__node__": "Name", "line": 1, "col": 5, "name": "a"},
                    {
                        "is_c_literal": "None",
                        "unsigned": "",
                        "value": "10",
                        "constant_result": "10",
                        "__node__": "Int",
                        "line": 1,
                        "type": "long",
                        "col": 8,
                        "longness": "",
                    },
                ],
                "col": 4,
            },
            "lhs": {"__node__": "Name", "line": 1, "col": 0, "name": "x"},
            "line": 1,
            "col": 4,
        }
    ]


if __name__ == "__main__":
    pytest.main()
