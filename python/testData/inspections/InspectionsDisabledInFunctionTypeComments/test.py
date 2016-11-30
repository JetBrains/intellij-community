<weak_warning descr="Missing docstring"></weak_warning><warning descr="No encoding specified for file"></warning>def <weak_warning descr="Missing docstring">f</weak_warning>(x):
    <warning descr="Byte literal contains characters > 255"><warning descr="Non-ASCII character М in file, but no encoding declared"># type: (b'Моноцикл') -> None</warning></warning>
    pass


def <weak_warning descr="Missing docstring">g</weak_warning>():
    # type: <error descr="unexpected tokens">"foo"</error>
    pass
