# Contributing to typeshed

Welcome!  typeshed is a community project that aims to work for a wide
range of Python users and Python codebases.  If you're trying a type
checker on your Python code, your experience and what you can contribute
are important to the project's success.


## The contribution process at a glance

1. Read the [README.md file](README.md).
2. Set up your environment to be able to [run all tests](README.md#running-the-tests).  They should pass.
3. [Prepare your changes](#preparing-changes):
    * Small fixes and additions can be submitted directly as pull requests,
      but [contact us](#discussion) before starting significant work.
    * Create your stubs [conforming to the coding style](#stub-file-coding-style).
    * Make sure your tests pass cleanly on `mypy`, `pytype`, and `flake8`.
    * Reformat your stubs with `black` and `isort`.
4. [Submit your changes](#submitting-changes) by opening a pull request.
5. You can expect a reply within a few days:
    * Diffs are merged when considered ready by the core team.
    * Feel free to ping the core team if your pull request goes without
      a reply for more than a few days.

For more details, read below.


## Discussion

If you've run into behavior in the type checker that suggests the type
stubs for a given library are incorrect or incomplete,
we want to hear from you!

Our main forum for discussion is the project's [GitHub issue
tracker](https://github.com/python/typeshed/issues).  This is the right
place to start a discussion of any of the above or most any other
topic concerning the project.

For less formal discussion, try the typing chat room on
[gitter.im](https://gitter.im/python/typing).  Some Mypy core developers
are almost always present; feel free to find us there and we're happy
to chat.  Substantive technical discussion will be directed to the
issue tracker.

### Code of Conduct

Everyone participating in the typeshed community, and in particular in
our issue tracker, pull requests, and IRC channel, is expected to treat
other people with respect and more generally to follow the guidelines
articulated in the [Python Community Code of
Conduct](https://www.python.org/psf/codeofconduct/).


## Submitting Changes

Even more excellent than a good bug report is a fix for a bug, or the
implementation of a much-needed stub. We'd love to have
your contributions.

We use the usual GitHub pull-request flow, which may be familiar to
you if you've contributed to other projects on GitHub.  For the
mechanics, see [Mypy's git and GitHub workflow help page](https://github.com/python/mypy/wiki/Using-Git-And-GitHub),
or [GitHub's own documentation](https://help.github.com/articles/using-pull-requests/).

Anyone interested in type stubs may review your code.  One of the core
developers will merge your pull request when they think it's ready.
For every pull request, we aim to promptly either merge it or say why
it's not yet ready; if you go a few days without a reply, please feel
free to ping the thread by adding a new comment.

To get your pull request merged sooner, you should explain why you are
making the change. For example, you can point to a code sample that is
processed incorrectly by a type checker. It is also helpful to add
links to online documentation or to the implementation of the code
you are changing.

Also, do not squash your commits after you have submitted a pull request, as this
erases context during review. We will squash commits when the pull request is merged.

At present the core developers are (alphabetically):
* David Fisher (@ddfisher)
* Łukasz Langa (@ambv)
* Jukka Lehtosalo (@JukkaL)
* Ivan Levkivskyi (@ilevkivskyi)
* Matthias Kramm (@matthiaskramm)
* Greg Price (@gnprice)
* Sebastian Rittau (@srittau)
* Guido van Rossum (@gvanrossum)
* Shantanu (@hauntsaninja)
* Rune Tynan (@CraftSpider)
* Jelle Zijlstra (@JelleZijlstra)

NOTE: the process for preparing and submitting changes also applies to
core developers.  This ensures high quality contributions and keeps
everybody on the same page.  Avoid direct pushes to the repository.


## Preparing Changes

### Before you begin

If your change will be a significant amount of work to write, we highly
recommend starting by opening an issue laying out what you want to do.
That lets a conversation happen early in case other contributors disagree
with what you'd like to do or have ideas that will help you do it.

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

### Stub file coding style

#### Syntax example

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

#### Conventions

Stub files are *like* Python files and you should generally expect them
to look the same.  Your tools should be able to successfully treat them
as regular Python files.  However, there are a few important differences
you should know about.

Style conventions for stub files are different from PEP 8. The general
rule is that they should be as concise as possible.  Specifically:
* lines can be up to 130 characters long;
* functions and methods that don't fit in one line should be split up
  with one argument per line;
* all function bodies should be empty;
* prefer ``...`` over ``pass``;
* prefer ``...`` on the same line as the class/function signature;
* avoid vertical whitespace between consecutive module-level functions,
  names, or methods and fields within a single class;
* use a single blank line between top-level class definitions, or none
  if the classes are very small;
* do not use docstrings;
* use variable annotations instead of type comments, even for stubs
  that target older versions of Python;
* for arguments with a type and a default, use spaces around the `=`.

Stubs should be reformatted with the formatters
[black](https://github.com/psf/black) and
[isort](https://github.com/PyCQA/isort) before submission.
These formatters are included in typeshed's `requirements-tests-py3.txt` file.
A sample `pre-commit` file is included in the typeshed repository.  Copy it
to `.git/hooks` and adjust the path to your virtual environment's `bin`
directory to automatically reformat stubs before commit.

Stub files should only contain information necessary for the type
checker, and leave out unnecessary detail:
* for arguments with a default, use `...` instead of the actual
  default;
* for arguments that default to `None`, use `Optional[]` explicitly
  (see below for details);
* use `float` instead of `Union[int, float]`.

Some further tips for good type hints:
* avoid invariant collection types (`List`, `Dict`) in argument
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

For arguments with type and a default value of `None`, PEP 484
prescribes that the type automatically becomes `Optional`.  However we
prefer explicit over implicit in this case, and require the explicit
`Optional[]` around the type.  The mypy tests enforce this (through
the use of --no-implicit-optional) and the error looks like
`Incompatible types in assignment (expression has type None, variable
has type "Blah") `.

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

NOTE: there are stubs in this repository that don't conform to the
style described above.  Fixing them is a great starting point for new
contributors.

### Stub versioning

There are separate directories for `stdlib` and `third_party` stubs.
Within those, there are separate directories for different versions of
Python the stubs target.

The directory name indicates the major version of Python that a stub targets
and optionally the lowest minor version, with the exception of the `2and3`
directory which applies to both Python 2 and 3.

For example, stubs in the `3` directory will be applied to all versions of
Python 3, though stubs in the `3.7` directory will only be applied to versions
3.7 and above. However, stubs in the `2` directory will not be applied to
Python 3.

It is preferred to use a single stub in the more generic directory that
conditionally targets specific versions when needed, as opposed
to maintaining multiple stub files within more specific directories. Similarly,
if the given library works on both Python 2 and Python 3, prefer to put your
stubs in the `2and3` directory, unless the types are so different that the stubs
become unreadable that way.

You can use checks like `if sys.version_info >= (3, 8):` to denote new
functionality introduced in a given Python version or solve type
differences.  When doing so, only use one-tuples or two-tuples.  This is
because:

* mypy doesn't support more fine-grained version checks; and more
  importantly

* the micro versions of a Python release will change over time in your
  checking environment and the checker should return consistent results
  regardless of the micro version used.

Because of this, if a given functionality was introduced in, say, Python
3.7.4, your check:

* should be expressed as `if sys.version_info >= (3, 7):`
* should NOT be expressed as `if sys.version_info >= (3, 7, 4):`
* should NOT be expressed as `if sys.version_info >= (3, 8):`

This makes the type checker assume the functionality was also available
in 3.7.0 - 3.7.3, which while *technically* incorrect is relatively
harmless.  This is a strictly better compromise than using the latter
two forms, which would generate false positive errors for correct use
under Python 3.7.4.

Note: in its current implementation, typeshed cannot contain stubs for
multiple versions of the same third-party library.  Prefer to generate
stubs for the latest version released on PyPI at the time of your
stubbing.

### What to do when a project's documentation and implementation disagree

Type stubs are meant to be external type annotations for a given
library.  While they are useful documentation in its own merit, they
augment the project's concrete implementation, not the project's
documentation.  Whenever you find them disagreeing, model the type
information after the actual implementation and file an issue on the
project's tracker to fix their documentation.

## Issue-tracker conventions

We aim to reply to all new issues promptly.  We'll assign one or more
labels to indicate we've triaged an issue, but most typeshed issues
are relatively simple (stubs for a given module or package are
missing, incomplete or incorrect) and we won't add noise to the
tracker by labeling all of them.  Please see the
[list of all labels](https://github.com/python/typeshed/issues/labels)
for a detailed description of the labels we use.

Sometimes a PR can't make progress until some external issue is
addressed.  We indicate this by editing the subject to add a ``[WIP]``
prefix.  (This should be removed before committing the issue once
unblocked!)

### Core developer guidelines

Core developers should follow these rules when processing pull requests:

* Always wait for tests to pass before merging PRs.
* Use "[Squash and merge](https://github.com/blog/2141-squash-your-commits)" to merge PRs.
* Delete branches for merged PRs (by core devs pushing to the main repo).
* Make sure commit messages to master are meaningful. For example, remove irrelevant
  intermediate commit messages.
* If stubs for a new library are submitted, notify the library's maintainers.

When reviewing PRs, follow these guidelines:

* Typing is hard. Try to be helpful and explain issues with the PR,
  especially to new contributors.
* When reviewing auto-generated stubs, just scan for red flags and obvious
  errors. Leave possible manual improvements for separate PRs.
* When reviewing large, hand-crafted PRs, you only need to look for red flags
  and general issues, and do a few spot checks.
* Review smaller, hand-crafted PRs thoroughly.
