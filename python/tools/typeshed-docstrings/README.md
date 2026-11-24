# typeshed-docstrings

Scripts that take data for the IDE from the runtime of the standard library. The module docstring of each script
tells how to run it and when.

- `typeshed_docstrings.py` adds the runtime docstrings to the bundled typeshed stdlib and indents them with black. The
  typeshed update bot runs it after each typeshed update.
- `list_types_without_string_conversion.py` lists the native stdlib classes without a string conversion for
  `PyStringDunderUtil`. The typeshed update bot runs it after `typeshed_docstrings.py`.
- `python_versions.py` holds the Python versions of both scripts. Add a new Python version there.
