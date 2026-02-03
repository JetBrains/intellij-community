# bg is always black.
# effect is white
# func decl: red bold
# class decl: blue bold
# predefined decl: green bold
def <info descr="PY.FUNC_DEFINITION" type="INFORMATION" foreground="0xff0000" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">foo</info>():
  pass

class <info descr="PY.CLASS_DEFINITION" type="INFORMATION" foreground="0x0000ff" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">Moo</info>:
  def <info descr="PY.PREDEFINED_DEFINITION" type="INFORMATION" foreground="0x00ff00" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">__init__</info>(<info descr="PY.SELF_PARAMETER">self</info>):
    pass
  
  def <info descr="PY.FUNC_DEFINITION" type="INFORMATION" foreground="0xff0000" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">doodle</info>(<info descr="PY.SELF_PARAMETER">self</info>):
    pass

  def <info descr="PY.FUNC_DEFINITION" type="INFORMATION" foreground="0xff0000" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">__made_up__</info>(<info descr="PY.SELF_PARAMETER">self</info>):
    return <info descr="PY.BUILTIN_NAME" type="INFORMATION">None</info>
