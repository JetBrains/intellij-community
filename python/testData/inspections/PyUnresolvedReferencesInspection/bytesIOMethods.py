from io import BytesIO

fd = BytesIO('foo')
fd.read(10)  # Should resolve
fd.<warning descr="Unresolved attribute reference 'foo' for class 'BytesIO'">foo</warning>()
