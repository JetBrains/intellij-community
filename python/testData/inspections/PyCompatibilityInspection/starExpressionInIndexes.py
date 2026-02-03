
# Supported in Python 3.11
a[1, <warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.7, 3.8, 3.9, 3.10 do not support starred expressions in subscriptions">*x</warning></warning>, 3]
a[1, <warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.7, 3.8, 3.9, 3.10 do not support starred expressions in subscriptions">*x</warning></warning>]
a[1, <warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.7, 3.8, 3.9, 3.10 do not support starred expressions in subscriptions">*x</warning></warning>, <warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.7, 3.8, 3.9, 3.10 do not support starred expressions in subscriptions">*x</warning></warning>]
a[1:2, <warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets"><warning descr="Python versions 3.7, 3.8, 3.9, 3.10 do not support starred expressions in subscriptions">*x</warning></warning>]

# Not supported
a[(<error descr="Can't use starred expression here">*x</error>)]
a[1:<error descr="Can't use starred expression here">*x</error>]
a[<error descr="Can't use starred expression here">*x</error>:<error descr="Can't use starred expression here">*x</error>]

# Ok in Python 3
a[(<warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets">*x</warning>,)]
[1, <warning descr="Python version 2.7 does not support starred expressions in tuples, lists, and sets">*x</warning>, 4]
