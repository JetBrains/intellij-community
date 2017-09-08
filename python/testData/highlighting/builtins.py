# bg is always black.
# effect is white
# func decl: red bold
# class decl: blue bold
# predefined decl: green bold
# predefined usage: yellow bold

<info descr="PY.BUILTIN_NAME" type="INFORMATION" foreground="0x00ff00" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">len</info>("")
len = [] # redefine
len # no highlight

class <info descr="PY.CLASS_DEFINITION" type="INFORMATION">A</info>(<info descr="PY.BUILTIN_NAME" type="INFORMATION" foreground="0x00ff00" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">object</info>):
  <info descr="PY.PREDEFINED_USAGE" type="INFORMATION" foreground="0xffff00" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">__metaclass__</info> = M # assignment target

  <info descr="PY.BUILTIN_NAME"><info descr="PY.DECORATOR">@</info></info><info descr="PY.DECORATOR">classmethod</info>
  def <info descr="PY.FUNC_DEFINITION">foo</info>(<info descr="PY.SELF_PARAMETER">cls</info>):
    pass

try:
  1/0
except <info descr="PY.BUILTIN_NAME" type="INFORMATION" foreground="0x00ff00" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">ArithmeticError</info>:
  pass
