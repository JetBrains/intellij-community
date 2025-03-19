import typing

class FakeBase:
  def __init__(self):
      self._some_var: typing.Optional[str] = ""

class Fake(FakeBase):
  def __init__(self):
      super().__init__()
      self._some_var = None

  def some_method(self):
      expr = self._some_var
#                  <ref>
