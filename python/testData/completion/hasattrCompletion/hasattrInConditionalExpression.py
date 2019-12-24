def foo(x):
  some_var = (x.<caret> if hasattr(x, "foo") else 42) if hasattr(x, "bar") else 42