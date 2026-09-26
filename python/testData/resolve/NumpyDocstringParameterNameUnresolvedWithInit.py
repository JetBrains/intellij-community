from dataclasses import dataclass

@dataclass
class MyClass:
    """Class description

    Parameters
    ----------
    attr1:
    <ref>
        attr1 description
    """
    attr1 = 1

    def __init__(self):
        pass