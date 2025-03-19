# Contributing to typeshed

Welcome! typeshed is a community project that aims to work for a wide
range of Python users and Python codebases. If you're trying a type
checker on your Python code, your experience and what you can contribute
are important to the project's success.


## The contribution process at a glance

1. [Prepare your environment](#preparing-the-environment).
2. Find out [where to make your changes](#where-to-make-changes).
3. [Prepare your changes](#preparing-changes):
    * Small fixes and additions can be submitted directly as pull requests,
      but [contact us](README.md#discussion) before starting significant work.
    * Create your stubs, considering [what to include](#what-to-include) and
      conforming to the [coding style](#stub-file-coding-style).
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
-- including our linter, [Flake8](https://github.com/PyCQA/flake8) --
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

### Linux/Mac OS

On Linux and Mac OS, you will be able to run the full test suite on Python
3.9, 3.10, or 3.11.
To install the necessary requirements, run the following commands from a
terminal window:

```bash
$ python3 -m venv .venv
$ source .venv/bin/activate
(.venv)$ pip install -U pip
(.venv)$ pip install -r requirements-tests.txt
```

### Windows

If you are using a Windows operating system, you will not be able to run the pytype
tests, as pytype
[does not currently support running on Windows](https://github.com/google/pytype#requirements).
One option is to install
[Windows Subsystem for Linux](https://docs.microsoft.com/en-us/windows/wsl/faq),
which will allow you to run the full suite of tests. If you choose to install
WSL, follow the Linux/Mac OS instructions above.

If you do not wish to install WSL, run the following commands from a Windows
terminal to install all non-pytype requirements:

```powershell
> python -m venv .venv
> .venv\scripts\activate
(.venv) > pip install -U pip
(.venv) > pip install -r "requirements-tests.txt"
```

## Code formatting

The code is formatted using [`Black`](https://github.com/psf/black).
Various other autofixes and lint rules are
also performed by [`Ruff`](https://github.com/astral-sh/ruff) and
[`Flake8`](https://github.com/pycqa/flake8),
with plugins [`flake8-pyi`](https://github.com/pycqa/flake8-pyi),
and [`flake8-noqa`](https://github.com/plinss/flake8-noqa).

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

## Where to make changes

### Standard library stubs

The `stdlib` directory contains stubs for modules in the
Python standard library — which
includes pure Python modules, dynamically loaded extension modules,
hard-linked extension modules, and the builtins. The `VERSIONS` file lists
the versions of Python where the module is available.

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

`*_dependencies` are usually packages needed to `pip install` the implementation
distribution.

The format of all `METADATA.toml` files can be checked by running
`python3 ./tests/check_typeshed_structure.py`.


## Preparing Changes

### Before you begin

If your change will be a significant amount of work to write, we highly
recommend starting by opening an issue laying out what you want to do.
That lets a conversation happen early in case other contributors disagree
with what you'd like to do or have ideas that will help you do it.

### Format

Each Python module is represented by a `.pyi` "stub file".  This is a
syntactically valid Python file, although it usually cannot be run by
Python (since forward references don't require string quotes).  All
the methods are empty.

Python function annotations ([PEP 3107](https://www.python.org/dev/peps/pep-3107/))
are used to describe the signature of each function or method.

See [PEP 484](http://www.python.org/dev/peps/pep-0484/) for the exact
syntax of the stub files and [below](#stub-file-coding-style) for the
coding style used in typeshed.

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
Example:

```python
def list2cmdline(seq: Sequence[str]) -> str: ...  # undocumented
```

We accept such undocumented objects because omitting objects can confuse
users. Users who see an error like "module X has no attribute Y" will
not know whether the error appeared because their code had a bug or
because the stub is wrong. Although it may also be helpful for a type
checker to point out usage of private objects, we usually prefer false
negatives (no errors for wrong code) over false positives (type errors
for correct code). In addition, even for private objects a type checker
can be helpful in pointing out that an incorrect type was used.

### What to do when a project's documentation and implementation disagree

Type stubs are meant to be external type annotations for a given
library.  While they are useful documentation in its own merit, they
augment the project's concrete implementation, not the project's
documentation.  Whenever you find them disagreeing, model the type
information after the actual implementation and file an issue on the
project's tracker to fix their documentation.

### Stub versioning

You can use checks
like `if sys.version_info >= (3, 12):` to denote new functionality introduced
in a given Python version or solve type differences.  When doing so, only use
two-tuples. Because of this, if a given functionality was
introduced in, say, Python 3.11.4, your check:

* should be expressed as `if sys.version_info >= (3, 11):`
* should NOT be expressed as `if sys.version_info >= (3, 11, 4):`
* should NOT be expressed as `if sys.version_info >= (3, 12):`

When your stub contains if statements for different Python versions,
always put the code for the most recent Python version first.

## Stub file coding style

### Syntax example

The below is an excerpt from the types for the `datetime` module.

```python
MAXYEAR: int
MINYEAR: int

class date:
    def __new__(cls, year: SupportsIndex, month: SupportsIndex, day: SupportsIndex) -> Self: ...
    @classmethod
    def fromtimestamp(cls, timestamp: float, /) -> Self: ...
    @classmethod
    def today(cls) -> Self: ...
    @classmethod
    def fromordinal(cls, n: int, /) -> Self: ...
    @property
    def year(self) -> int: ...
    def replace(self, year: SupportsIndex = ..., month: SupportsIndex = ..., day: SupportsIndex = ...) -> Self: ...
    def ctime(self) -> str: ...
    def weekday(self) -> int: ...
```

### Conventions

Stub files are *like* Python files and you should generally expect them
to look the same.  Your tools should be able to successfully treat them
as regular Python files.  However, there are a few important differences
you should know about.

Style conventions for stub files are different from PEP 8. The general
rule is that they should be as concise as possible.  Specifically:
* all function bodies should be empty;
* prefer ``...`` over ``pass``;
* prefer ``...`` on the same line as the class/function signature;
* avoid vertical whitespace between consecutive module-level functions,
  names, or methods and fields within a single class;
* use a single blank line between top-level class definitions, or none
  if the classes are very small;
* do not use docstrings;
* use variable annotations instead of type comments, even for stubs
  that target older versions of Python.

The primary users for stub files are type checkers,
so stub files should generally only contain information necessary for the type
checker, and leave out unnecessary detail.
However, stubs also have other use cases:
* stub files are often used as a data source for IDEs,
  which will often use the signature in a stub to provide information
  on functions or classes in tooltip messages.
* stubs can serve as useful documentation to human readers,
  as well as machine-readable sources of data.

As such, we recommend that default values be retained for "simple" default values
(e.g. bools, ints, bytes, strings, and floats are all permitted).
Use `= ...` for more complex default values,
rather than trying to exactly reproduce the default at runtime.

Some further tips for good type hints:
* for arguments that default to `None`, use `Foo | None` explicitly for the type annotation;
* use `float` instead of `int | float` for parameter annotations
  (see [PEP 484](https://peps.python.org/pep-0484/#the-numeric-tower) for motivation).
* use built-in generics (`list`, `dict`, `tuple`, `set`), instead
  of importing them from `typing`.
* use `X | Y` instead of `Union[X, Y]` and `X | None`, instead of
  `Optional[X]`;
* import collections (`Mapping`, `Iterable`, etc.)
  from `collections.abc` instead of `typing`;
* avoid invariant collection types (`list`, `dict`) for function
  parameters, in favor of covariant types like `Mapping` or `Sequence`;
* avoid union return types: https://github.com/python/mypy/issues/1693;
* use platform checks like `if sys.platform == 'win32'` to denote
  platform-dependent APIs;
* use mypy error codes for mypy-specific `# type: ignore` annotations,
  e.g. `# type: ignore[override]` for Liskov Substitution Principle violations.
* use pyright error codes for pyright-specific suppressions,
  e.g. `# pyright: ignore[reportGeneralTypeIssues]`.
  - pyright is configured to discard `# type: ignore` annotations.
  If you need both on the same line, mypy's annotation needs to go first,
  e.g. `# type: ignore[override]  # pyright: ignore[reportGeneralTypeIssues]`.

Imports in stubs are considered private (not part of the exported API)
unless:
* they use the form ``from library import name as name`` (sic, using
  explicit ``as`` even if the name stays the same); or
* they use the form ``from library import *`` which means all names
  from that library are exported.

Stub files support forward references natively.  In other words, the
order of class declarations and type aliases does not matter in
a stub file.  You can also use the name of the class within its own
body.  Focus on making your stubs clear to the reader.  Avoid using
string literals in type annotations.

### Using `Any` and `object`

When adding type hints, avoid using the `Any` type when possible. Reserve
the use of `Any` for when:
* the correct type cannot be expressed in the current type system; and
* to avoid union returns (see above).

Note that `Any` is not the correct type to use if you want to indicate
that some function can accept literally anything: in those cases use
`object` instead.

When using `Any`, document the reason for using it in a comment. Ideally,
document what types could be used. The `_typeshed` module also provides
a few aliases to `Any` — like `Incomplete` and `MaybeNone` (see below) —
that should be used instead of `Any` in appropriate situations and double
as documentation.

### Context managers

When adding type annotations for context manager classes, annotate
the return type of `__exit__` as bool only if the context manager
sometimes suppresses exceptions -- if it sometimes returns `True`
at runtime. If the context manager never suppresses exceptions,
have the return type be either `None` or `bool | None`. If you
are not sure whether exceptions are suppressed or not or if the
context manager is meant to be subclassed, pick `bool | None`.
See https://github.com/python/mypy/issues/7214 for more details.

`__enter__` methods and other methods that return instances of the
current class should be annotated with `typing_extensions.Self`
([example](https://github.com/python/typeshed/blob/3581846/stdlib/contextlib.pyi#L151)).

### Naming

Type variables and aliases you introduce purely for legibility reasons
should be prefixed with an underscore to make it obvious to the reader
they are not part of the stubbed API.

A few guidelines for protocol names below. In cases that don't fall
into any of those categories, use your best judgement.

* Use plain names for protocols that represent a clear concept
  (e.g. `Iterator`, `Container`).
* Use `SupportsX` for protocols that provide callable methods (e.g.
  `SupportsInt`, `SupportsRead`, `SupportsReadSeek`).
* Use `HasX` for protocols that have readable and/or writable attributes
  or getter/setter methods (e.g. `HasItems`, `HasFileno`).

### `@deprecated`

Typeshed uses the `@typing_extensions.deprecated` decorator
(`@warnings.deprecated` since Python 3.13) to mark deprecated
functionality; see [PEP 702](https://peps.python.org/pep-0702/).

A few guidelines for how to use it:

* In the standard library, apply the decorator only in Python versions
  where an appropriate replacement for the deprecated functionality
  exists. If in doubt, apply the decorator only on versions where the
  functionality has been explicitly deprecated, either through runtime
  warnings or in the documentation. Use `if sys.version_info` checks to
  apply the decorator only to some versions.
* Keep the deprecation message concise, but try to mention the projected
  version when the functionality is to be removed, and a suggested
  replacement.

### Incomplete annotations

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
def foo(x: Incomplete | None = None) -> list[Incomplete]: ...
```

### `Any` vs. `Incomplete`

While `Incomplete` is a type alias of `Any`, they serve difference purposes:
`Incomplete` is a placeholder where a proper type might be substituted.
It's a "to do" item and should be replaced if possible. `Any` is used when
it's not possible to accurately type an item using the current type system.
It should be used sparingly.

### "The `Any` trick"

In cases where a function or method can return `None`, but where forcing the
user to explicitly check for `None` can be detrimental, use
`_typeshed.MaybeNone` (an alias to `Any`), instead of `None`.

Consider the following (simplified) signature of `re.Match[str].group`:

```python
class Match:
    def group(self, group: str | int, /) -> str | MaybeNone: ...
```

This avoid forcing the user to check for `None`:

```python
match = re.fullmatch(r"\d+_(.*)", some_string)
assert match is not None
name_group = match.group(1)  # The user knows that this will never be None
return name_group.uper()  # This typo will be flagged by the type checker
```

In this case, the user of `match.group()` must be prepared to handle a `str`,
but type checkers are happy with `if name_group is None` checks, because we're
saying it can also be something else than an `str`.

This is sometimes called "the Any trick".

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

* The upstream package ships a `py.typed` file for at least six months, or
* the package does not support any of the Python versions supported by
  typeshed.

If a package ships its own `py.typed` file, please follow these steps:

1. Open an issue with the earliest month of removal in the subject.
2. A maintainer will add the
   ["removal" label](https://github.com/python/typeshed/labels/removal).
3. Open a PR that sets the `obsolete_since` field in the `METADATA.toml`
   file to the first version of the package that shipped `py.typed`.
4. After at least six months, open a PR to remove the stubs.

If third-party stubs should be removed for other reasons, please follow these
steps:

1. Open an issue explaining why the stubs should be removed.
2. A maintainer will add the
   ["removal" label](https://github.com/python/typeshed/labels/removal).
3. Open a PR that sets the `no_longer_updated` field in the `METADATA.toml`
   file to `true`.
4. When a new version of the package was automatically uploaded to PyPI
   (which usually takes up to 3 hours), open a PR to remove the stubs.

If feeling kindly, please update [mypy](https://github.com/python/mypy/blob/master/mypy/stubinfo.py)
for any stub obsoletions or removals.

## Maintainer guidelines

The process for preparing and submitting changes also applies to
maintainers.  This ensures high quality contributions and keeps
everybody on the same page.  Avoid direct pushes to the repository.

When reviewing pull requests, follow these guidelines:

* Typing is hard. Try to be helpful and explain issues with the PR,
  especially to new contributors.
* When reviewing auto-generated stubs, just scan for red flags and obvious
  errors. Leave possible manual improvements for separate PRs.
* When reviewing large, hand-crafted PRs, you only need to look for red flags
  and general issues, and do a few spot checks.
* Review smaller, hand-crafted PRs thoroughly.

When merging pull requests, follow these guidelines:

* Always wait for tests to pass before merging PRs.
* Use "[Squash and merge](https://github.com/blog/2141-squash-your-commits)" to merge PRs.
* Make sure the commit message is meaningful. For example, remove irrelevant
  intermediate commit messages.
* The commit message for third-party stubs is used to generate the changelog.
  It should be valid Markdown, be comprehensive, read like a changelog entry,
  and assume that the reader has no access to the diff.
* Delete branches for merged PRs (by maintainers pushing to the main repo).

### Marking PRs as "deferred"

We sometimes use the ["deferred" label](https://github.com/python/typeshed/labels/deferred)
to mark PRs and issues that we'd like to accept, but that are blocked by some
external factor. Blockers can include:

- An unambiguous bug in a type checker (i.e., a case where the
  type checker is not implementing [the typing spec](https://typing.readthedocs.io/en/latest/spec/index.html)).
- A dependency on a typing PEP that is still under consideration.
- A pending change in a related project, such as stub-uploader.

PRs should only be marked as "deferred" if there is a clear path towards getting
the blocking issue resolved within a reasonable time frame. If a PR depends on
a more amorphous change, such as a type system change that has not yet reached
the PEP stage, it should instead be closed.

Maintainers who add the "deferred" label should state clearly what exactly the
blocker is, usually with a link to an open issue in another project.

### Closing stale PRs

To keep the number of open PRs manageable, we may close PRs when they have been
open for too long. Specifically, we close open PRs that either have failures in CI,
serious merge conflicts or unaddressed feedback, and that have not seen any
activity in three months.

We want to maintain a welcoming atmosphere for contributors, so use a friendly
message when closing the PR. Example message:

    Thanks for contributing! I'm closing this PR for now, because it still
    <fails some tests OR has unresolved review feedback OR has a merge conflict>
    after three months of inactivity. If you are still interested, please feel free to open
    a new PR (or ping us to reopen this one).
