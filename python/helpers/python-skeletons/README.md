Python Skeletons
================

_This proposal is a draft._

Python skeletons are Python files that contain API definitions of existing
libraries extended for static analysis tools.

Rationale
---------

Python is a dynamic language less suitable for static code analysis than static
languages like C or Java. Although Python static analysis tools can extract
some information from Python source code without executing it, this information
is often very shallow and incomplete.

Dynamic features of Python are very useful for user code. But using these
features in APIs of third-party libraries and the standard library is not
always a good idea. Tools (and users, in fact) need clear definitions of APIs.
Often library API definitions are quite static and easy to grasp (defined
using `class`, `def`), but types of function parameters and return values
usually are not specified. Sometimes API definitions involve metaprogramming.

As there is not enough information in API definition code of libraries,
developers of static analysis tools collect extended API data themselves and
store it in their own formats. For example, PyLint uses imperative AST
transformations of API modules in order to extend them with hard-coded data.
PyCharm extends APIs via its proprietary database of declarative type
annotations. The absence of a common extended API information format makes it
hard for developers and users of tools to collect and share data.


Proposal
--------

The proposal is to create a common database of extended API definitions as a
collection of Python files called skeletons. Static analysis tools already
understand Python code, so it should be easy to start extracting API
definitions from these Python skeleton files. Regular function and class
definitions can be extended with additional docstrings and decorators, e.g. for
providing types of function parameters and return values. Static analysis tools
may use a subset of information contained in skeleton files needed for their
operation. Using Python files instead of a custom API definition format will
also make it easier for users to populate the skeletons database.

Declarative Python API definitions for static analysis tools cannot cover all
dynamic tricks used in real APIs of libraries: some of them still require
library-specific code analysis. Nevertheless the skeletons database is enough
for many libraries.

The proposed [python-skeletons](https://github.com/JetBrains/python-skeletons)
repository is hosted on GitHub.


Conventions
-----------

Skeletons should contain syntactically correct Python code, preferably compatible
with Python 2.6-3.3.

Skeletons should respect [PEP-8](http://www.python.org/dev/peps/pep-0008/) and
[PEP-257](http://www.python.org/dev/peps/pep-0257/) style guides.

If you need to reference the members of the original module of a skeleton, you
should import it explicitly. For example, in a skeleton for the `foo` module:

```python
import foo


class C(foo.B):
    def bar():
        """Do bar and return Bar.

        :rtype: foo.Bar
        """
        return foo.Bar()
```
Modules can be referenced in docstring without explicit imports.

The body of a function in a skeleton file should consist of a single `return`
statement that returns a simple value of the declared return type (e.g. `0`
for `int`, `False` for `bool`, `Foo()` for `Foo`). If the function returns
something non-trivial, its may consist of a `pass` statement.


### Types

There is no standard notation for specifying types in Python code. We would
like this standard to emerge, see the related work below.

The current understanding is that a standard for optional type annotations in
Python could use the syntax of function annotations in Python 3 and decorators
as a fallback in Python 2. The type system should be relatively simple, but it
has to include parametric (generic) types for collections and probably more.

As a temporary solution, we propose a simple way of specifying types in
skeletons using Sphinx docstrings using the following notation:

    Foo                # Class Foo visible in the current scope
    x.y.Bar            # Class Bar from x.y module
    Foo | Bar          # Foo or Bar
    (Foo, Bar)         # Tuple of Foo and Bar
    list[Foo]          # List of Foo elements
    dict[Foo, Bar]     # Dict from Foo to Bar
    T                  # Generic type (T-Z are reserved for generics)
    T <= Foo           # Generic type with upper bound Foo
    Foo[T]             # Foo parameterized with T
    (Foo, Bar) -> Baz  # Function of Foo and Bar that returns Baz

There are several shortcuts available:

    unknown            # Unknown type
    None               # type(None)
    string             # Py2: str | unicode, Py3: str
    bytestring         # Py2: str | unicode, Py3: bytes
    bytes              # Py2: str, Py3: bytes
    unicode            # Py2: unicode, Py3: str

The syntax is a subject to change. It is almost compatible to Python (except
function types), but its semantics differs from Python (no `|`, no implicitly
visible names, no generic types). So you cannot use these expressions in
Python 3 function annotations.

If you want to create a parameterized class, you should define its parameters
in the mock return type of a constructor:

```python
class C(object):
    """Some collection C that can contain values of T."""

    def __init__(self, value):
        """Initialize C.

        :type value: T
        :rtype: C[T]
        """
        pass

    def get(self):
        """Return the contained value.

        :rtype: T
        """
        pass
```


### Versioning

The recommended way of checking the version of Python is:

```python
import sys


if sys.version_info >= (2, 7) and sys.version_info < (3,):
    def from_27_until_30():
        pass
```

A skeleton should document the most recently released version of a library. Use
deprecation warnings for functions that have been removed from the API.

Skeletons for built-in symbols is an exception. There are two modules:
`__builtin__` for Python 2 and `builtins` for Python 3.


Related Work
------------

The JavaScript community is also interested in formalizing API definitions and
specifying types. They have come up with several JavaScript dialects that
support optional types: TypeScript, Dart. There is a JavaScript initiative
similar to the proposed Python skeletons called
[DefinitelyTyped](https://github.com/borisyankov/DefinitelyTyped). The idea is
to use TypeScript API stubs for various JavaScript libraries.

There are many approaches to specifying types in Python, none of them is widely
adopted at the moment:

* A series of old (2005) posts by GvR:
  [1](http://www.artima.com/weblogs/viewpost.jsp?thread=85551),
  [2](http://www.artima.com/weblogs/viewpost.jsp?thread=86641),
  [3](http://www.artima.com/weblogs/viewpost.jsp?thread=87182)
* String-based [python-rightarrow](https://github.com/kennknowles/python-rightarrow)
  library
* Expression-based [typeannotations](https://github.com/ceronman/typeannotations)
  library for Python 3
* [mypy](http://www.mypy-lang.org/) Python dialect
* [pytypes](https://github.com/pytypes/pytypes): Optional typing for Python proposal
* [Proposal: Use mypy syntax for function annotations](https://mail.python.org/pipermail/python-ideas/2014-August/028618.html) by GvR

See also the notes on function annotations in
[PEP-8](http://www.python.org/dev/peps/pep-0008/).


PyCharm / IntelliJ
------------------

PyCharm 3 and the Python plugin 3.x for IntelliJ can extract the following
information from the skeletons:

* Parameters of functions and methods
* Return types and parameter types of functions and methods
* Types of assignment targets
* Extra module members
* Extra class members
* TODO

PyCharm 3 comes with a snapshot of the Python skeletons repository (Python
plugin 3.0.1 for IntelliJ still doesn't include this repository). You
**should not** modify it, because it will be updated with the PyCharm / Python
plugin for IntelliJ installation. If you want to change the skeletons, clone
the skeletons GitHub repository into your PyCharm/IntelliJ config directory:

```bash
cd <config directory>
git clone https://github.com/JetBrains/python-skeletons.git
```

where `<config directory>` is:

* PyCharm
    * Mac OS X: `~/Library/Preferences/PyCharmXX`
    * Linux: `~/.PyCharmXX/config`
    * Windows: `<User home>\.PyCharmXX\config`
* IntelliJ
    * Mac OS X: `~/Library/Preferences/IntelliJIdeaXX`
    * Linux: `~/.IntelliJIdeaXX/config`
    * Windows: `<User home>\.IntelliJIdeaXX\config`

Please send your PyCharm/IntelliJ-related bug reports and feature requests to
[PyCharm issue tracker](https://youtrack.jetbrains.com/issues/PY).


Feedback
--------

If you want to contribute, send your pull requests to the Python skeletons
repository on GitHub. Please make sure, that you follow the conventions above.

Use [code-quality](http://mail.python.org/mailman/listinfo/code-quality)
mailing list to discuss Python skeletons.
