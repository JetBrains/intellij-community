from htmlLib import <warning descr="Calling format_html() without passing args or kwargs is deprecated.">format_html</warning>

# deprecated overload (no args/kwargs).
<warning descr="Calling format_html() without passing args or kwargs is deprecated.">format_html</warning>("{}")

# non-deprecated overload via *args / **kwargs.
format_html("{}", 1)
format_html("{}", 1, 2)
format_html("{}", name="x")
format_html("{}", 1, name="x")