from stub import *
import stub


class MyOldClass():
    pass


class MyNewClass(object):
    pass


class MyNewClass_2(object, datetime):
    pass

class NewClass_3(stub.object):
    pass

class NewClass_4(stub.object, stub.datetime):
    pass

class NewClass_5(stub.datetime, foo=stub.object):
    pass


spam = "new_param"

my_function()
my_function_1("some_param")
my_function_2(named_param="ham")
my_function_3("some_param", "some_param_2", named_param="ham")
my_function_4("some_param", "some_param_2", named_param=stub.object, named_param_2="eggs")