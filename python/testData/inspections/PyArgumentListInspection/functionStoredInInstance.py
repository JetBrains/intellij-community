def xlistdir(path): return path

class Directory:
    listdir = xlistdir
    #...
    def listing(self):
    #....
    # Warning is expected: `xlistdir` is a regular Python function and is a descriptor,
    # so `self.listdir` becomes a bound method — `self` fills `path`, and the literal
    # "path" is an extra argument.
    #
    # Note: PY-3623 originally used `os.listdir`, a built-in (C) function that is NOT
    # a descriptor; in that case `self.listdir` would not be bound and the call would
    # be valid. Consider covering that case in a separate test.
        self.listdir(<warning descr="Unexpected argument">"path"</warning>)  #<---here
    #...