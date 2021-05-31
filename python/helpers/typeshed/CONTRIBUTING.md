# Contributing to typeshed

Welcome!  typeshed is a community project that aims to work for a wide
range of Python users and Python codebases.  If you're trying a type
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
4. [Format and check your stubs](#formatting-stubs).
5. Optionally [run the tests](#running-the-tests).
6. [Submit your changes](#submitting-changes) by opening a pull request.

You can expect a reply within a few days, but please be patient when
it takes a bit longer. For more details, read below.

## Preparing the environment

To reformat the code, check for common problems, and
run the tests, you need to prepare a
[virtual environment](https://docs.python.org/3/tutorial/venv.html)
with the necessary libraries installed. To do this, run:

```
$ python3 -m venv .venv3
$ source .venv3/bin/activate
(.venv3)$ pip install -U pip
(.venv3)$ pip install -r requirements-tests-py3.txt
```

To automatically check your code before committing, copy the file
`pre-commit` to `.git/hooks/pre-commit`.

## Where to make changes

### Standard library stubs

The `stdlib` directory contains stubs for modules in the
Python 3 standard library — which
includes pure Python modules, dynamically loaded extension modules,
hard-linked extension modules, and the builtins. The `VERSIONS` file lists
the versions of Python where the module is available.

Stubs for Python 2 are available in the `stdlib/@python2` subdirectory.
Modules that are only available for Python 2 are not listed in `VERSIONS`.

### Third-party library stubs

Modules that are not shipped with Python but have a type description in Python
go into `stubs`. Each subdirectory there represents a PyPI distribution, and
contains the following:
* `METADATA.toml`, describing the package. See below for details.
* Stubs (i.e. `*.pyi` files) for packages and modules that are shipped in the
  source distribution. If the Python 2 version of the stubs must be kept
  *separate*, they can be put in a `@python2` subdirectory.
* (Rarely) some docs specific to a given type stub package in `README` file.

When a third party stub is
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

* `version`: The oldest version of the library for which the stubs are still
  applicable (i.e. reflect the actual runtime behaviour). Note that only two
  most significant version levels are supported (i.e. only single dot).
  When a significant change is made in the library, the version of the
  stub should be bumped (note that previous versions are still available
  on PyPI).
* `python2` (default: `False`) and `python3` (default: `True`): These fields
  indicate whether a package supports Python 2, Python 3, or both.
* `requires` (optional): A list of other stub packages that this package uses.
* `extra_description` (optional): Can be used to add a custom description to
  the package's long description. It should be a multi-line string in
  Markdown format.
* `obsolete_since` (optional): This field is part of our process for
  [removing obsolete third-party libraries](#third-party-library-removal-policy).
  It contains the first version of the corresponding library that ships
  its own `py.typed` file.

The format of all `METADATA.toml` files can be checked by running
`python3 ./tests/check_consistent.py`.


## Preparing Changes

### Before you begin

If your change will be a significant amount of work to write, we highly
recommend starting by opening an issue laying out what you want to do.
That lets a conversation happen early in case other contributors disagree
with what you'd like to do or have ideas that will help you do it.

### Format

Each Python module is represented by a `.pyi` "stub file".  This is a
syntactically valid Python file, although it usually cannot be run by
Python 3 (since forward references don't require string quotes).  All
the methods are empty.

Python function annotations ([PEP 3107](https://www.python.org/dev/peps/pep-3107/))
are used to describe the signature of each function or method.

See [PEP 484](http://www.python.org/dev/peps/pep-0484/) for the exact
syntax of the stub files and [below](#stub-file-coding-style) for the
coding style used in typeshed.

### Supported type system features

Since PEP 484 was accepted, there have been many other PEPs that added
new features to the Python type system. In general, new features can
be used in typeshed as soon as the PEP has been accepted and implemented
and most type checkers support the new feature.

Accepted features that *cannot* yet be used in typeshed include:
- [PEP 570](https://www.python.org/dev/peps/pep-0570/) (positional-only
  arguments): see [#4972](https://github.com/python/typeshed/issues/4972),
  use argument names prefixed with `__` instead
- [PEP 585](https://www.python.org/dev/peps/pep-0585/) (builtin
  generics): see [#4820](https://github.com/python/typeshed/issues/4820),
  mostly supported but bugs remain for a few specific cases
- [PEP 604](https://www.python.org/dev/peps/pep-0604/) (Union
  pipe operator): see [#4819](https://github.com/python/typeshed/issues/4819)
- [PEP 612](https://www.python.org/dev/peps/pep-0612/) (ParamSpec):
  see [#4827](https://github.com/python/typeshed/issues/4827)
- [PEP 613](https://www.python.org/dev/peps/pep-0613/) (TypeAlias):
  see [#4913](https://github.com/python/typeshed/issues/4913)

Supported features include:
- [PEP 544](https://www.python.org/dev/peps/pep-0544/) (Protocol)
- [PEP 586](https://www.python.org/dev/peps/pep-0586/) (Literal)
- [PEP 591](https://www.python.org/dev/peps/pep-0591/) (Final/@final)
- [PEP 589](https://www.python.org/dev/peps/pep-0589/) (TypedDict)
- [PEP 647](https://www.python.org/dev/peps/pep-0647/) (TypeGuard):
  see [#5406](https://github.com/python/typeshed/issues/5406)

Features from the `typing` module that are not present in all
supported Python 3 versions must be imported from `typing_extensions`
instead in typeshed stubs. This currently affects:

- `Final` and `@final` (new in Python 3.8)
- `Literal` (new in Python 3.8)
- `SupportsIndex` (new in Python 3.8)
- `TypedDict` (new in Python 3.8)
- `TypeGuard` (new in Python 3.10)

An exception is `Protocol`: although it was added in Python 3.8, it
can be used in stubs regardless of Python version.

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
like `if sys.version_info >= (3, 8):` to denote new functionality introduced
in a given Python version or solve type differences.  When doing so, only use
one-tuples or two-tuples. Because of this, if a given functionality was
introduced in, say, Python 3.7.4, your check:

* should be expressed as `if sys.version_info >= (3, 7):`
* should NOT be expressed as `if sys.version_info >= (3, 7, 4):`
* should NOT be expressed as `if sys.version_info >= (3, 8):`

When your stub contains if statements for different Python versions,
always put the code for the most recent Python version first.

### Incomplete stubs

We accept partial stubs, especially for larger packages. These need to
follow the following guidelines:

* Included functions and methods must list all arguments, but the arguments
  can be left unannotated. Do not use `Any` to mark unannotated arguments
  or return values.
* Partial classes must include a `__getattr__()` method marked with an
  `# incomplete` comment (see example below).
* Partial modules (i.e. modules that are missing some or all classes,
  functions, or attributes) must include a top-level `__getattr__()`
  function marked with an `# incomplete` comment (see example below).
* Partial packages (i.e. packages that are missing one or more sub-modules)
  must have a `__init__.pyi` stub that is marked as incomplete (see above).
  A better alternative is to create empty stubs for all sub-modules and
  mark them as incomplete individually.

Example of a partial module with a partial class `Foo` and a partially
annotated function `bar()`:

```python
def __getattr__(name: str) -> Any: ...  # incomplete

class Foo:
    def __getattr__(self, name: str) -> Any: ...  # incomplete
    x: int
    y: str

def bar(x: str, y, *, z=...): ...
```

### Using stubgen

Mypy includes a tool called [stubgen](https://mypy.readthedocs.io/en/latest/stubgen.html)
that auto-generates stubs for Python and C modules using static analysis,
Sphinx docs, and runtime introspection.  It can be used to get a starting
point for your stubs.  Note that this generator is currently unable to
determine most argument and return types and omits them or uses ``Any`` in
their place.  Fill out manually the types that you know.

## Stub file coding style

### Syntax example

The below is an excerpt from the types for the `datetime` module.

```python
MAXYEAR: int
MINYEAR: int

class date:
    def __init__(self, year: int, month: int, day: int) -> None: ...
    @classmethod
    def fromtimestamp(cls, timestamp: float) -> date: ...
    @classmethod
    def today(cls) -> date: ...
    @classmethod
    def fromordinal(cls, ordinal: int) -> date: ...
    @property
    def year(self) -> int: ...
    def replace(self, year: int = ..., month: int = ..., day: int = ...) -> date: ...
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

Stub files should only contain information necessary for the type
checker, and leave out unnecessary detail:
* for arguments with a default, use `...` instead of the actual
  default;
* for arguments that default to `None`, use `Optional[]` explicitly
  (see below for details);
* use `float` instead of `Union[int, float]`.

Some further tips for good type hints:
* use built-in generics (`list`, `dict`, `tuple`, `set`), instead
  of importing them from `typing`, **except** for arbitrary length tuples
  (`Tuple[int, ...]`) (see
  [python/mypy#9980](https://github.com/python/mypy/issues/9980));
* in Python 3 stubs, import collections (`Mapping`, `Iterable`, etc.)
  from `collections.abc` instead of `typing`;
* avoid invariant collection types (`list`, `dict`) in argument
  positions, in favor of covariant types like `Mapping` or `Sequence`;
* avoid Union return types: https://github.com/python/mypy/issues/1693;
* in Python 2, whenever possible, use `unicode` if that's the only
  possible type, and `Text` if it can be either `unicode` or `bytes`;
* use platform checks like `if sys.platform == 'win32'` to denote
  platform-dependent APIs.

Imports in stubs are considered private (not part of the exported API)
unless:
* they use the form ``from library import name as name`` (sic, using
  explicit ``as`` even if the name stays the same); or
* they use the form ``from library import *`` which means all names
  from that library are exported.

When adding type hints, avoid using the `Any` type when possible. Reserve
the use of `Any` for when:
* the correct type cannot be expressed in the current type system; and
* to avoid Union returns (see above).

Note that `Any` is not the correct type to use if you want to indicate
that some function can accept literally anything: in those cases use
`object` instead.

Stub files support forward references natively.  In other words, the
order of class declarations and type aliases does not matter in
a stub file.  You can also use the name of the class within its own
body.  Focus on making your stubs clear to the reader.  Avoid using
string literals in type annotations.

Type variables and aliases you introduce purely for legibility reasons
should be prefixed with an underscore to make it obvious to the reader
they are not part of the stubbed API.

When adding type annotations for context manager classes, annotate
the return type of `__exit__` as bool only if the context manager
sometimes suppresses exceptions -- if it sometimes returns `True`
at runtime. If the context manager never suppresses exceptions,
have the return type be either `None` or `Optional[bool]`. If you
are not sure whether exceptions are suppressed or not or if the
context manager is meant to be subclassed, pick `Optional[bool]`.
See https://github.com/python/mypy/issues/7214 for more details.

A few guidelines for protocol names below. In cases that don't fall
into any of those categories, use your best judgement.

* Use plain names for protocols that represent a clear concept
  (e.g. `Iterator`, `Container`).
* Use `SupportsX` for protocols that provide callable methods (e.g.
  `SupportsInt`, `SupportsRead`, `SupportsReadSeek`).
* Use `HasX` for protocols that have readable and/or writable attributes
  or getter/setter methods (e.g. `HasItems`, `HasFileno`).

## Formatting stubs

Stubs should be reformatted with the formatters
[black](https://github.com/psf/black) and
[isort](https://github.com/PyCQA/isort) before submission. They
should also be checked for common problems by using
[flake8](https://flake8.pycqa.org/en/latest/) and the flake8 plugins
[flake8-pyi](https://github.com/ambv/flake8-pyi) and
[flake8-bugbear](https://github.com/PyCQA/flake8-bugbear).
All of these packages have been installed if you followed the
[setup instructions above](#preparing-the-environment).

To format and check your stubs, run the following commands:

```
(.venv3)$ black stdlib stubs
(.venv3)$ isort stdlib stubs
(.venv3)$ flake8
```


## Running the tests

The tests are automatically run on every PR and push to the repo.
Therefore you don't need to run them locally, unless you want to run
them before making a pull request or you want to debug some problem without
creating several small commits.

For more information about our available tests, see
[tests/README.md](tests/README.md).


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

Also, do not squash your commits or use `git commit --amend` after you have submitted a pull request, as this
erases context during review. We will squash commits when the pull request is merged.
This way, your pull request will appear as a single commit in our git history, even
if it consisted of several smaller commits.

## Third-party library removal policy

Third-party packages are generally removed from typeshed when one of the
following criteria is met:

* The upstream package ships a `py.typed` file for at least 6-12 months, or
* the package does not support any of the Python versions supported by
  typeshed.

If a package ships its own `py.typed` file, please follow these steps:

1. Open an issue with the earliest month of removal in the subject.
2. A maintainer will add the
   ["removal" label](https://github.com/python/typeshed/labels/removal).
3. Open a PR that sets the `obsolete_since` field in the `METADATA.toml`
   file to the first version of the package that shipped `py.typed`.

## Maintainer guidelines

The process for preparing and submitting changes also applies to
maintainers.  This ensures high quality contributions and keeps
everybody on the same page.  Avoid direct pushes to the repository.

Maintainers should follow these rules when processing pull requests:

* Always wait for tests to pass before merging PRs.
* Use "[Squash and merge](https://github.com/blog/2141-squash-your-commits)" to merge PRs.
* Delete branches for merged PRs (by maintainers pushing to the main repo).
* Make sure commit messages to master are meaningful. For example, remove irrelevant
  intermediate commit messages.
* If stubs for a new library are submitted, notify the library's maintainers.

When reviewing pull requests, follow these guidelines:

* Typing is hard. Try to be helpful and explain issues with the PR,
  especially to new contributors.
* When reviewing auto-generated stubs, just scan for red flags and obvious
  errors. Leave possible manual improvements for separate PRs.
* When reviewing large, hand-crafted PRs, you only need to look for red flags
  and general issues, and do a few spot checks.
* Review smaller, hand-crafted PRs thoroughly.
