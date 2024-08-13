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
                       def <weak_warning descr="Invalid statement in TypedDict definition; expected 'field_name: field_type'">my_method</weak_warning>(self):
                           pass
                       class <weak_warning descr="Invalid statement in TypedDict definition; expected 'field_name: field_type'">Horror</weak_warning>:
                           def __init__(self):
                               ...""");
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
                       <weak_warning descr="Invalid statement in TypedDict definition; expected 'field_name: field_type'">...</weak_warning>
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
                   from typing import TypedDict
                   class Movie(TypedDict):
                       name: str
                       year: int
                   class HorrorMovie(Movie, total=False):
                       based_on_book: bool
                   year = 'year'
                   year2 = year
                   m = HorrorMovie(name='Alien', year=1979)
                   del (m['based_on_book'], m[<warning descr="Key 'name' of TypedDict 'HorrorMovie' cannot be deleted">'name'</warning>])
                   del m[<warning descr="Key 'year' of TypedDict 'HorrorMovie' cannot be deleted">year2</warning>], m['based_on_book']""");
  }

  public void testDictModificationMethods() {
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
                   m.<weak_warning descr="This operation might break TypedDict consistency">popitem</weak_warning>()
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
    doTestByText("from typing import TypedDict\n" +
                 "c = TypedDict(\"c\", [1, 2, 3])");
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
  public void testRequiredNotRequiredAtTheSameTime() {
    doTestByText("""
                   from typing_extensions import TypedDict, Required, NotRequired
                   class A(TypedDict):
                       x: <warning descr="Key cannot be required and not required at the same time">Required[NotRequired[int]]</warning>
                       y: Required[int]
                       z: NotRequired[int]
                   A = TypedDict('A', {'x': <warning descr="Key cannot be required and not required at the same time">Required[NotRequired[int]]</warning>, 'y': NotRequired[int]})""");
  }

  public void testRequiredNotRequiredWithReadOnly() {
    doTestByText("""
                   from typing_extensions import TypedDict, Required, NotRequired, ReadOnly
                   class A(TypedDict):
                       x: <warning descr="Key cannot be required and not required at the same time">Required[ReadOnly[NotRequired[int]]]</warning>
                   """);
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
                   from typing_extensions import TypedDict, Required, ReadOnly
                   
                   class Movie(TypedDict):
                       name: ReadOnly[Required[str]]
                   
                   m: Movie = {"name": "Blur"}
                   print(m["name"])
                   <warning descr="TypedDict key \\"name\\" is ReadOnly">m["name"]</warning> = "new name"
                   """);
  }

  public void testOverridenReadOnly() {
    doTestByText("""
                   from typing_extensions import TypedDict, Required, ReadOnly
                   
                   class VisualArt(TypedDict):
                       name: ReadOnly[Required[str]]
                   
                   class Movie(VisualArt):
                       <warning descr="Cannot overwrite TypedDict field">name</warning>: Required[str]
                   
                   m: Movie = {"name": "Blur"}
                   print(m["name"])
                   m["name"] = "new name"
                   """);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTypedDictInspection.class;
  }
}