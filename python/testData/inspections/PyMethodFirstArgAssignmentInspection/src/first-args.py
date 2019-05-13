def self():  # ok
  pass

self = 1 # ok

class A:
  def foo(self, a):
    (self, (a, b)) = 1, ((22, 23))
    if 1:
      a = {}
      self = 23
      for (self, a) in []:
        pass

    def boo():
      self = 1

    def moo(self):
      def inner_moo():
        self =1

    def self():
      pass

    class self:
      pass

  @classmethod
  def qoo(cls):
    cls = 1

# no builtins detection -> can't test static methods :( where's mock Python SDK?
  @staticmethod
  def stat(first):
    first = 1 # ok
