class C:
    x<warning descr="Python version 2.7 does not support variable annotations">: int</warning>
    y<warning descr="Python version 2.7 does not support variable annotations">: None</warning> = 42

    def m(self, d):
        x<warning descr="Python version 2.7 does not support variable annotations">: List[bool]</warning>
        d['foo']<warning descr="Python version 2.7 does not support variable annotations">: str</warning>
        (d['bar'])<warning descr="Python version 2.7 does not support variable annotations">: float</warning>
