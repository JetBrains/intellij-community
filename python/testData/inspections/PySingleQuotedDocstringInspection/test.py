<warning descr="Triple double-quoted strings should be used for docstrings.">'''package docstring'''</warning>

def foo():
  <warning descr="Triple double-quoted strings should be used for docstrings.">"foo docstring"</warning>
  pass

class Klass:
  <warning descr="Triple double-quoted strings should be used for docstrings.">'class docstring\
  second line'</warning>
  pass

def bar():
  """ bar docstring """
  pass

a = '''some string'''
'''another string'''