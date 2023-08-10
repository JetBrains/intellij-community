match 42:
    case {'foo': 1}:
        pass
    case {<error descr="Key pattern can only be a value pattern or a literal pattern">foo</error>: 1}:
        pass
    case {<error descr="Key pattern can only be a value pattern or a literal pattern">[foo]</error>: 1}:
        pass
    case {foo.bar: 1}:
        pass
    case {<error descr="Key pattern can only be a value pattern or a literal pattern">str()</error>: 1}:
        pass
