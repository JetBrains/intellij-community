// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Test

/**
 * Tests specifically to test functionality of Python 2 and related to [PyTypeCheckerInspection].
 *
 * When dropping support for Python 2, check whether small adjustments to test expectations are
 * enough for conversion to Python 3.
 */
@TestFor(classes = [PyTypeCheckerInspection::class])
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class Py2TypeCheckerInspectionTest : PyCodeInsightTestCase() {

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27, assertRecursionPrevention = false)
  fun `str unicode`() = test("""
    def str_to_none(b):
        '''
        :type b: str
        '''
        pass

    def unicode_to_none(s):
        '''
        :type s: unicode
        '''
        pass

    def string_to_none(s):
        '''
        :type s: string
        '''
        pass

    def str_or_unicode_to_none(s):
        '''
        :type s: str or unicode
        '''
        pass

    def test():
        b1 = 'hello'
        s1 = u'привет'
        b2 = str(-1)
        s2 = unicode(3.14)
        ENC = 'utf-8'
        str_to_none(b1.decode(ENC))
    #               ^^^^^^^^^^^^^^ WARNING Expected type 'str', got 'unicode' instead
        unicode_to_none(b1.decode(ENC))
        string_to_none(b1.decode(ENC))
        str_or_unicode_to_none(b1.decode(ENC))
        b1.encode(ENC)
        s1.decode(ENC)
        str_to_none(s1.encode(ENC))
        unicode_to_none(s1.encode(ENC))  # mypy: str is compatible to unicode for PY2
        string_to_none(s1.encode(ENC))
        str_or_unicode_to_none(s1.encode(ENC))
        b2.decode(ENC)
        b2.encode(ENC)
        s2.decode(ENC)
        s2.encode(ENC)
    """.trimIndent())

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27, assertRecursionPrevention = false)
  fun `typed generator calls`() = test("""
    def test():
        def gen(n):
            for x in xrange(n):
                yield str(x)
        def f_1(xs):
            '''
            :type xs: list of int
            '''
            return xs
        def f_2(xs):
            '''
            :type xs: collections.Sequence of int
            '''
            return xs
        def f_3(xs):
            '''
            :type xs: collections.Container of int
            '''
            return xs
        def f_4(xs):
            '''
            :type xs: collections.Iterator of int
            '''
            return xs
        def f_5(xs):
            '''
            :type xs: collections.Iterable of int
            '''
            return xs
        def f_6(xs):
            '''
            :type xs: list
            '''
            return xs
        def f_7(xs):
            '''
            :type xs: collections.Sequence
            '''
            return xs
        def f_8(xs):
            '''
            :type xs: collections.Container
            '''
            return xs
        def f_9(xs):
            '''
            :type xs: collections.Iterator
            '''
            return xs
        def f_10(xs):
            '''
            :type xs: collections.Iterable
            '''
            return xs
        def f_11(xs):
            '''
            :type xs: list of string
            '''
            return xs
        def f_12(xs):
            '''
            :type xs: collections.Sequence of string
            '''
            return xs
        def f_13(xs):
            '''
            :type xs: collections.Container of string
            '''
            return xs
        def f_14(xs):
            '''
            :type xs: collections.Iterator of string
            '''
            return xs
        def f_15(xs):
            '''
            :type xs: collections.Iterable of string
            '''
            return xs
        return [
            ''.join(gen(10)),
            f_1(gen(11)),
    #           ^^^^^^^ WARNING Expected type 'List[int]', got 'Generator[str, Unknown, None]' instead
            f_2(gen(11)),
    #           ^^^^^^^ WARNING Expected type 'Sequence[int]', got 'Generator[str, Unknown, None]' instead
            f_3(gen(11)),
    #           ^^^^^^^ WARNING Expected type 'Container[int]', got 'Generator[str, Unknown, None]' instead
            f_4(gen(11)),
    #           ^^^^^^^ WARNING Expected type 'Iterator[int]', got 'Generator[str, Unknown, None]' instead
            f_5(gen(11)),
    #           ^^^^^^^ WARNING Expected type 'Iterable[int]', got 'Generator[str, Unknown, None]' instead
            f_6(gen(11)),
    #           ^^^^^^^ WARNING Expected type 'list', got 'Generator[str, Unknown, None]' instead
            f_7(gen(11)),
    #           ^^^^^^^ WARNING Expected type 'Sequence', got 'Generator[str, Unknown, None]' instead
            f_8(gen(11)),
    #           ^^^^^^^ WARNING Expected type 'Container', got 'Generator[str, Unknown, None]' instead
            f_9(gen(11)),
            f_10(gen(11)),
            f_11(gen(11)),
    #            ^^^^^^^ WARNING Expected type 'List[Union[str, unicode]]', got 'Generator[str, Unknown, None]' instead
            f_12(gen(11)),
    #            ^^^^^^^ WARNING Expected type 'Sequence[Union[str, unicode]]', got 'Generator[str, Unknown, None]' instead
            f_13(gen(11)),
    #            ^^^^^^^ WARNING Expected type 'Container[Union[str, unicode]]', got 'Generator[str, Unknown, None]' instead
            f_14(gen(11)),
            f_15(gen(11)),
            f_15('foo'.split('o')),
        ]
    """.trimIndent())

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `is instance implicit self types`() = test("""
    def test():
        x = 1
        if isinstance(x, unicode):
            x.encode('UTF-8') # pass
    """.trimIndent())

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `generic user functions`() = test("""
    def test():
        def f1(xs):
            '''
            :type xs: collections.Iterable of T
            '''
            return iter(xs).next()

        def f2(x, xs, z):
            '''
            :type x: T
            :type xs: list of T
            :type z: U
            '''
            return x in xs

        def id(x):
            '''
            :type x: T
            :rtype: T
            '''
            return x

        def f3(x, y, z):
            '''
            :type x: T
            :type y: U
            :type z: V
            '''
            r1 = id(x)
            r2 = id(y)
            r3 = id(z)
            return r1, r2, r3

        def f4(x):
            '''
            :type x: (bool, int, str)
            '''

        result = f1([1, 2, 3])
        print(result)
        print(result + 'foo')
    #                  ^^^^^ WARNING Expected type 'int', got 'str' instead
            
        # Bug: Expected error.
        # Generics are considered to be covariant.
        # I.e. `list[str]` is assignable to `list[int | str]`.
        # Thus, substitution `T` -> `int | str` is considered valid.
        f2(1, ['foo'], 'bar')
    
        result = f3(1, 'foo', True)
        f4(result)
    #      ^^^^^^ WARNING Expected type 'Tuple[bool, int, str]', got 'Tuple[int, str, bool]' instead
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-6542"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `dict literals`() = test("""
    def test():
        xs = {'foo': 1, 'bar': 2}
        for v in xs.values():
            print(v + None)
    #                 ^^^^ WARNING Expected type 'int', got 'None' instead
        for k in xs.keys():
            print(k + None)
    #                 ^^^^ WARNING Expected type 'AnyStr ≤: Union[str, unicode]', got 'None' instead
        for k in xs:
            print(k + None)
    #                 ^^^^ WARNING Expected type 'AnyStr ≤: Union[str, unicode]', got 'None' instead
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-8181"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `bytes subclass as str`() = test("""
    class String(bytes):
        pass

    def foo(x):
        '''
        :type x: str
        '''

    s = String('hello')
    foo(s)
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-4285"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `map return element type`() = test("""
    def test():
        xs = map(lambda x: x + 1, [1, 2, 3])
        print('foo' + xs[0])
    #                 ^^^^^ WARNING Expected type 'AnyStr ≤: Union[str, unicode]', got 'int' instead
        ys = map(tuple, iter([[1, 2, 3]]))
        print(1 + ys[0], 'bar' + ys[1])
    #             │              ^^^^^ WARNING Expected type 'AnyStr ≤: Union[str, unicode]', got 'tuple' instead
    #             ^^^^^ WARNING Expected type 'int', got 'tuple' instead
    """.trimIndent())

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `meta class iteration`() = test("""
    class M1(type):
        def __iter__(self):
            pass

    class M2(type):
        pass

    class C1(object):
        __metaclass__ = M1

    class C2(object):
        __metaclass__ = M2

    class B1(C1):
        pass

    for x in C1:
        pass

    for y in C2:
    #        ^^ WARNING Expected type 'collections.Iterable', got 'Type[C2]' instead
        pass

    for z in B1:
        pass
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-18275"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `str format`() = test("""
    b'{}'.format(0)
    u'{}'.format(0)
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-19884"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `set methods`() = test("""
    def foo(xs, ys):
        '''
        :type xs: set[int]
        :type ys: set[string]
        '''
        'foo' + xs.pop()
    #           ^^^^^^^^ WARNING Expected type 'AnyStr ≤: Union[str, unicode]', got 'int' instead
        xs.discard('foo')
    #              ^^^^^ WARNING Expected type 'int', got 'str' instead
        xs.remove('bar')
    #             ^^^^^ WARNING Expected type 'int', got 'str' instead
        xs.add(object())
    #          ^^^^^^^^ WARNING Expected type 'int', got 'object' instead
        
        ys.extend(xs)
    #      ^^^^^^ WARNING Unresolved attribute reference 'extend' for class 'set'
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-11943"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `mutable mapping`() = test("""
    def foo(x):
        '''
        :type x: collections.MutableMapping
        :rtype: dict
        '''
        return {v: k for k, v in x.iteritems()}

    d = dict(a=1, b=2)
    foo(d)

    l = [i for i in range(10)]
    foo(l)
    #   └ WARNING Expected type 'MutableMapping', got 'List[int]' instead
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-20073"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `map arguments in opposite order py2`() = test("""
    map('foo', lambda c: 42)
    #  ^^^^^^^^^^^^^^^^^^^^^ WARNING No overload of 'map' matches the arguments. Argument types: (str, (c: Unknown) -> Literal[42]). Expected one of: (__func: None, __iter1: Iterable[_T1]), (__func: (_T1) -> _S, __iter1: Iterable[_T1])
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-19723"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `keyword arguments`() = test("""
    def foo(**kwargs):
        '''
        :type kwargs: int
        '''
        pass

    foo(key1=10, key2="str")
    #            ^^^^^^^^^^ WARNING Expected type 'int', got 'str' instead
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-21350"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `builtin raw input`() = test("""
    class A:
        pass

    raw_input(A())
    raw_input(b"b")
    raw_input(u"u")
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-22763"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `chained comparisons`() = test("""
    print('a' < 'b' < 'c' < 'd')
    print(('a' < 'b') < 'c')
    #                   ^^^ WARNING Expected type 'int', got 'str' instead
    print((1, 1) < (1, 2) < (1, 3) < (1, 4))
    #                       │        ^^^^^^ WARNING Expected type 'Tuple[Literal[1, 3], ...]', got 'Tuple[Literal[1], Literal[4]]' instead
    #                       ^^^^^^ WARNING Expected type 'Tuple[Literal[1, 2], ...]', got 'Tuple[Literal[1], Literal[3]]' instead
    print(((1, 1) < (1, 2)) < (1, 3))
    #                         ^^^^^^ WARNING Expected type 'int', got 'Tuple[Literal[1], Literal[3]]' instead
    print(1.0 < 4.5 < 9.3 < 10.0)
    print((1.0 < 4.5) < 9.3)

    from datetime import datetime
    d1 = datetime.now() 
    d2 = datetime.now() 
    d3 = datetime.now() 

    print(d1 < d2 < d3)
    print((d1 < d2) < d3)
    #                 ^^ WARNING Expected type 'int', got 'datetime' instead
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-24287"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `promoting bytearray to str and unicode`() = test("""
    def f(bar):
    # type: (str) -> str
        return bar

    f(bytearray())
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-25120"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `iterate over dict value when its type is union`() = test("""
    KWARGS = {
        "do_stuff": True,
        "little_list": ['WORLD_RET_BP_IMPALA_AB.Control', 'WORLD_RET_BP_IMPALA_AB.Impala_WS'],
    }

    for element in KWARGS["little_list"]:
    #              ^^^^^^^^^^^^^^^^^^^^^ WARNING Expected type 'collections.Iterable', got 'Union[bool, List[str]]' instead
        print(element)
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-16066"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `basestring matches type`() = test("""
    class Filter(object):
      def __init__(self, allowed_types):
        '''
        :type allowed_types: type
        '''
        pass

    Filter(basestring)
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-23864"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `class object and metaclass compatibility`() = test("""
    class MetaClass(type):
        pass

    class SubMetaClass(MetaClass):
        pass

    class MetaClass2(type):
        pass

    class MyClass(object):
        __metaclass__ = MetaClass
        pass

    class MyClass2(object):
        __metaclass__ = SubMetaClass

    def builder():
        return MetaClass("MyClass", (), {})

    Generated = builder()

    class MyClass3(Generated):
        pass

    class MyClass4:
        __metaclass__ = MetaClass2
        pass

    class MyClass5:
        pass

    def f(x):
    # type: (MetaClass) -> None
        pass

    f(MyClass)
    f(MyClass2)
    f(Generated)
    f(MyClass3)
    f(MyClass4)
    # ^^^^^^^^ WARNING Expected type 'MetaClass', got 'Type[MyClass4]' instead
    f(MyClass5)
    # ^^^^^^^^ WARNING Expected type 'MetaClass', got 'Type[MyClass5]' instead
    """.trimIndent())

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `matching open function call types py2`() = test("""
    from foo import calcT, calcB

    with open('1.txt') as file1:
        calcT(file1)
    #         ^^^^^ WARNING Expected type 'TextIO', got 'BinaryIO' instead
        calcB(file1)

    with open('1.txt', 'rb') as file2:
        calcT(file2)
    #         ^^^^^ WARNING Expected type 'TextIO', got 'BinaryIO' instead
        calcB(file2)
    """.trimIndent(),
                                                       "foo.py" to """
    def calcT(a): pass
    def calcB(a): pass
    """.trimIndent(),
                                                       "foo.pyi" to """
    from typing import BinaryIO, TextIO

    def calcT(a: TextIO) -> int: ...
    def calcB(a: BinaryIO) -> int: ...
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-27949"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `assigning to dict entry`() = test("""
    from typing import Dict

    data = {}  # type: Dict[int, str]
    data['test'] = 12
    #    │         ^^ WARNING Expected type 'str', got 'Literal[12]' instead
    #    ^^^^^^ WARNING Expected type 'int', got 'str' instead
    data[12] = 'test'

    """.trimIndent())

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `call by class`() = test("""
    class Z:
        def method(self):
              pass

    class A:
        def method(self):
            Z.method(self) # passing wrong instance
    #                ^^^^ WARNING Expected type 'Z', got 'Self@A' instead
            Z.method(Z) # passing class instead of instance
    #                └ WARNING Expected type 'Z', got 'Type[Z]' instead
            Z.method(A) # passing class instead of instance AND wrong class
    #                └ WARNING Expected type 'Z', got 'Type[A]' instead
            Z.method(A()) # passing class instead of instance AND wrong class
    #                ^^^ WARNING Expected type 'Z', got 'A' instead
            Z.method(Z()) # pass

        def __init__(self):
            pass

    class B(A):
        def __init__(self):
            A.__init__(self) # pass

    A.method(B())
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-39762"])
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON27)
  fun `overloads and pure stub in same pyi scope`() = test(
    """
    from module import foo, bar

    foo(5)
    foo("str", 5)
    foo([5])
    #   ^^^ WARNING Expected type 'int', got 'List[Literal[5]]' instead

    bar("str")
    bar(5)
    bar([5])
    #   ^^^ WARNING No overload of 'bar' matches the arguments. Argument types: (List[Literal[5]]). Expected one of: (p: str), (p: int)
    """.trimIndent(),
    "module.pyi" to """
    import sys
    from typing import overload

    if sys.version_info >= (3, ):
        def foo(p: str) -> str: pass
    else:
        @overload
        def foo(p: int) -> int: pass
        @overload
        def foo(p: str, i: int) -> str: pass

    @overload
    def bar(p: str) -> str: pass

    @overload
    def bar(p: int) -> int: pass
    """.trimIndent(),
  )

  @Test
  @TestFor(issues = ["PY-27551"])
  fun `dunder init annotated with no return`() = test("""
    from typing import NoReturn

    class Test:
        def __init__(self) -> NoReturn:
            raise Exception()
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-80427"])
  @TestCaseOptions(assertRecursionPrevention = false)
  fun `none type type`() = test("""
    from types import NoneType

    x: NoneType = None
    y: type[NoneType] = type(None)
    z: type[None] = NoneType
    """.trimIndent())

}
