<weak_warning descr="Triple double-quoted strings should be used for docstrings.">'''</weak_warning>package docstring<weak_warning descr="Triple double-quoted strings should be used for docstrings.">'''</weak_warning>

def foo():
  <weak_warning descr="Triple double-quoted strings should be used for docstrings.">"</weak_warning>foo docstring<weak_warning descr="Triple double-quoted strings should be used for docstrings.">"</weak_warning>
  pass

class Klass:
  <weak_warning descr="Triple double-quoted strings should be used for docstrings.">'</weak_warning>class docstring\
  second line<weak_warning descr="Triple double-quoted strings should be used for docstrings.">'</weak_warning>
  pass

def bar():
  """ bar docstring """
  pass

a = '''some string'''
'''another string'''