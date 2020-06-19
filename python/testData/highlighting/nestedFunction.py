# func declarations are red
def <info descr="PY.FUNC_DEFINITION" foreground="0xff0000" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">foo</info>():
    def <info descr="PY.NESTED_FUNC_DEFINITION" foreground="0x00ff00" background="0x0000ff" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">nested</info>():
        return 42
    return <info descr="PY.BUILTIN_NAME">False</info>


def <info descr="PY.FUNC_DEFINITION" foreground="0xff0000" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">bar</info>():
    class <info descr="PY.CLASS_DEFINITION" type="INFORMATION" foreground="0x0000ff" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">Clzz</info>:
        def <info descr="PY.FUNC_DEFINITION" foreground="0xff0000" background="0x000000" effectcolor="0xffffff" effecttype="BOXED" fonttype="1">baz</info>():
            return 42
    return <info descr="PY.BUILTIN_NAME">False</info>
