from _typeshed import Incomplete

from google.cloud.ndb import model

class _ClassKeyProperty(model.StringProperty):
    def __init__(self, name="class", indexed: bool = True) -> None: ...

class PolyModel(model.Model):
    class_: Incomplete
