# click 7.0

`__init__.pyi` is almost a copy of `click/__init__.py`. It's a shortcut module
anyway in the actual sources so it works well with minimal changes.

The types are pretty complete but they were created mostly for public API use
so some internal modules (`_compat`) or functions (`core._bashcomplete`) are
deliberately missing. If you feel the need to add those, pull requests accepted.

Speaking of pull requests, it would be great if the option decorators informed
the type checker on what types the command callback should accept.
