<warning descr="Triple double-quoted strings should be used for docstrings.">'''</warning>package docstring<warning descr="Triple double-quoted strings should be used for docstrings.">'''</warning>

def foo():
  <warning descr="Triple double-quoted strings should be used for docstrings.">"</warning>foo docstring<warning descr="Triple double-quoted strings should be used for docstrings.">"</warning>
  pass

class Klass:
  <warning descr="Triple double-quoted strings should be used for docstrings.">'</warning>class docstring\
  second line<warning descr="Triple double-quoted strings should be used for docstrings.">'</warning>
  pass

def bar():
  """ bar docstring """
  pass

a = '''some string'''
'''another string'''