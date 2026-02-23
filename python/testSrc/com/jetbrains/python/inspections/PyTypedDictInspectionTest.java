// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyTypedDictInspectionTest extends PyInspectionTestCase {

  public void testUnknownKey() {
    doTestByText("""
                   from typing import TypedDict
                   class Movie(TypedDict, total=False):
                       name: str
                       year: int
                   Movie2 = TypedDict('Movie2', {'name': str, 'year': int}, total=False)
                   movie = Movie2(name='Blade Runner')
                   movie2 = Movie2(name='Blade Runner')
                   movie['year'] = 42
                   movie2['year'] = 42
                   movie[<warning descr="TypedDict \\"Movie2\\" has no key 'id'">'id'</warning>] = 43
                   movie2[<warning descr="TypedDict \\"Movie2\\" has no key 'id'">'id'</warning>] = 43
                   """);
  }

  public void testMetaclass() {
    doTestByText("""
                   from typing import TypedDict

                   class Movie(TypedDict, <warning descr="Specifying a metaclass is not allowed in TypedDict">metaclass=Meta</warning>):
                       name: str""");
  }

  public void testExtraClassDeclarations() {
    doTestByText("""
                   from typing import TypedDict
                   class Movie(TypedDict):
                       name: str
                       <warning descr="Invalid statement in TypedDict definition; expected 'field_name: field_type'">def my_method(self):
                           pass</warning>
                       <warning descr="Invalid statement in TypedDict definition; expected 'field_name: field_type'">class Horror:
                           def __init__(self):
                               ...</warning>""");
  }

  public void testInitializer() {
    doTestByText("""
                   from typing import TypedDict
                   class Movie(TypedDict):
                       name: str
                       year: int = <warning descr="Right-hand side values are not supported in TypedDict">42</warning>""");
  }

  public void testPass() {
    doTestByText("""
                   from typing import TypedDict
                   class Movie(TypedDict):
                       ...
                   class HorrorMovie(TypedDict):
                       pass""");
  }

  public void testNonTypedDictAsSuperclass() {
    doTestByText("""
                   from typing import TypedDict, NamedTuple
                   class Bastard:
                       pass
                   class X(TypedDict):
                       x: int
                   class Y(TypedDict):
                       y: str
                   class XYZ(X, <warning descr="TypedDict cannot inherit from a non-TypedDict base class">Bastard</warning>):
                       z: bool
                   class MyNT(NamedTuple):
                       a: str""");
  }

  public void testNameAndVariableNameDoNotMatch() {
    doTestByText("from typing import TypedDict\n" +
                 "Movie2 = TypedDict(<warning descr=\"First argument has to match the variable name\">'Movie'</warning>, {'name': str, 'year': int}, total=False)");
  }

  public void testKeyTypesAlternativeSyntax() {
    doTestByText("from typing import TypedDict, Any, Optional\n" +
                 "Movie = TypedDict('Movie', {'name': Optional[int], 'smth': type, 'smthElse': Any, 'year': <weak_warning descr=\"Value must be a type\">2</weak_warning>}, total=False)");
  }

  public void testTypeHintInParenthesis() {
    doTestByText(
      """
        class B(TypedDict):
            a: (
                int
                | str
            )""");
  }

  public void testKeyTypes() {
    doTestByText("""
                   from typing import TypedDict, Any, Optional
                   class Movie(TypedDict):
                       name: Optional[int]
                       smth: type
                       smthElse: Any
                       year: <weak_warning descr="Value must be a type">2</weak_warning>""");
  }

  public void testFinalKey() {
    doTestByText("""
                   from typing import TypedDict, Final
                   class Movie(TypedDict):
                       name: str
                       year: int
                   YEAR: Final = 'year'
                   m = Movie(name='Alien', year=1979)
                   years_since_epoch = m[YEAR] - 1970""");
  }

  public void testStringVariableAsKey() {
    doTestByText("""
                   from typing import TypedDict, Final
                   class Movie(TypedDict):
                       name: str
                       year: int
                   year = 'year'
                   year2 = year
                   m = Movie(name='Alien', year=1979)
                   years_since_epoch = m[year2] - 1970
                   year = 42
                   print(m[<warning descr="TypedDict key must be a string literal; expected one of ('name', 'year')">year</warning>])""");
  }

  public void testDelStatement() {
    doTestByText("""
                   from typing import TypedDict, Literal
                   class Movie(TypedDict):
                       name: str
                       year: int
                   class HorrorMovie(Movie, total=False):
                       based_on_book: bool
                   year = 'year'
                   year2 = year
                   m = HorrorMovie(name='Alien', year=1979)
                   del (m['based_on_book'], m[<warning descr="Key 'name' of TypedDict 'HorrorMovie' cannot be deleted">'name'</warning>])
                   del m[<warning descr="Key 'year' of TypedDict 'HorrorMovie' cannot be deleted">year2</warning>], m['based_on_book']
                   def foo(k1: Literal['name', 'year', 'based_on_book'], k2: Literal['based_on_book']):
                       del m[<warning descr="Key 'name' of TypedDict 'HorrorMovie' cannot be deleted"><warning descr="Key 'year' of TypedDict 'HorrorMovie' cannot be deleted">k1</warning></warning>]
                       del m[k2]""");
  }

  public void testDictModificationMethods() {
    doTestByText("""
                   from typing import TypedDict
                   class Movie(TypedDict, total=False):
                       name: str
                       year: int
                   m = Movie(name='Alien', year=1979)
                   m.<warning descr="This operation might break TypedDict consistency">clear</warning>()
                   m.<warning descr="This operation might break TypedDict consistency">popitem</warning>()""");
    doTestByText("""
                   from typing import TypedDict
                   class Movie(TypedDict):
                       name: str
                       year: int
                   class Horror(Movie, total=False):
                       based_on_book: bool
                   m = Horror(name='Alien', year=1979)
                   m.<warning descr="This operation might break TypedDict consistency">clear</warning>()
                   name = 'name'
                   m.pop('based_on_book')
                   m.<warning descr="Key 'year' of TypedDict 'Horror' cannot be deleted">pop</warning>('year')
                   m.<warning descr="This operation might break TypedDict consistency">popitem</warning>()
                   m.setdefault('based_on_book', <warning descr="Expected type 'bool', got 'int' instead">42</warning>)""");
  }

  public void testUpdateMethods() {
    doTestByText("""
                   from typing import TypedDict, Optional
                   class Movie(TypedDict):
                       name: str
                       year: Optional[int]
                   class Horror(Movie, total=False):
                       based_on_book: bool
                   m = Horror(name='Alien', year=1979)
                   d={'name':'Garden State', 'year':2004}
                   m.update(d)
                   m.update({'name':'Garden State', 'year':<warning descr="Expected type 'int | None', got 'str' instead">'2004'</warning>, <warning descr="TypedDict \\"Horror\\" cannot have key 'based_on'">'based_on'</warning>: 'book'})
                   m.update(name=<warning descr="Expected type 'str', got 'int' instead">1984</warning>, year=1984, based_on_book=<warning descr="Expected type 'bool', got 'str' instead">'yes'</warning>)
                   m.update([('name',<warning descr="Expected type 'str', got 'int' instead">1984</warning>), ('year',None)])""");
  }

  public void testDocString() {
    doTestByText("""
                   from typing import TypedDict
                   class Cinema(TypedDict):
                       ""\"
                           It's doc string
                       ""\"""");
  }

  public void testIncorrectTypedDictArguments() {
    doTestByText("""
                   from typing import TypedDict
                   BadTD1 = TypedDict("BadTD1", <warning descr="Expected dictionary literal">[1, 2, 3]</warning>)
                   
                   fields = {"v": int}
                   BadTD2 = TypedDict("BadTD2", <warning descr="Expected dictionary literal">fields</warning>)
                   """);
  }

  public void testTypedDictNonStringKey() {
    doTestByText("""
                   from typing import TypedDict
                   Movie = TypedDict('Movie', {'name': str, 'year': int}, total=False)
                   class Movie2(TypedDict, total=False):
                       name: str
                       year: int
                   movie = Movie()
                   movie2 = Movie2()
                   movie[<warning descr="TypedDict key must be a string literal; expected one of ('name', 'year')">2</warning>]
                   movie2[<warning descr="TypedDict key must be a string literal; expected one of ('name', 'year')">2</warning>]""");
  }

  public void testTypedDictKeyValueWrite() {
    doTestByText("""
                   from typing import TypedDict
                   Movie = TypedDict('Movie', {'name': str, 'year': int}, total=False)
                   class Movie2(TypedDict, total=False):
                       name: str
                       year: int
                   movie = Movie()
                   movie2 = Movie2()
                   movie['year'], movie2['year'] = <warning descr="Expected type 'int', got 'str' instead">'1984'</warning>, <warning descr="Expected type 'int', got 'str' instead">'1984'</warning>
                   """);
  }

  public void testTypedDictKeyValueWriteToTypedDictField() {
    doTestByText("""
                   from typing import TypedDict
                   X = TypedDict('X', {'d': dict}, total=False)
                   class X2(TypedDict, total=False):
                       d: dict
                   x = X()
                   x2 = X2()
                   x['d']['k'], x2['d']['k'] = 'v', 'v'""");
  }

  public void testIncorrectTotalityValue() {
    doTestByText("""
                   from typing import TypedDict
                   class X(TypedDict, total=<warning descr="Value of 'total' must be True or False">1</warning>):
                       x: int
                   """);
  }

  public void testIncorrectTotalityValueAlternativeSyntax() {
    doTestByText("from typing import TypedDict\n" +
                 "X = TypedDict('X', {'x': int}, total=<warning descr=\"Value of 'total' must be True or False\">1</warning>)");
  }

  public void testUnexpectedInitClassArgument() {
    doTestByText("""
                   from typing import TypedDict
                   class A(TypedDict, <warning descr="Unexpected argument 'ab' for __init_subclass__ of TypedDict">ab=False</warning>):
                       i: int""");
  }

  public void testUnexpectedArgumentAlternativeSyntax() {
    doTestByText("from typing import TypedDict\n" +
                 "X = TypedDict('X', {'x': int}, abb=False)");
  }

  public void testGetWithIncorrectKeyType() {
    doTestByText("""
                   from typing import TypedDict
                   class X(TypedDict):
                       x: int
                   x = X()
                   x.get(<warning descr="Key should be string">42</warning>, 67)""");
  }

  public void testGetWithIncorrectKeyValue() {
    doTestByText("""
                   from typing import TypedDict
                   class X(TypedDict):
                       x: int
                   x = X()
                   x.get(<warning descr="TypedDict \\"X\\" has no key 'y'">'y'</warning>, 67)
                   x.get('x', '')""");
  }

  // PY-39404
  public void testImportedTypedDict() {
    doMultiFileTest();
  }

  // PY-40906
  public void testLiteralAsTypedDictKey() {
    doTestByText("""
                   from typing import TypedDict, Literal, Union
                   class Movie(TypedDict):
                       name: str
                       year: int
                   def get_value(movie: Movie, key: Literal['year', 'name']) -> Union[int, str]:
                       return movie[key]
                   def get_value(movie: Movie, key: Literal['name']) -> Union[int, str]:
                       return movie[key]
                   def get_value(movie: Movie, key: Literal['name1']) -> Union[int, str]:
                       return movie[<warning descr="TypedDict \\"Movie\\" has no key 'name1'">key</warning>]
                   def get_value(movie: Movie, key: Literal['year', 42]) -> Union[int, str]:
                       return movie[<warning descr="TypedDict key must be a string literal; expected one of ('name', 'year')">key</warning>]
                   def get_value(movie: Movie, key: Literal['year', 'name1', '42']) -> Union[int, str]:
                       return movie[<warning descr="TypedDict \\"Movie\\" has no keys ('name1', '42')">key</warning>]
                   def get_value(movie: Movie, key: Literal[42]) -> Union[int, str]:
                       return movie[<warning descr="TypedDict key must be a string literal; expected one of ('name', 'year')">key</warning>]""");
  }

  // PY-44714
  public void testNoneAsType() {
    doTestByText("""
                   from typing import TypedDict
                   class X(TypedDict):
                       n: None
                   Y = TypedDict('Y', {'n': None})
                   """);
  }

  // PY-50025
  public void testNoWarningInSubscriptionExpressionOnDictLiteral() {
    doTestByText("""
                   ROMAN = {'I': 1, 'V': 5, 'X': 10, 'L': 50, 'C': 100, 'D': 500, 'M': 1000}
                   class Solution:
                       def romanToInt(self, roman: str) -> int:
                           result = 0
                           for letter in roman:
                               int_value = ROMAN[letter]
                           return 42""");
  }

  // PY-50113
  public void testNoWarningInGetMethodOnDictLiteral() {
    doTestByText("def baz(param: str):\n" +
                 "    {'a': 1}.get(param)");
  }

  // PY-43689
  public void testNoWarningOnTypesUsingForwardReferences() {
    doTestByText("""
                   from typing import TypedDict
                   class MyDict(TypedDict):
                       sub: 'SubDict'
                   class SubDict(TypedDict):
                       foo: str""");
  }

  // PY-43689
  public void testNoWarningOnUnionTypesUsingBinaryOrOperator() {
    doTestByText("""
                   from typing import TypedDict
                   class A(TypedDict):
                       a: int | str""");
  }

  // PY-53611
  public void testRequiredOutsideTypedDictItems() {
    doTestByText("""
                   from typing_extensions import Required, NotRequired
                   x: <warning descr="'Required' can be used only in a TypedDict definition">Required</warning>[int]
                   y = print(<warning descr="'NotRequired' can be used only in a TypedDict definition">NotRequired</warning>[int])
                   class B:
                       a: <warning descr="'Required' can be used only in a TypedDict definition">Required</warning>[int]""");
  }

  // PY-53611
  public void testNestedQualifiers() {
    doTestByText("""
                   from typing_extensions import TypedDict, Required, NotRequired, ReadOnly
                   
                   class A(TypedDict):
                       x: <warning descr="Required[] and NotRequired[] cannot be nested">Required[NotRequired[int]]</warning>
                       y: Required[int]
                       z: NotRequired[int]
                       a: <warning descr="Required[] and NotRequired[] cannot be nested">Required[ReadOnly[NotRequired[int]]]</warning>
                       b: <warning descr="Required[] and NotRequired[] cannot be nested">Required[Required[int]]</warning>
                       c: <warning descr="Required[] and NotRequired[] cannot be nested">Required[ReadOnly[Required[int]]]</warning>
                   
                   A = TypedDict('A', {'x': <warning descr="Required[] and NotRequired[] cannot be nested">Required[NotRequired[int]]</warning>, 'y': NotRequired[int]})
                   
                   class B(TypedDict):
                       x: <warning descr="ReadOnly[] cannot be nested">ReadOnly[ReadOnly[int]]</warning>
                       y: <warning descr="ReadOnly[] cannot be nested">ReadOnly[Required[ReadOnly[int]]]</warning>
                       z: ReadOnly[int]""");
  }

  // PY-53611
  public void testRequiredWithMultipleParameters() {
    doTestByText("""
                   from typing_extensions import TypedDict, Annotated, Required, NotRequired
                   Alternative = TypedDict("Alternative", {'x': Annotated[Required[int], "constraint"],
                                                           'y': NotRequired[<warning descr="'NotRequired' must have exactly one type argument">int, "constraint"</warning>]})""");
  }

  // PY-55092
  public void testGenericTypedDictNoWarnings() {
    doTestByText("""
                   from typing import TypeVar, TypedDict, Generic
                   T = TypeVar('T')
                   class Group(TypedDict, Generic[T]):
                       key: T
                       group: list[T]
                   group: Group[str] = {"key": 1, "group": ['one']}""");
  }

  // PY-64127, PY-64128
  public void testGenericTypedDictWithNewStyleSyntaxNoWarnings() {
    doTestByText("""
                   from typing import TypedDict
                   class Group[T](TypedDict):
                       key: T
                       group: list[T]
                   group: Group[str] = {"key": 1, "group": ['one']}""");
  }

  // PY-55044
  public void testTypedDictKwargsParameter() {
    doTestByText("""
                   from typing import TypedDict, Unpack

                   class Movie(TypedDict):
                       title: str
                       year: int

                   def foo(**x: Unpack[Movie]):
                       print(x[<warning descr="TypedDict \\"Movie\\" has no key 'nonexistent_key'">'nonexistent_key'</warning>])
                       print(x['title'])
                       print(x['year'])""");
  }

  // PY-73099
  public void testReadOnly() {
    doTestByText("""
                   from typing_extensions import TypedDict, Required, ReadOnly, Literal
                   
                   class Movie(TypedDict):
                       name: ReadOnly[Required[str]]
                       director: str
                   
                   m: Movie = {"name": "Blur"}
                   print(m["name"])
                   <warning descr="TypedDict key \\"name\\" is ReadOnly">m["name"]</warning> = "new name"
                   
                   def foo(k1: Literal["name", "director"], k2: Literal["director"]):
                       <warning descr="TypedDict key \\"name\\" is ReadOnly">m[k1]</warning> = "new name"
                       m[k2] = ""
                   """);
  }

  public void testOverridenReadOnly() {
    doTestByText("""
                   from typing_extensions import TypedDict, Required, ReadOnly
                   
                   class VisualArt(TypedDict):
                       name: ReadOnly[Required[str]]
                   
                   class Movie(VisualArt):
                       name: Required[str]
                   
                   m: Movie = {"name": "Blur"}
                   print(m["name"])
                   m["name"] = "new name"
                   """);
  }

  public void testChainedQualifiers() {
    doTestByText(
      """
        from typing_extensions import NotRequired, ReadOnly, TypedDict, Required

        class Movie(TypedDict):
            name: ReadOnly[str]
            year: ReadOnly[NotRequired[int | None]]


        movie = Movie(name="")
        """
    );
  }

  public void testUpdateReadOnlyMember() {
    doTestByText(
      """
        from typing import TypedDict, NotRequired
        from typing_extensions import ReadOnly
        
        class A(TypedDict):
            x: NotRequired[ReadOnly[int]]
            y: int
        
        a1: A = { "x": 1, "y": 1 }
        a2: A = { "x": 2, "y": 4 }
        a1.<warning descr="TypedDict key \\"x\\" is ReadOnly">update</warning>(a2)
        """
    );
  }

  public void testCorrectOverriden() {
    doTestByText(
      """
        from typing import TypedDict
        from typing_extensions import ReadOnly
        
        
        class NamedDict(TypedDict):
            name: ReadOnly[str]
        
        
        class Album(NamedDict):
            name: str
            year: int
        """
    );
  }

  // PY-76878
  public void testMixedMutableAndReadOnlyFieldsMerging313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      doTestByText("""
        from typing import TypedDict
        from typing_extensions import ReadOnly
        
        class TD_A1(TypedDict):
            x: int
            y: ReadOnly[int]
        
        class TD_A2(TypedDict):
            x: float
            y: ReadOnly[float]
        
        class TD_A<warning descr="Base classes define field 'x' incompatibly">(TD_A1, TD_A2)</warning>:
            pass
        """);
    });
  }

  // PY-76878
  public void testReadOnlyMutableMergeConflict313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      doTestByText("""
        from typing import TypedDict
        from typing_extensions import ReadOnly
        
        class Base1(TypedDict):
            name: ReadOnly[str]
        
        class Base2(TypedDict):
            name: str
        
        class Child<warning descr="Base classes define field 'name' incompatibly">(Base1, Base2)</warning>:
            pass
        """);
    });
  }

  // PY-76878
  public void testMutableTypeMismatchMerging313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      doTestByText("""
        from typing import TypedDict
        
        class Base1(TypedDict):
            items: list[int]
        
        class Base2(TypedDict):
            items: list[str]
        
        class Child<warning descr="Base classes define field 'items' incompatibly">(Base1, Base2)</warning>:
            pass
        """);
    });
  }

  // PY-76878
  public void testRequiredNotRequiredMergeConflict313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      doTestByText("""
        from typing import TypedDict, NotRequired
        
        class Base1(TypedDict, total=False):
            name: str  # NotRequired by default
        
        class Base2(TypedDict):
            name: str  # Required
        
        class Child<warning descr="Field \\"name\\" cannot be redefined as NotRequired">(Base1, Base2)</warning>:
            pass
        """);
    });
  }

  // PY-76878
  public void testReadOnlyOverrideMutableRequired313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      doTestByText("""
        from typing import TypedDict
        from typing_extensions import ReadOnly
        
        class Base(TypedDict):
            name: str
        
        class Child(Base):
            <warning descr="Cannot override mutable required field 'name' as read-only">name</warning>: ReadOnly[str]
        """);
    });
  }

  // PY-76878
  public void testOverrideMutableRequiredAsNotRequired313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      doTestByText("""
        from typing import TypedDict, NotRequired
        
        class Base(TypedDict):
            name: str
        
        class Child(Base):
            <warning descr="Cannot override mutable required field 'name' as not-required">name</warning>: NotRequired[str]
        """);
    });
  }

  // PY-76878
  public void testOverrideReadOnlyRequiredAsNotRequired313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      doTestByText("""
        from typing import TypedDict, NotRequired
        from typing_extensions import ReadOnly
        
        class Base(TypedDict):
            name: ReadOnly[str]
        
        class Child(Base):
            <warning descr="Cannot override read-only required field 'name' as not-required">name</warning>: ReadOnly[NotRequired[str]]
        """);
    });
  }

  // PY-76878
  public void testValidReadOnlyOverride313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      doTestByText("""
        from typing import TypedDict
        from typing_extensions import ReadOnly
        
        class Base(TypedDict):
            name: ReadOnly[str]
        
        class Child(Base):
            name: str  # OK - can override read-only as mutable
        """);
    });
  }

  // PY-76878
  public void testReadOnlyTypeMismatchOverride313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      doTestByText("""
        from typing import TypedDict
        from typing_extensions import ReadOnly
        
        class Base(TypedDict):
            name: ReadOnly[str]
        
        class Child(Base):
            <warning descr="Type 'int' is incompatible with expected type 'str'">name</warning>: ReadOnly[int]
        """);
    });
  }

  // PY-76878
  public void testMutableInvariantMismatchOverride313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> {
      doTestByText("""
        from typing import TypedDict
        
        class Base(TypedDict):
            items: list[int]
        
        class Child(Base):
            <warning descr="Type 'list[str]' is incompatible with expected type 'list[int]'">items</warning>: list[str]
        """);
    });
  }

  // PY-78174
  public void testRawDictTypeInferredForDictLiteral() {
    doTestByText("""
                   d = {'name': 'Matrix', 'year': 1999}
                   def f():
                       d['name'] = 1
                   """);
  }

  // PY-76878
  public void testReadonlyListInvarianceOverride313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText("""
    from typing import TypedDict
    from typing_extensions import ReadOnly
    
    class Animal: ...
    class Dog(Animal): ...

    class Base(TypedDict):
        items: ReadOnly[list[Animal]]

    class Child(Base):
        <warning descr="Type 'list[Dog]' is incompatible with expected type 'list[Animal]'">items</warning>: ReadOnly[list[Dog]]
    """));
  }

  // PY-76878
  public void testReadonlySequenceCovarianceOverride313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText("""
    from typing import TypedDict, Sequence
    from typing_extensions import ReadOnly

    class Animal: ...
    class Dog(Animal): ...

    class Base(TypedDict):
        items: ReadOnly[Sequence[Animal]]

    class Child(Base):
        items: ReadOnly[list[Dog]]  # OK
    """));
  }

  // PY-76878
  public void testReadonlyDictInvarianceVsMappingCovariance313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText("""
    from typing import TypedDict, Mapping
    from typing_extensions import ReadOnly

    class Animal: ...
    class Dog(Animal): ...

    class Base1(TypedDict):
        m: ReadOnly[dict[str, Animal]]

    class BadChild(Base1):
        <warning descr="Type 'dict[str, Dog]' is incompatible with expected type 'dict[str, Animal]'">m</warning>: ReadOnly[dict[str, Dog]]

    class Base2(TypedDict):
        m: ReadOnly[Mapping[str, Animal]]

    class GoodChild(Base2):
        m: ReadOnly[dict[str, Dog]]  # OK
    """));
  }

  // PY-76878
  public void testReadonlyCallableVarianceOverride313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText("""
    from typing import TypedDict, Callable
    from typing_extensions import ReadOnly

    class Animal: ...
    class Dog(Animal): ...

    class Base(TypedDict):
        f: ReadOnly[Callable[[Dog], Animal]]

    class GoodChild(Base):
        f: ReadOnly[Callable[[Animal], Dog]]  # OK

    class BadChild(Base):
        <warning descr="Type '(Dog) -> object' is incompatible with expected type '(Dog) -> Animal'">f</warning>: ReadOnly[Callable[[Dog], object]]
    """));
  }

  // PY-76878
  public void testReadonlyTupleCovarianceOverride313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText("""
    from typing import TypedDict
    from typing_extensions import ReadOnly

    class Animal: ...
    class Dog(Animal): ...

    class Base(TypedDict):
        t1: ReadOnly[tuple[Animal, Animal]]
        t2: ReadOnly[tuple[Animal, ...]]

    class Child(Base):
        t1: ReadOnly[tuple[Dog, Dog]]  # OK
        t2: ReadOnly[tuple[Dog, ...]]  # OK
    """));
  }

  // PY-76878
  public void testReadonlyMutableSequenceInvarianceOverride313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText("""
      from typing import TypedDict, MutableSequence
      from typing_extensions import ReadOnly

      class Animal: ...
      class Dog(Animal): ...

      class Base(TypedDict):
          items: ReadOnly[MutableSequence[Animal]]

      class Child(Base):
          <warning descr="Type 'MutableSequence[Dog]' is incompatible with expected type 'MutableSequence[Animal]'">items</warning>: ReadOnly[MutableSequence[Dog]]
      """));
  }

  // PY-76878
  public void testReadonlyMappingKeyInvarianceOverride313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText("""
      from typing import TypedDict, Mapping
      from typing_extensions import ReadOnly

      class Animal: ...
      class Dog(Animal): ...

      class Base(TypedDict):
          m: ReadOnly[Mapping[Animal, int]]

      class Child(Base):
          <warning descr="Type 'Mapping[Dog, int]' is incompatible with expected type 'Mapping[Animal, int]'">m</warning>: ReadOnly[Mapping[Dog, int]]
      """));
  }

  // PY-76878
  public void testReadonlySequenceVsCollectionCovarianceAndWidening313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText("""
      from typing import TypedDict, Sequence, Collection
      from typing_extensions import ReadOnly

      class Animal: ...
      class Dog(Animal): ...

      class BaseOk(TypedDict):
          items: ReadOnly[Collection[Animal]]

      class ChildOk(BaseOk):
          items: ReadOnly[Sequence[Dog]]  # OK: Sequence[Dog] <: Collection[Dog] <: Collection[Animal]

      class BaseBad(TypedDict):
          items: ReadOnly[Sequence[Dog]]

      class ChildBad(BaseBad):
          <warning descr="Type 'Collection[Animal]' is incompatible with expected type 'Sequence[Dog]'">items</warning>: ReadOnly[Collection[Animal]]
      """));
  }

  // PY-76878
  public void testReadonlyDequeInvarianceOverride313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText("""
      from typing import TypedDict, Deque
      from typing_extensions import ReadOnly

      class Animal: ...
      class Dog(Animal): ...

      class Base(TypedDict):
          dq: ReadOnly[Deque[Animal]]

      class Child(Base):
          <warning descr="Type 'deque[Dog]' is incompatible with expected type 'deque[Animal]'">dq</warning>: ReadOnly[Deque[Dog]]
      """));
  }

  // PY-76878
  public void testReadonlyMutableMappingInvarianceOverride313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText("""
      from typing import TypedDict, MutableMapping
      from typing_extensions import ReadOnly

      class Animal: ...
      class Dog(Animal): ...

      class Base(TypedDict):
          m: ReadOnly[MutableMapping[str, Animal]]

      class Child(Base):
          <warning descr="Type 'MutableMapping[str, Dog]' is incompatible with expected type 'MutableMapping[str, Animal]'">m</warning>: ReadOnly[MutableMapping[str, Dog]]
      """));
  }

  // PY-76878
  public void testReadonlyMappingFromDefaultDictIsOk313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText("""
      from typing import TypedDict, Mapping, DefaultDict
      from typing_extensions import ReadOnly

      class Animal: ...
      class Dog(Animal): ...

      class Base(TypedDict):
          m: ReadOnly[Mapping[str, Animal]]

      class Child(Base):
          m: ReadOnly[DefaultDict[str, Dog]]  # OK: DefaultDict[str, Dog] <: Mapping[str, Dog] <: Mapping[str, Animal]
      """));
  }

  // PY-76878
  public void testReadonlyIterableCovarianceAndWidening313() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText("""
      from typing import TypedDict, Iterable
      from typing_extensions import ReadOnly

      class Animal: ...
      class Dog(Animal): ...

      class BaseOk(TypedDict):
          it: ReadOnly[Iterable[Animal]]

      class ChildOk(BaseOk):
          it: ReadOnly[list[Dog]]  # OK: list[Dog] <: Iterable[Dog] <: Iterable[Animal]

      class BaseBad(TypedDict):
          it: ReadOnly[Iterable[Dog]]

      class ChildBad(BaseBad):
          <warning descr="Type 'Iterable[Animal]' is incompatible with expected type 'Iterable[Dog]'">it</warning>: ReadOnly[Iterable[Animal]]
      """));
  }

  // PY-85421
  public void testClosedParameterRequiresBooleanValue() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText(
      """
      from typing_extensions import TypedDict
      
      class IllegalTD(TypedDict, closed=<warning descr="Value of 'closed' must be True or False">42 == 42</warning>):
          name: str
      """
    ));
  }

  // PY-85421
  public void testClosedParameterInheritanceConstraints() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText(
      """
      from typing_extensions import TypedDict
  
      class ClosedBase(TypedDict, closed=True):
          name: str
     
      class IllegalChild1(ClosedBase, <warning descr="Cannot set 'closed=False' when superclass is 'closed=True'">closed=False</warning>):
          pass
      
      class ExtraItemsBase(TypedDict, extra_items=int):
          name: str
      
      class IllegalChild2(ExtraItemsBase, <warning descr="Cannot set 'closed=False' when superclass has 'extra_items'">closed=False</warning>):
          pass
      """
    ));
  }

  // PY-85421
  public void testClosedPropertyInheritedFromSuperclass() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText(
      """
      from typing_extensions import TypedDict
      
      # > If ``closed`` is not provided, the behavior is inherited from the superclass.
      # > If the superclass is TypedDict itself or the superclass does not have ``closed=True``
      # > or the ``extra_items`` parameter, the previous TypedDict behavior is preserved:
      # > arbitrary extra items are allowed. If the superclass has ``closed=True``, the
      # > child class is also closed.
      
      class BaseMovie(TypedDict, closed=True):
          name: str
      
      class MovieA(BaseMovie):
          pass
      
      class MovieB(BaseMovie, closed=True):
          pass
      
      class MovieC(MovieA):
          <warning descr="\\"MovieC\\" is a closed TypedDict; extra key \\"age\\" not allowed">age</warning>: int
      
      class MovieD(MovieB):
          <warning descr="\\"MovieD\\" is a closed TypedDict; extra key \\"age\\" not allowed">age</warning>: int
      """
    ));
  }

  // PY-85421
  public void testClosedTrueRequiresReadOnlyExtraItems() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText(
      """
      from typing import Never
      from typing_extensions import TypedDict, ReadOnly
      
      class ExtraItemsBase(TypedDict, extra_items=int):
          name: str
      
      class MovieES(TypedDict, extra_items=ReadOnly[str]):
          pass
      
      class MovieClosed(MovieES, closed=True):  # OK
          pass
      
      class MovieNever(MovieES, extra_items=Never):
          pass
      
      class IllegalCloseNonReadOnly(ExtraItemsBase, <warning descr="Cannot set 'closed=True' when superclass has non-readonly 'extra_items'">closed=True</warning>):
          pass
      """
    ));
  }

  // PY-85421
  public void testExtraItemsCannotBeRequiredOrNotRequired() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText(
      """
      from typing import NotRequired, Required
      from typing_extensions import TypedDict
      
      class IllegalExtraItemsTD(TypedDict, <warning descr="'extra_items' value cannot be 'Required[...]'">extra_items=Required[int]</warning>):
          name: str
      
      class AnotherIllegalExtraItemsTD(TypedDict, <warning descr="'extra_items' value cannot be 'NotRequired[...]'">extra_items=NotRequired[int]</warning>):
          name: str
      """
    ));
  }

  // PY-85421
  public void testExtraItemsImplicitlyNotRequired() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText(
      """
      from typing_extensions import TypedDict
      
      class MovieEI(TypedDict, extra_items=int):
          name: str
      
      def del_items(movie: MovieEI) -> None:
          del movie[<warning descr="Key 'name' of TypedDict 'MovieEI' cannot be deleted">"name"</warning>]
          del movie["year"]
      """
    ));
  }

  // PY-85421
  public void testExtraItemsRedeclarationRequiresReadOnlyInSuperclass() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText(
      """
      from typing import ReadOnly
      
      from typing_extensions import TypedDict
      
      class ReadOnlyBase(TypedDict, extra_items=ReadOnly[int]):
          pass
      
      class NonReadOnlyBase(TypedDict, extra_items=int):
          pass
      
      class ReadOnlyChild(ReadOnlyBase, extra_items=ReadOnly[bool]):
          pass
      
      class MutableChild(ReadOnlyBase, extra_items=int):
          pass
      
      class IllegalChild(NonReadOnlyBase, <warning descr="Cannot change 'extra_items' type unless it is 'ReadOnly' in the superclass">extra_items=int</warning>):
          pass
      """
    ));
  }

  // PY-85421
  public void testExtraItemsEnforcesNotRequiredForNewKeys() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText(
      """
      from typing_extensions import TypedDict, ReadOnly
      from typing import NotRequired
      
      class MovieBase2(TypedDict, extra_items=int | None):
          name: str
      
      class MovieRequiredYear(MovieBase2):
          <warning descr="Required key 'year' is not known to 'MovieBase2'">year</warning>: int | None
      
      class MovieNotRequiredYear(MovieBase2):
          <warning descr="Expected type 'int | None', got 'int' instead">year</warning>: NotRequired[int]
      
      class MovieWithYear(MovieBase2):  # OK
          year: NotRequired[int | None]
      
      class BookBase(TypedDict, extra_items=ReadOnly[int | None]):
          name: str
      
      class BookWithPublisher(BookBase):
          <warning descr="'str' is not assignable to 'int | None'">publisher</warning>: str
      """
    ));
  }

  // PY-85421
  public void testExtraItemsTypedDictAssignmentWithRequiredKeyMismatch() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText(
      """
      from typing_extensions import TypedDict
      from typing import NotRequired
      
      class MovieBase(TypedDict, extra_items=int | None):
          name: str
      
      class MovieDetails(TypedDict, extra_items=int | None):
          name: str
          year: NotRequired[int]
      
      class MovieDetails2(TypedDict, extra_items=int | None):
          name: str
          year: NotRequired[int | None]
      
      details: MovieDetails = {"name": "Kill Bill Vol. 1", "year": 2003}
      <warning descr="Expected type 'int | None', got 'int' instead">movie</warning>: MovieBase = details
      
      details2: MovieDetails2 = {"name": "Kill Bill Vol. 1", "year": 2003}
      movie2: MovieBase = details2  # OK
      """
    ));
  }

  // PY-85421
  public void testExtraItemsAssignmentRequiresNotRequiredKeys() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText(
      """
      from typing_extensions import TypedDict
      
      # > - If ``extra_items`` is not read-only:
      # >   - The key is non-required.
      # >   - The key's value type is :term:`consistent` with ``T``.
      # >   - The key is not in ``S``.
      
      class MovieBase(TypedDict, extra_items=int | None):
          name: str
      
      class MovieWithYear(TypedDict, extra_items=int | None):
          name: str
          year: int | None
      
      details: MovieWithYear = {"name": "Kill Bill Vol. 1", "year": 2003}
      <warning descr="'year' is not required in 'MovieBase', but it is required in 'MovieWithYear'">movie3</warning>: MovieBase = details
      """
    ));
  }

  // PY-85421
  public void testReadOnlyExtraItemsAllowNarrowerTypesInSubclass() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText(
      """
      from typing_extensions import TypedDict, ReadOnly
  
      # > When ``extra_items`` is specified to be read-only on a TypedDict type, it is
      # > possible for an item to have a :term:`narrower <narrow>` type than the
      # > ``extra_items`` argument.
  
      class MovieSI(TypedDict, extra_items=ReadOnly[str | int]):
          name: str
  
      class MovieDetails4(TypedDict, extra_items=int):
          name: str
          year: NotRequired[int]
  
      class MovieDetails5(TypedDict, extra_items=int):
          name: str
          actors: list[str]
  
      details4: MovieDetails4 = {"name": "Kill Bill Vol. 2", "year": 2004}
      details5: MovieDetails5 = {"name": "Kill Bill Vol. 2", "actors": ["Uma Thurman"]}
      movie4: MovieSI = details4  # OK. 'int' is assignable to 'str | int'.
      <warning descr="'list[str]' is not assignable to 'str | int'">movie5</warning>: MovieSI = details5
      """
    ));
  }

  // PY-85421
  public void testExtraItemsTypeCompatibilityCheckedInAssignment() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText(
      """
      from typing_extensions import TypedDict
  
      # > ``extra_items`` as a pseudo-item follows the same rules that other items have,
      # > so when both TypedDicts types specify ``extra_items``, this check is naturally
      # > enforced.
  
      class MovieExtraInt(TypedDict, extra_items=int):
          name: str
  
      class MovieExtraStr(TypedDict, extra_items=str):
          name: str
  
      extra_int: MovieExtraInt = {"name": "No Country for Old Men", "year": 2007}
      extra_str: MovieExtraStr = {"name": "No Country for Old Men", "description": ""}
      <warning descr="'str' is not assignable to extra items type 'int'">extra_int</warning> = extra_str
      <warning descr="'int' is not assignable to extra items type 'str'">extra_str</warning> = extra_int
      """
    ));
  }

  // PY-85421
  public void testNonClosedTypedDictHasImplicitReadOnlyObjectExtraItems() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText("""
    from typing_extensions import TypedDict

    class MovieExtraInt(TypedDict, extra_items=int):
        name: str

    class MovieNotClosed(TypedDict):
        name: str

    extra_int2: MovieExtraInt = {"name": "No Country for Old Men", "year": 2007}
    not_closed: MovieNotClosed = {"name": "No Country for Old Men"}
    <warning descr="Implicit ReadOnly[object] on 'MovieNotClosed' is not assignable to 'int'">extra_int2</warning> = not_closed
    not_closed = extra_int2  # OK
    """));
  }

  // PY-89006
  public void testDictModificationMethodsAllowedWhenAssignableToDict() {
    runWithLanguageLevel(LanguageLevel.PYTHON313, () -> doTestByText(
      """
      from typing import NotRequired
      from typing_extensions import TypedDict

      # > The TypedDict type is assignable to dict[str, VT] if all items satisfy:
      # > - The value type of the item is consistent with VT.
      # > - The item is not read-only.
      # > - The item is not required.
      # > In this case, methods that are previously unavailable on a TypedDict are allowed,
      # > with signatures matching dict[str, VT].

      class IntDict(TypedDict, extra_items=int):
          pass

      class IntDictWithNum(IntDict):
          num: NotRequired[int]

      m: IntDictWithNum = {"num": 1, "bar": 2}
      m.clear()
      m.popitem()
      m.get("bar")
      m.get("unknown_key")

      class RegularTypedDict(TypedDict):
          name: str
          year: int

      r = RegularTypedDict(name="Alien", year=1979)
      r.<warning descr="This operation might break TypedDict consistency">clear</warning>()
      r.<warning descr="This operation might break TypedDict consistency">popitem</warning>()
      """
    ));
  }

  // PY-76847
  public void testParamOverlapsWithTypedDict() {
    doTestByText("""
    from typing import TypedDict, Unpack
    class TD1(TypedDict):
        v1: Required[int]
        v2: NotRequired[str]
        v3: Required[str]
    
    def foo1(<warning descr="Named parameter 'v1' conflicts with a field of the TypedDict 'TD1'">v1: int</warning>, <warning descr="Named parameter 'v2' conflicts with a field of the TypedDict 'TD1'">v2: str</warning>, **kwargs: Unpack[TD1]) -> None:
        ...
    def foo1(v1: int, v2: str, /, **kwargs: Unpack[TD1]) -> None: # pos-only OK
        ...
    """);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTypedDictInspection.class;
  }
}