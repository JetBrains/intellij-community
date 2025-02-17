// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
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

  public void testFieldOverwrittenByInheritance() {
    doTestByText("""
                   from typing import TypedDict
                   class X(TypedDict):
                       y: int
                   class Y(TypedDict):
                       y: str
                   class XYZ<warning descr="Cannot overwrite TypedDict field 'y' while merging">(X, Y)</warning>:
                       <warning descr="Cannot overwrite TypedDict field">y</warning>: bool""");
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

  // PY-78174
  public void testRawDictTypeInferredForDictLiteral() {
    doTestByText("""
                   d = {'name': 'Matrix', 'year': 1999}
                   def f():
                       d['name'] = 1
                   """);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTypedDictInspection.class;
  }
}