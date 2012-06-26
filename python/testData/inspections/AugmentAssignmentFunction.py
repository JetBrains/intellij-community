def retStr():
  return ''

s1 = ''
<weak_warning descr="Assignment can be replaced with augmented assignment">s1 = s1 <caret>+ retStr()</weak_warning>