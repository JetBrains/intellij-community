# helpersTests

- tests for PyCharm helpers
- to be able to run tests—install [uv][install-uv], e.g., ...

```sh
curl -LsSf https://astral.sh/uv/install.sh | sh
```

- to run all tests ...

```sh
./run_tests.sh
```

- to run a specific module, e.g., ...

```sh
uv run --python 3.14 __main__.py tests/pycharm_tests/test_jb_unittest_runner.py
```

- the tests are executed on all supported Python versions using `uv.lock` for reproducible builds
- no manual Python installation is required
- to update the list of dependencies / supported Python versions - edit `pyproject.toml`
- to update the lock file ...

```sh
uv lock
```

[install-uv]: https://docs.astral.sh/uv/getting-started/installation/
