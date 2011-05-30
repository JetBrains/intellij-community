def foo():
  <warning descr="Triple double-quoted strings should be used for docstrings."><caret>'''</warning>foo first line docstring
  second line of docstring<warning descr="Triple double-quoted strings should be used for docstrings.">'''</warning>
  pass