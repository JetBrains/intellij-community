# Contributing to typeshed

Welcome! typeshed is a community project that aims to work for a wide
range of Python users and Python codebases. If you're trying a type
checker on your Python code, your experience and what you can contribute
are important to the project's success.

## The contribution process at a glance

1. [Prepare your environment](#preparing-the-environment).
2. Find out [where to make your changes](#where-to-make-changes).
3. [Making your changes](#making-changes):
    * Small fixes and additions can be submitted directly as pull requests,
      but [contact us](README.md#discussion) before starting significant work.
    * Create your stubs, considering [what to include](#what-to-include) and
      conforming to the [coding style](https://typing.readthedocs.io/en/latest/guides/writing_stubs.html#style-guide).
4. Optionally [format and check your stubs](#code-formatting).
5. Optionally [run the tests](tests/README.md).
6. [Submit your changes](#submitting-changes) by opening a pull request.
7. Make sure that all tests in CI are passing.

You can expect a reply within a few days, but please be patient when
it takes a bit longer. For more details, read below.

## Preparing the environment

### Code away!

Typeshed runs continuous integration (CI) on all pull requests. This means that
if you file a pull request (PR), our full test suite
-- including our linter, [`flake8-pyi`](https://github.com/pycqa/flake8-pyi) --
is run on your PR. It also means that bots will automatically apply
changes to your PR (using [Black](https://github.com/psf/black) and
[Ruff](https://github.com/astral-sh/ruff)) to fix any formatting issues.
This frees you up to ignore all local setup on your side, focus on the
code and rely on the CI to fix everything, or point you to the places that
need fixing.

### ... Or create a local development environment

If you prefer to run the tests and formatting locally, it's
possible too. Follow platform-specific instructions below.
For more information about our available tests, see
[tests/README.md](tests/README.md).

Whichever platform you're using, you will need a
virtual environment. If you're not familiar with what it is and how it works,
please refer to this
[documentation](https://packaging.python.org/guides/installing-using-pip-and-virtual-environments/).

Note that some tests require extra setup steps to install the required dependencies.

<table>
<tr>
  <td>Linux / macOS / WSL</td>
  <td>

  On Linux and macOS, you will be able to run the full test suite on Python
  3.9-3.12.
  To install the necessary requirements, run the following commands from a
  terminal window:

  ```bash
  $ python3 -m venv .venv
  $ source .venv/bin/activate
  (.venv)$ pip install -U pip
  (.venv)$ pip install -r requirements-tests.txt
  ```

  </td>
</tr>
<tr><!-- disables zebra striping --></tr>
<tr>
  <td>Windows</td>
  <td>

  Run the following commands from a Windows terminal to install all requirements:

  ```powershell
  > python -m venv .venv
  > .venv\Scripts\activate
  (.venv) > pip install -U pip
  (.venv) > pip install -r requirements-tests.txt
  ```

  To be able to run pytype tests, you'll also need to install it manually
as it's currently excluded from the requirements file:

  ```powershell
  (.venv) > pip install -U pytype
  ```

  </td>
</tr>
<tr><!-- disables zebra striping --></tr>
<tr>
  <td>Using uv</td>
  <td>

  If you already have [uv](https://docs.astral.sh/uv/getting-started/installation/) installed, you can simply replace the commands above with:

  ```shell
  uv venv
  uv pip install -r requirements-tests.txt
  ```

  ```shell
  uv pip install -U pytype
  ```

  </td>
</tr>
</table>

## Where to make changes

### Standard library stubs

The `stdlib` directory contains stubs for modules in the
Python standard library — which
includes pure Python modules, dynamically loaded extension modules,
hard-linked extension modules, and the builtins. The `VERSIONS` file lists
the versions of Python where the module is available.

We accept changes for future versions of Python after the first beta for that
version was released. We drop support for a Python version three months
after it reaches [end-of-life](https://devguide.python.org/versions/). This
means that we will no longer actively test the stubs against that version.
After six months, we will remove the stubs for that version and start
to use syntax and typing features not supported by that version.

### Third-party library stubs

We accept stubs for third-party packages into typeshed as long as:
* the package is publicly available on the [Python Package Index](https://pypi.org/);
* the package supports any Python version supported by typeshed; and
* the package does not ship with its own stubs or type annotations.

The fastest way to generate new stubs is to use `scripts/create_baseline_stubs.py` (see below).

Stubs for third-party packages go into the `stubs` directory. Each subdirectory
there represents a PyPI distribution, and contains the following:
* `METADATA.toml`, describing the package. See below for details.
* Stubs (i.e. `*.pyi` files) for packages and modules that are shipped in the
  source distribution.
* (Rarely) some docs specific to a given type stub package in `README` file.

When a third party stub is added or
modified, an updated version of the corresponding distribution will be
automatically uploaded to PyPI within a few hours.
Each time this happens the least significant
version level is incremented. For example, if `stubs/foo/METADATA.toml` has
`version = "x.y"` the package on PyPI will be updated from `types-foo-x.y.n`
to `types-foo-x.y.n+1`.

*Note:* In its current implementation, typeshed cannot contain stubs for
multiple versions of the same third-party library.  Prefer to generate
stubs for the latest version released on PyPI at the time of your
stubbing.

#### The `METADATA.toml` file

The metadata file describes the stubs package using the
[TOML file format](https://toml.io/en/). Currently, the following keys are
supported:

* `version`: The versions of the library that the stubs support. Two
  formats are supported:
    - A concrete version. This is especially suited for libraries that
      use [Calendar Versioning](https://calver.org/).
    - A version range ending in `.*`. This is suited for libraries that
      reflect API changes in the version number only, where the API-independent
      part is represented by the asterisk. In the case
      of [Semantic Versioning](https://semver.org/), this version could look
      like this: `2.7.*`.
  When the stubs are updated to a newer version
  of the library, the version of the stub should be bumped (note that
  previous versions are still available on PyPI).
* `requires` (optional): A list of other stub packages or packages with type
  information that are imported by the stubs in this package. Only packages
  generated by typeshed or required by the upstream package are allowed to
  be listed here, for security reasons. See
  [this issue](https://github.com/typeshed-internal/stub_uploader/issues/90)
  for more information about what external dependencies are allowed.
* `extra_description` (optional): Can be used to add a custom description to
  the package's long description. It should be a multi-line string in
  Markdown format.
* `stub_distribution` (optional): Distribution name to be uploaded to PyPI.
  This defaults to `types-<distribution>` and should only be set in special
  cases.
* `upstream_repository` (recommended): The URL of the upstream repository.
* `obsolete_since` (optional): This field is part of our process for
  [removing obsolete third-party libraries](#third-party-library-removal-policy).
  It contains the first version of the corresponding library that ships
  its own `py.typed` file.
* `no_longer_updated` (optional): This field is set to `true` before removing
  stubs for other reasons than the upstream library shipping with type
  information.
* `upload` (optional): This field is set to `false` to prevent automatic
  uploads to PyPI. This should only used in special cases, e.g. when the stubs
  break the upload.
* `partial_stub` (optional): This field marks the type stub package as
  [partial](https://peps.python.org/pep-0561/#partial-stub-packages). This is for
  3rd-party stubs that don't cover the entirety of the package's public API.
* `requires_python` (optional): The minimum version of Python required to install
  the type stub package. It must be in the form `>=3.*`. If omitted, the oldest
  Python version supported by typeshed is used.

In addition, we specify configuration for stubtest in the `tool.stubtest` table.
This has the following keys:
* `skip` (default: `false`): Whether stubtest should be run against this
  package. Please avoid setting this to `true`, and add a comment if you have
  to.
* `ignore_missing_stub`: When set to `true`, this will add the
  `--ignore_missing_stub` option to the stubtest call. See
  [tests/README.md](./tests/README.md) for more information. In most cases,
  this field should be identical to `partial_stub`.
* `stubtest_requirements` (default: `[]`): A list of Python packages that need
  to be installed for stubtest to run successfully. These packages are installed
  in addition to the requirements in the `requires` field.
* `apt_dependencies` (default: `[]`): A list of Ubuntu APT packages
  that need to be installed for stubtest to run successfully.
* `brew_dependencies` (default: `[]`): A list of MacOS Homebrew packages
  that need to be installed for stubtest to run successfully
* `choco_dependencies` (default: `[]`): A list of Windows Chocolatey packages
  that need to be installed for stubtest to run successfully
* `platforms` (default: `["linux"]`): A list of OSes on which to run stubtest.
  Can contain `win32`, `linux`, and `darwin` values.
  If not specified, stubtest is run only on `linux`.
  Only add extra OSes to the test
  if there are platform-specific branches in a stubs package.
* `mypy_plugins` (default: `[]`): A list of Python modules to use as mypy plugins
when running stubtest. For example: `mypy_plugins = ["mypy_django_plugin.main"]`
* `mypy_plugins_config` (default: `{}`): A dictionary mapping plugin names to their
configuration dictionaries for use by mypy plugins. For example:
`mypy_plugins_config = {"django-stubs" = {"django_settings_module" = "@tests.django_settings"}}`


`*_dependencies` are usually packages needed to `pip install` the implementation
distribution.

The format of all `METADATA.toml` files can be checked by running
`python3 ./tests/check_typeshed_structure.py`.


## Making Changes

### Before you begin

If your change will be a significant amount of work to write, we highly
recommend starting by opening an issue laying out what you want to do.
That lets a conversation happen early in case other contributors disagree
with what you'd like to do or have ideas that will help you do it.

### Stub Content and Style

Each Python module is represented by a .pyi "stub file". This is a syntactically valid Python file, where all methods are empty and [type annotations](https://typing.readthedocs.io/en/latest/spec/annotations.html) are used to describe function signatures and variable types.

Typeshed follows the standard type system guidelines for [stub content](https://typing.readthedocs.io/en/latest/guides/writing_stubs.html#stub-content) and [coding style](https://typing.readthedocs.io/en/latest/guides/writing_stubs.html#style-guide).

The code is formatted using [`Black`](https://github.com/psf/black).
Various other autofixes and lint rules are
also performed by [`Ruff`](https://github.com/astral-sh/ruff) and
[`Flake8`](https://github.com/pycqa/flake8),
with plugin [`flake8-pyi`](https://github.com/pycqa/flake8-pyi).

The repository is equipped with a [pre-commit.ci](https://pre-commit.ci/)
configuration file. This means that you don't *need* to do anything yourself to
run the code formatters or linters. When you push a commit, a bot will run
those for you right away and add any autofixes to your PR. Anything
that can't be autofixed will show up as a CI failure, hopefully with an error
message that will make it clear what's gone wrong.

That being said, if you *want* to run the formatters and linters locally
when you commit, you're free to do so. To use the same configuration as we use
in CI, we recommend doing this via pre-commit:

```bash
(.venv)$ pre-commit run --all-files
```

### What to include

Stubs should include the complete interface (classes, functions,
constants, etc.) of the module they cover, but it is not always
clear exactly what is part of the interface.

The following should always be included:
- All objects listed in the module's documentation.
- All objects included in ``__all__`` (if present).

Other objects may be included if they are being used in practice
or if they are not prefixed with an underscore. This means
that typeshed will generally accept contributions that add missing
objects, even if they are undocumented. Undocumented objects should
be marked with a comment of the form ``# undocumented``.

### Incomplete Annotations

When submitting new stubs, it is not necessary to annotate all arguments,
return types, and fields. Such items should either be left unannotated or
use `_typeshed.Incomplete` if this is not possible:

```python
from _typeshed import Incomplete

field: Incomplete  # unannotated

def foo(x): ...  # unannotated argument and return type
```

`Incomplete` can also be used for partially known types:

```python
def foo(x: Incomplete | None) -> list[Incomplete]: ...
```

### What to do when a project's documentation and implementation disagree

Type stubs are meant to be external type annotations for a given
library.  While they are useful documentation in its own merit, they
augment the project's concrete implementation, not the project's
documentation.  Whenever you find them disagreeing, model the type
information after the actual implementation and file an issue on the
project's tracker to fix their documentation.

### Docstrings

Typeshed stubs should not include duplicated docstrings from the source code.

### Auto-generating stub files

Typeshed includes `scripts/create_baseline_stubs.py`.
It generates stubs automatically using a tool called
[stubgen](https://mypy.readthedocs.io/en/latest/stubgen.html) that comes with mypy.

To get started, fork typeshed, clone your fork, and then
[create a virtualenv](#-or-create-a-local-development-environment).
You can then install the library with `pip` into the virtualenv and run the script below,
replacing `$INSERT_LIBRARY_NAME_HERE` with the name of the library:

```bash
(.venv)$ pip install $INSERT_LIBRARY_NAME_HERE
(.venv)$ python3 scripts/create_baseline_stubs.py $INSERT_LIBRARY_NAME_HERE
```

When the script has finished running, it will print instructions telling you what to do next.

If it has been a while since you set up the virtualenv, make sure you have
the latest mypy (`pip install -r requirements-tests.txt`) before running the script.

### Supported type system features

Since PEP 484 was accepted, there have been many other PEPs that added
new features to the Python type system. In general, new features can
be used in typeshed as soon as the PEP has been accepted and implemented
and most type checkers support the new feature.

Supported features include:
- [PEP 544](https://peps.python.org/pep-0544/) (Protocol)
- [PEP 585](https://peps.python.org/pep-0585/) (builtin generics)
- [PEP 586](https://peps.python.org/pep-0586/) (Literal)
- [PEP 591](https://peps.python.org/pep-0591/) (Final/@final)
- [PEP 589](https://peps.python.org/pep-0589/) (TypedDict)
- [PEP 604](https://peps.python.org/pep-0604/) (`Foo | Bar` union syntax)
- [PEP 612](https://peps.python.org/pep-0612/) (ParamSpec)
- [PEP 647](https://peps.python.org/pep-0647/) (TypeGuard):
  see [#5406](https://github.com/python/typeshed/issues/5406)
- [PEP 655](https://peps.python.org/pep-0655/) (`Required` and `NotRequired`)
- [PEP 673](https://peps.python.org/pep-0673/) (`Self`)
- [PEP 675](https://peps.python.org/pep-0675/) (`LiteralString`)
- [PEP 702](https://peps.python.org/pep-0702/) (`@deprecated()`)

Features from the `typing` module that are not present in all
supported Python versions must be imported from `typing_extensions`
instead in typeshed stubs. This currently affects:

- `TypeAlias` (new in Python 3.10)
- `Concatenate` (new in Python 3.10)
- `ParamSpec` (new in Python 3.10)
- `TypeGuard` (new in Python 3.10)
- `Self` (new in Python 3.11)
- `Never` (new in Python 3.11)
- `LiteralString` (new in Python 3.11)
- `TypeVarTuple` and `Unpack` (new in Python 3.11)
- `Required` and `NotRequired` (new in Python 3.11)
- `Buffer` (new in Python 3.12; in the `collections.abc` module)
- `@deprecated` (new in Python 3.13; in the `warnings` module)

Some type checkers implicitly promote the `bytearray` and
`memoryview` types to `bytes`.
[PEP 688](https://www.python.org/dev/peps/pep-0688/) removes
this implicit promotion.
Typeshed stubs should be written assuming that these promotions
do not happen, so a parameter that accepts either `bytes` or
`bytearray` should be typed as `bytes | bytearray`.
Often one of the aliases from `_typeshed`, such as
`_typeshed.ReadableBuffer`, can be used instead.

## Submitting Changes

Even more excellent than a good bug report is a fix for a bug, or the
implementation of a much-needed stub. We'd love to have
your contributions.

We use the usual GitHub pull-request flow, which may be familiar to
you if you've contributed to other projects on GitHub.  For the
mechanics, see [Mypy's git and GitHub workflow help page](https://github.com/python/mypy/wiki/Using-Git-And-GitHub),
or [GitHub's own documentation](https://help.github.com/articles/using-pull-requests/).

Anyone interested in type stubs may review your code.  One of the
maintainers will merge your pull request when they think it's ready.
For every pull request, we aim to promptly either merge it or say why
it's not yet ready; if you go a few days without a reply, please feel
free to ping the thread by adding a new comment.

To get your pull request merged sooner, you should explain why you are
making the change. For example, you can point to a code sample that is
processed incorrectly by a type checker. It is also helpful to add
links to online documentation or to the implementation of the code
you are changing.

As the author of the pull request, it is your responsibility to make
sure all CI tests pass and that any feedback is addressed. The typeshed
maintainers will probably provide some help and may even push changes
to your PR to fix any minor issues, but this is not always possible.
If a PR lingers with unresolved problems for too long, we may close it
([see below](#closing-stale-prs)).

Also, do not squash your commits or use `git commit --amend` after you have submitted a pull request, as this
erases context during review. We will squash commits when the pull request is merged.
This way, your pull request will appear as a single commit in our git history, even
if it consisted of several smaller commits.

## Third-party library removal policy

Third-party stubs are generally removed from typeshed when one of the
following criteria is met:

* The upstream package ships a `py.typed` file for at least six months,
  and the upstream type annotations are of a comparable standard to those in
  typeshed, or
* the upstream package was declared or appears to be unmaintained, and
  retaining the stubs causes maintenance issues in typeshed.

If a package ships its own `py.typed` file, please follow these steps:

1. Open an issue with the earliest month of removal in the subject.
2. A maintainer will add the
   ["stubs: removal" label](https://github.com/python/typeshed/labels/%22stubs%3A%20removal%22).
3. Open a PR that sets the `obsolete_since` field in the `METADATA.toml`
   file to the first version of the package that shipped `py.typed`.
4. After at least six months, open a PR to remove the stubs.

If third-party stubs should be removed for other reasons, please follow these
steps:

1. Open an issue explaining why the stubs should be removed.
2. A maintainer will add the
   ["stubs: removal" label](https://github.com/python/typeshed/labels/%22stubs%3A%20removal%22).
3. Open a PR that sets the `no_longer_updated` field in the `METADATA.toml`
   file to `true`.
4. When a new version of the package was automatically uploaded to PyPI
   (which can take up to a day), open a PR to remove the stubs.

If feeling kindly, please update [mypy](https://github.com/python/mypy/blob/master/mypy/stubinfo.py)
for any stub obsoletions or removals.

### Marking PRs as "deferred"

We sometimes use the ["status: deferred" label](https://github.com/python/typeshed/labels/%22status%3A%20deferred%22)
to mark PRs and issues that we'd like to accept, but that are blocked by some
external factor. Blockers can include:

- An unambiguous bug in a type checker (i.e., a case where the
  type checker is not implementing [the typing spec](https://typing.readthedocs.io/en/latest/spec/index.html)).
- A dependency on a typing PEP that is still under consideration.
- A pending change in a related project, such as stub-uploader.

### Closing stale PRs

To keep the number of open PRs manageable, we may close PRs when they have been
open for too long. Specifically, we close open PRs that either have failures in CI,
serious merge conflicts or unaddressed feedback, and that have not seen any
activity in three months.
