from stub import *
import stub


class MyOldClass(metaclass=ABCMeta):
    pass


class MyNewClass(object,metaclass=ABCMeta):
    pass


class MyNewClass_2(object, datetime,metaclass=ABCMeta):
    pass

class NewClass_3(stub.object,metaclass=ABCMeta):
    pass

class NewClass_4(stub.object, stub.datetime,metaclass=ABCMeta):
    pass

class NewClass_5(stub.datetime, foo=stub.object,metaclass=ABCMeta):
    pass


spam = "new_param"

my_function(new_param=spam)
my_function_1("some_param",new_param=spam)
my_function_2(named_param="ham",new_param=spam)
my_function_3("some_param", "some_param_2", named_param="ham",new_param=spam)
my_function_4("some_param", "some_param_2", named_param=stub.object, named_param_2="eggs",new_param=spam)