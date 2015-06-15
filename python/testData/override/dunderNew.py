class BaseMeta(type):
    def __new__(cls, name, bases, namespace):
        return super(BaseMeta, cls).__new__(cls, name, bases, namespace)


class MyMeta(BaseMeta):
    <caret>pass