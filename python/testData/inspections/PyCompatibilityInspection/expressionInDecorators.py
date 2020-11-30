<warning descr="Python versions 2.7, 3.5, 3.6, 3.7, 3.8 do not support arbitrary expressions as a decorator">@x[0][1]</warning>
@my_decorator
def say_whee():
    print("Whee!")

<warning descr="Python versions 2.7, 3.5, 3.6, 3.7, 3.8 do not support arbitrary expressions as a decorator">@foo[0].wrapper</warning>
@foo.bar()
def say_whee():
    print("Whee!")