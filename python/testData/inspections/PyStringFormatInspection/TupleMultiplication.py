argument_pattern = re.compile(r'(%s)\s*(\(\s*(%s)\s*\)\s*)?$'
                              % ((states.Inliner.simplename,) * 2))

t, num = ('foo',), 2
res = '%d %d' % (<warning descr="Unexpected type str"><warning descr="Unexpected type str">t * num</warning></warning>)
