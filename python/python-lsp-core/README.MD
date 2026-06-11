## Updated: 2025-12-26

Today we have major LSP providers for Python type checking:
- [pyrefly](https://github.com/facebook/pyrefly)
- [ty](https://github.com/astral-sh/ty)

# Overview

## Pyrefly
Pyrefly is Meta’s new Rust-based Python type checker, replacing Pyre — Meta’s previous Python type checker written in OCaml. 
The expectation is that Pyrefly should be faster, more portable, and more capable compared to Pyre.

One key thing the Pyrefly team made very clear this year is that they want to be truly open source. Pyre was also technically open source, but it was more of a “we built this for our needs, but here’s the source code if you want it”. In contrast, one of the foundational goals of Pyrefly is to be more engaged with the needs of the open-source community.

## ty
ty is also a Rust-based Python type checker currently under development by Astral, the team behind uv and ruff. The project was formerly known as Red-Knot, but now has its official name: ty. Compared to Meta, Astral has been much quieter about its announcement: just a soft launch on GitHub, a brief 30-minute presentation, and a couple of blog articles and podcasts here and there.


## Comparison

Both pyrefly and ty are written in Rust, both are incremental (albeit implemented slightly differently: see details below), and both are powered under the hood by Ruff for AST parsing. Also, both have first-class support for command-line type checking and LSP/IDE integration.

However, other than the fact that they are both fast Python type checkers, that’s where the similarities end.
The primary difference lies in their goals. Pyrefly tries to be as aggressive as possible when typing — inferring as much as possible so that even code with absolutely no explicit types can have some amount of typing guarantees.

ty, on the other hand, follows a different mantra: the gradual guarantee. The principal idea is that in a well-typed program, removing a type annotation should not cause a type error. In other words: you shouldn’t need to add new types to working code to resolve type errors.

Example with difference:
```python

def foo(imp: Any):
  return str(imp)

a = foo(123)

# ✅ pyrefly | Revealed type: str
# ➖ ty       | Revealed type: `Unknown`
# ➖ mypy    | Revealed type is "Any"
# ✅ pyright | Type of "a" is "str"
reveal_type(a)

# ✅ pyrefly | ERROR: `+` is not supported between `str` and `Literal[1]`
# ➖ ty       | < No Error >
# ➖ mypy     | < No Error >
# ✅ pyright | ERROR: Operator "+" not supported for types "str" and "Literal[1]"
a + 1
```


## Experiments

I decided to test and implement type inference based on pyrefly. To resolve types I use the following algorithm:
- Filter PyTypedElements to determine whether they should be sent to LSP type evaluation or can be resolved instantly. For instance, for PyClass and PyStringLiteral we don't need to send them to LSP because it's sufficient to resolve the type from the builtin type provider.
- I created a special cache for each file that persists between document modifications and stores calculated types there.
- The first 10 requests are sent to LSP directly without batching because we have completion that should run really fast. Please note that our limitation is to not use completion from LSP and instead use builtin with LSP type provider. After the first 10 requests, I collect all filtered types from the file and send them to LSP as a batch request.
- LSP returns types as strings with type hints. We use special code that converts these strings to PyTypes. The code is not ideal and some types cannot be converted from string to PyType.

## Test Environment
For testing I used a project from https://packages.jetbrains.team/files/p/pyqa/pycharm-test-data/pandasExample.zip and measured how much time it takes to infer types for all PyTypedElements in the file.
I tested both the builtin type provider and the pyrefly type provider. I ran the task in single-threaded mode (one by one) and in multithreaded mode with 8 parallel threads.
After the first run, I modified the file by adding `a=2\n` to the end and checked types again.

## Results

| LSP Provider        | First Run (ms) | Second Run (ms) |
|---------------------|----------------|-----------------|
| builtin (single)    | 4040           | 2400            |
| pyrefly (single)    | 4010           | 1200            |
| builtin (8 thread)  | 2200           | 1200            |
| pyrefly (8 threads) | 3051           | 608             |

The following durations are for single-threaded Pyrefly:

| Operation             | Total Duration First Run (ms)  | Second Run (ms) |
|-----------------------|--------------------------------|-----------------|
| LSP Request           | 3000                           | 1789            |
| String Type -> PyType | 557                            | 166             |
| Total                 | 4010                           | 1200            |

Please note that in my test I had 2 LSP requests because I inferred types for pandas_example.py and the file pandas/typing.py was processed by the String PyType to PyType resolver.



# Tested Hypotheses:
- Sending LSP requests without batching (1 type per request): This has significant overhead and is ~10x slower compared to batching.
- Sending all LSP requests in a single batch: This is faster overall, but results in very long response times for large files. For instance, a 3k-line file has a completion response time of 3 seconds. Not all files need types for all PSI elements, so storing them all creates significant memory and time overhead, especially for libraries.
- Sending LSP requests both individually and in batch simultaneously: There is some internal blocking, and the response for a single type can take longer than the batch response.

# Risks
- The rest of our codeinsight was created and tested using our current type inference engine. Replacing this could cause issues for anything that relies on typing. These could include refactorings, resolve, inspections. To mitigate this, we should make our codeinsight tests (most of which are unit tests) are compatible with external type inference.
- Memory consumption: The WebStorm team is dealing with a heavy TS server. We should be prepared for the same data to be cached twice: once inside the LSP and again inside the IDE.
- We should invest in converting LSP string type representations into PyType instances. Ideally, we wouldn't use our old type engine. 

# Distribution of external type inference binaries
- We have a fork of Ty at the JB Account on GitHub. This fork contains an action that builds the distribution of the patched Ty.
- Pyrefly has already accepted our provideType() method. Therefore, we can install Pyrefly using the Python package manager.

# Helpful Links:
- Test project for validating type resolution on large pandas file: https://packages.jetbrains.team/files/p/pyqa/pycharm-test-data/pandasExample.zip
- My MR branch: https://code.jetbrains.team/p/ij/repositories/ultimate/reviews/186102/files?repo=ultimate
- My MR to pyrefly: https://github.com/facebook/pyrefly/pull/1918
