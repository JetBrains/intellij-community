class C:
    x<warning descr="Python versions 2.7, 3.5 do not support variable annotations">: int</warning>
    y<warning descr="Python versions 2.7, 3.5 do not support variable annotations">: None</warning> = 42

    def m(self, d):
        x<warning descr="Python versions 2.7, 3.5 do not support variable annotations">: List[bool]</warning>
        d['foo']<warning descr="Python versions 2.7, 3.5 do not support variable annotations">: str</warning>
        (d['bar'])<warning descr="Python versions 2.7, 3.5 do not support variable annotations">: float</warning>
