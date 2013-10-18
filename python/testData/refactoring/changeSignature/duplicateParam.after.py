
def some_function_name(argument_1, opt2=None, **extra_info):
  print locals()


class A(object):
  def someMethod(self):
    self.url = some_function_name(0, extra1=True, extra2=2)