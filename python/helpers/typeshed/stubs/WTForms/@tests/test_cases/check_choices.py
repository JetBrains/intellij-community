from __future__ import annotations

from wtforms import SelectField

# any way we can specify the choices inline with a literal should work

# tuple of tuples
SelectField(choices=(("", ""),))
SelectField(choices=((1, "1"),))
SelectField(choices=(("", "", {}),))
SelectField(choices=((True, "t", {}),))
SelectField(choices=((True, "t"), (False, "f", {})))

# list of tuples
SelectField(choices=[("", "")])
SelectField(choices=[(1, "1")])
SelectField(choices=[("", "", {})])
SelectField(choices=[(True, "t", {})])
SelectField(choices=[(True, "t"), (False, "f", {})])

# dict of tuple of tuples
SelectField(choices={"a": (("", ""),)})
SelectField(choices={"a": ((1, "1"),)})
SelectField(choices={"a": (("", "", {}),)})
SelectField(choices={"a": ((True, "t", {}),)})
SelectField(choices={"a": ((True, "t"), (False, "f", {}))})
SelectField(choices={"a": ((True, "", {}),), "b": ((False, "f"),)})

# dict of list of tuples
SelectField(choices={"a": [("", "")]})
SelectField(choices={"a": [(1, "1")]})
SelectField(choices={"a": [("", "", {})]})
SelectField(choices={"a": [(True, "t", {})]})
SelectField(choices={"a": [(True, "t"), (False, "f", {})]})
SelectField(choices={"a": [(True, "", {})], "b": [(False, "f")]})

# the same should be true for lambdas

# tuple of tuples
SelectField(choices=lambda: (("", ""),))
SelectField(choices=lambda: ((1, "1"),))
SelectField(choices=lambda: (("", "", {}),))
SelectField(choices=lambda: ((True, "t", {}),))
SelectField(choices=lambda: ((True, "t"), (False, "f", {})))

# list of tuples
SelectField(choices=lambda: [("", "")])
SelectField(choices=lambda: [(1, "1")])
SelectField(choices=lambda: [("", "", {})])
SelectField(choices=lambda: [(True, "t", {})])
SelectField(choices=lambda: [(True, "t"), (False, "f", {})])

# dict of tuple of tuples
SelectField(choices=lambda: {"a": (("", ""),)})
SelectField(choices=lambda: {"a": ((1, "1"),)})
SelectField(choices=lambda: {"a": (("", "", {}),)})
SelectField(choices=lambda: {"a": ((True, "t", {}),)})
SelectField(choices=lambda: {"a": ((True, "t"), (False, "f", {}))})
SelectField(choices=lambda: {"a": ((True, "", {}),), "b": ((False, "f"),)})

# dict of list of tuples
SelectField(choices=lambda: {"a": [("", "")]})
SelectField(choices=lambda: {"a": [(1, "1")]})
SelectField(choices=lambda: {"a": [("", "", {})]})
SelectField(choices=lambda: {"a": [(True, "t", {})]})
SelectField(choices=lambda: {"a": [(True, "t"), (False, "f", {})]})
SelectField(choices=lambda: {"a": [(True, "", {})], "b": [(False, "f")]})
