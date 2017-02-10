class C:
    x<error descr="Python version 3.5 does not support variable annotations">: int</error>
    y<error descr="Python version 3.5 does not support variable annotations">: None</error> = 42

    def m(self, d):
        x<error descr="Python version 3.5 does not support variable annotations">: List[bool]</error>
        d['foo']<error descr="Python version 3.5 does not support variable annotations">: str</error>
        (d['bar'])<error descr="Python version 3.5 does not support variable annotations">: float</error>
