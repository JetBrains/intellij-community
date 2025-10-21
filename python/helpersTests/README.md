# helpersTests

- tests for PyCharm helpers
- to be able to run tests—install [uv][install-uv], e.g., ...

```sh
curl -LsSf https://astral.sh/uv/install.sh | sh
```

- ... and `tox` with `tox-uv` plugin ...

```sh
uv tool install tox --with tox-uv
```

- to run tests ...

```sh
tox
```

- the tests are executed on all supported Python versions using `uv.lock` for reproducible builds
- plus `latest` environment—with latest available dependencies (outside the aggregator)
- no manual Python installation is required
- to update the list of dependencies / supported Python versions—edit `pyproject.toml`
- to update the lock file ...

```sh
uv lock
```

[install-uv]: https://docs.astral.sh/uv/getting-started/installation/
