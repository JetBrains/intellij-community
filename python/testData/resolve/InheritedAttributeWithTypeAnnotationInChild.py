import typing

class FakeBase:
  def __init__(self):
      _some_var = 1

class Fake(FakeBase):
  def __init__(self):
      super().__init__()
      self._some_var: typing.Optional[str] = None

  def some_method(self):
      expr = self._some_var
#                  <ref>
