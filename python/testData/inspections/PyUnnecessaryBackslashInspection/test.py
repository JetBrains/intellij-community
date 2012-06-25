
def foo(year, month, day, hour, minute, second):
  if 1900 < year < 2100 and 1 <= month <= 12 \
   and 1 <= day <= 31 and 0 <= hour < 24 \
   and 0 <= minute < 60 and 0 <= second < 60:   # Looks like a valid date
      return 1


if (True<warning descr="Unnecessary backslash in expression."> \</warning>
    or True<warning descr="Unnecessary backslash in expression."> \</warning>
    or False):
  print("false")

var1 = (1,1,<warning descr="Unnecessary backslash in expression.">\</warning>
     2,<warning descr="Unnecessary backslash in expression.">\</warning>
     3,
     4)

var2 = [1,2,<warning descr="Unnecessary backslash in expression.">\</warning>
     3,<warning descr="Unnecessary backslash in expression.">\</warning>
     4]

var3 = {1, 2,<warning descr="Unnecessary backslash in expression.">\</warning>
     3,4}

var4 = {1:1, 2:2,<warning descr="Unnecessary backslash in expression.">\</warning>
    3:3,
    4:4}


assert (val>4,<warning descr="Unnecessary backslash in expression."> \</warning>
    "val is too small")

var5 = (val1 < 20) and \
    (val2 < 30) and \
    (val3 < 40)

var6 = ('1' + '2' + '3' +<warning descr="Unnecessary backslash in expression."> \</warning>
    '4' + '5')


def foo(a, b,<warning descr="Unnecessary backslash in expression."> \</warning>
        c):
  pass

foo(1, 2,<warning descr="Unnecessary backslash in expression."> \</warning>
    3)

# PY-3036
v = [ "some"<warning descr="Unnecessary backslash in expression."> \</warning>
"long string" ]

a = func('some '<warning descr="Unnecessary backslash in expression."> \</warning>
         'string')


#PY-6589
a = (<warning descr="Unnecessary backslash in expression.">  \</warning>
(1,<warning descr="Unnecessary backslash in expression."> \</warning>
2),2)