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

new_param = "ogg"

my_function(some_param="spam")
my_function()
my_function_2("some_param")
my_function_3(some_param="spam",some_another_param=stub.object)