from dataclasses import dataclass

@dataclass
class MyClass:
    """
    Class description

    Parameters:
        at<caret>tr1: attribute description

    """
    attr1 = 3

    def __init__(self, attr1):
        pass