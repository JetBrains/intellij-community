# poetry-pkg

Exists so that `[tool.poetry] include = ["docs"]` points at a real directory: the test asserts that
this key stays unreferenced because of its position, not because the target is missing.
