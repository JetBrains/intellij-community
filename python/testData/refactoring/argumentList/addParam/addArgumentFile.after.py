from stub import *
import stub


class MyOldClass(SuperClass):
    pass


class MyNewClass(object,SuperClass):
    pass


class MyNewClass_2(object, datetime,SuperClass):
    pass

class NewClass_3(stub.object,SuperClass):
    pass

class NewClass_4(stub.object, stub.datetime,SuperClass):
    pass

new_param = "ogg"

my_function(new_param,some_param="spam")
my_function(new_param)
my_function_2("some_param",new_param)
my_function_3(new_param,some_param="spam",some_another_param=stub.object)