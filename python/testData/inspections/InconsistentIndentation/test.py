def foo():
<warning descr="Inconsistent indentation: mix of tabs and spaces">	     </warning>print "foo"

def bar():
	print "foo"
<warning descr="Inconsistent indentation: previous line used tabs, this line uses spaces">        </warning>print "bar"

"""
    foo
	bar
"""

print foo(
	bar,
    baz
)
