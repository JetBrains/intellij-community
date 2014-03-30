class Foo(object):
   def __init__(self, color):
        self.color = color

class Bar(object):
   fooFactory = Foo

   def quux(self):
       foo = self.fooFactory(<arg>"orange") #

