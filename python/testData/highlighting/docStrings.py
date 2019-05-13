# bg is always black.
# effect is white
# doc comment: blue bold
def <info descr="null" type="INFORMATION">foo</info>():
  <info descr="null" type="INFORMATION" foreground="0x0000ff" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">"Func doc string"</info>
  pass

class <info descr="null" type="INFORMATION">Boo</info>:
  <info descr="null" type="INFORMATION" foreground="0x0000ff" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">"Class doc string"</info>
  pass

class <info descr="null" type="INFORMATION">Moo</info>:
  def <info descr="null" type="INFORMATION">meth</info>(self):
    <info descr="null" type="INFORMATION" foreground="0x0000ff" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">"Meth doc string"</info>
    pass
 
