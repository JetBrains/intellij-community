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

Skeletons should respect PEP-8 and PEP-257 style guides.

The most simple way of specifying types in skeletons is Sphinx docstrings.
Function annotations could be used for specifying types, but they are
available only for Python 3.

There is no standard notation for specifying types in Python code. We propose
the following notation:

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

The formal syntax is defined in `pytypes` library (work in progress).

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
Python 3 function annotations. See also `python-righarrow`, `typeannotations`.

The recommended way of checking the version of Python is:

    import sys

    if sys.version_info >= (2, 7) and sys.version_info < (3,):
        def from_27_until_30():
            pass


PyCharm
-------

PyCharm 3 can extract the following information from the skeletons:

* Parameters of functions and methods
* Return types and parameter types of functions and methods
* Types of assignment targets
* Extra module members
* TODO

PyCharm 3 comes with a snapshot of the Python skeletons repository. You
should not modify it, because it will be updated with the PyCharm
installation. If you want to change the skeletons, clone the skeletons GitHub
repository into your PyCharm config directory:

    cd <PyCharm config>
    git clone https://github.com/JetBrains/python-skeletons.git

where `<PyCharm config>` is:

* Mac OS X: `~/Library/Preferences/PyCharmXX/config`
* Linux: `~/.PyCharmXX/config`
* Windows: `<User home>\.PyCharmXX\config`

Please send your PyCharm-related bug reports and feature requests to
[PyCharm issue tracker](http://youtrack.jetbrains.com/issues/PY).


Feedback
--------

If you want to contribute, send your pull requests to the Python skeletons
repository on GitHub. Please make sure, that you follow the conventions above.

Use [code-quality](http://mail.python.org/mailman/listinfo/code-quality)
mailing list to discuss Python skeletons.
