"""
Tests "# type: ignore" comments.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/directives.html#type-ignore-comments

# The following type violation should be suppressed.
x: int = ""  # type: ignore

# The following type violation should be suppressed.
y: int = ""  # type: ignore - additional stuff

# The following type violation may be suppressed, if the type checker
# actually uses the code `an-empty-str-is-not-an-int` (unlikely!), or if
# it treats unknown codes as blanket ignores.
z: int = ""  # type: ignore[an-empty-str-is-not-an-int]  # E?

# > In some cases, linting tools or other comments may be needed on the same
# > line as a type comment. In these cases, the type comment should be before
# > other comments and linting markers.

a: int = ""  # type: ignore # other comment
