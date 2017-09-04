# bg is always black.
# effect is white
# func decl: red bold
# class decl: blue bold
# predefined decl: green bold
# predefined usage: yellow bold

<info descr="null" type="INFORMATION" foreground="0x00ff00" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1"><info>len</info></info>("")
len = [] # redefine
len # no highlight

class <info descr="null" type="INFORMATION">A</info>(<info descr="null" type="INFORMATION" foreground="0x00ff00" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">object</info>):
  <info descr="null" type="INFORMATION" foreground="0xffff00" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">__metaclass__</info> = M # assignment target

  <info descr="null" type="INFORMATION">@</info><info descr="null" type="INFORMATION">classmethod</info>
  def <info descr="null" type="INFORMATION">foo</info>(<info descr="null">cls</info>):
    pass

try:
  1/0
except <info descr="null" type="INFORMATION" foreground="0x00ff00" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">ArithmeticError</info>:
  pass
