import ast
from typing_extensions import assert_type

# Test with source code strings
assert_type(ast.parse("x = 1"), ast.Module)
assert_type(ast.parse("x = 1", mode="exec"), ast.Module)
assert_type(ast.parse("1 + 1", mode="eval"), ast.Expression)
assert_type(ast.parse("x = 1", mode="single"), ast.Interactive)
assert_type(ast.parse("(int, str) -> None", mode="func_type"), ast.FunctionType)

# Test with mod objects - Module
mod1: ast.Module = ast.Module([], [])
assert_type(ast.parse(mod1), ast.Module)
assert_type(ast.parse(mod1, mode="exec"), ast.Module)
mod2: ast.Module = ast.Module(body=[ast.Expr(value=ast.Constant(value=42))], type_ignores=[])
assert_type(ast.parse(mod2), ast.Module)

# Test with mod objects - Expression
expr1: ast.Expression = ast.Expression(body=ast.Constant(value=42))
assert_type(ast.parse(expr1, mode="eval"), ast.Expression)

# Test with mod objects - Interactive
inter1: ast.Interactive = ast.Interactive(body=[])
assert_type(ast.parse(inter1, mode="single"), ast.Interactive)

# Test with mod objects - FunctionType
func1: ast.FunctionType = ast.FunctionType(argtypes=[], returns=ast.Constant(value=None))
assert_type(ast.parse(func1, mode="func_type"), ast.FunctionType)

# Test that any AST node can be passed and returns the same type
binop: ast.BinOp = ast.BinOp(left=ast.Constant(1), op=ast.Add(), right=ast.Constant(2))
assert_type(ast.parse(binop), ast.BinOp)

constant: ast.Constant = ast.Constant(value=42)
assert_type(ast.parse(constant), ast.Constant)

expr_stmt: ast.Expr = ast.Expr(value=ast.Constant(value=42))
assert_type(ast.parse(expr_stmt), ast.Expr)

# Test with additional parameters
assert_type(ast.parse(mod1, filename="test.py"), ast.Module)
assert_type(ast.parse(mod1, type_comments=True), ast.Module)
assert_type(ast.parse(mod1, feature_version=(3, 10)), ast.Module)
assert_type(ast.parse(binop, filename="test.py"), ast.BinOp)
