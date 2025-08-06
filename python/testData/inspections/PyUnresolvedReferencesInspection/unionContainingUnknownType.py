def foo(smth, param):
    if smth:
        param = ""
    print(param.<weak_warning descr="Member 'str' of 'Union[{smth}, str]' does not have attribute 'smth'">smth</weak_warning>())