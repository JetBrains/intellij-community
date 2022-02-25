// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class PyTypedDictInspectionTest extends PyInspectionTestCase {

  public void testUnknownKey() {
    doTestByText("from typing import TypedDict\n" +
                 "class Movie(TypedDict, total=False):\n" +
                 "    name: str\n" +
                 "    year: int\n" +
                 "Movie2 = TypedDict('Movie2', {'name': str, 'year': int}, total=False)\n" +
                 "movie = Movie2(name='Blade Runner')\n" +
                 "movie2 = Movie2(name='Blade Runner')\n" +
                 "movie['year'] = 42\n" +
                 "movie2['year'] = 42\n" +
                 "movie[<warning descr=\"TypedDict \\\"Movie2\\\" has no key 'id'\">'id'</warning>] = 43\n" +
                 "movie2[<warning descr=\"TypedDict \\\"Movie2\\\" has no key 'id'\">'id'</warning>] = 43\n");
  }

  public void testMetaclass() {
    doTestByText("from typing import TypedDict\n" +
                 "\n" +
                 "class Movie(TypedDict, <warning descr=\"Specifying a metaclass is not allowed in TypedDict\">metaclass=Meta</warning>):\n" +
                 "    name: str");
  }

  public void testExtraClassDeclarations() {
    doTestByText("from typing import TypedDict\n" +
                 "class Movie(TypedDict):\n" +
                 "    name: str\n" +
                 "    def <weak_warning descr=\"Invalid statement in TypedDict definition; expected 'field_name: field_type'\">my_method</weak_warning>(self):\n" +
                 "        pass\n" +
                 "    class <weak_warning descr=\"Invalid statement in TypedDict definition; expected 'field_name: field_type'\">Horror</weak_warning>:\n" +
                 "        def __init__(self):\n" +
                 "            ...");
  }

  public void testInitializer() {
    doTestByText("from typing import TypedDict\n" +
                 "class Movie(TypedDict):\n" +
                 "    name: str\n" +
                 "    year: int = <warning descr=\"Right-hand side values are not supported in TypedDict\">42</warning>");
  }

  public void testPass() {
    doTestByText("from typing import TypedDict\n" +
                 "class Movie(TypedDict):\n" +
                 "    <weak_warning descr=\"Invalid statement in TypedDict definition; expected 'field_name: field_type'\">...</weak_warning>\n" +
                 "class HorrorMovie(TypedDict):\n" +
                 "    pass");
  }

  public void testNonTypedDictAsSuperclass() {
    doTestByText("from typing import TypedDict, NamedTuple\n" +
                 "class Bastard:\n" +
                 "    pass\n" +
                 "class X(TypedDict):\n" +
                 "    x: int\n" +
                 "class Y(TypedDict):\n" +
                 "    y: str\n" +
                 "class XYZ(X, <warning descr=\"TypedDict cannot inherit from a non-TypedDict base class\">Bastard</warning>):\n" +
                 "    z: bool\n" +
                 "class MyNT(NamedTuple):\n" +
                 "    a: str");
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
    doTestByText("from typing import TypedDict, Any, Optional\n" +
                 "class Movie(TypedDict):\n" +
                 "    name: Optional[int]\n" +
                 "    smth: type\n" +
                 "    smthElse: Any\n" +
                 "    year: <weak_warning descr=\"Value must be a type\">2</weak_warning>");
  }

  public void testFinalKey() {
    doTestByText("from typing import TypedDict, Final\n" +
                 "class Movie(TypedDict):\n" +
                 "    name: str\n" +
                 "    year: int\n" +
                 "YEAR: Final = 'year'\n" +
                 "m = Movie(name='Alien', year=1979)\n" +
                 "years_since_epoch = m[YEAR] - 1970");
  }

  public void testStringVariableAsKey() {
    doTestByText("from typing import TypedDict, Final\n" +
                 "class Movie(TypedDict):\n" +
                 "    name: str\n" +
                 "    year: int\n" +
                 "year = 'year'\n" +
                 "year2 = year\n" +
                 "m = Movie(name='Alien', year=1979)\n" +
                 "years_since_epoch = m[year2] - 1970\n" +
                 "year = 42\n" +
                 "print(m[<warning descr=\"TypedDict key must be a string literal; expected one of ('name', 'year')\">year</warning>])");
  }

  public void testDelStatement() {
    doTestByText("from typing import TypedDict\n" +
                 "class Movie(TypedDict):\n" +
                 "    name: str\n" +
                 "    year: int\n" +
                 "class HorrorMovie(Movie, total=False):\n" +
                 "    based_on_book: bool\n" +
                 "year = 'year'\n" +
                 "year2 = year\n" +
                 "m = HorrorMovie(name='Alien', year=1979)\n" +
                 "del (m['based_on_book'], m[<warning descr=\"Key 'name' of TypedDict 'HorrorMovie' cannot be deleted\">'name'</warning>])\n" +
                 "del m[<warning descr=\"Key 'year' of TypedDict 'HorrorMovie' cannot be deleted\">year2</warning>], m['based_on_book']");
  }

  public void testDictModificationMethods() {
    doTestByText("from typing import TypedDict\n" +
                 "class Movie(TypedDict):\n" +
                 "    name: str\n" +
                 "    year: int\n" +
                 "class Horror(Movie, total=False):\n" +
                 "    based_on_book: bool\n" +
                 "m = Horror(name='Alien', year=1979)\n" +
                 "m.<warning descr=\"This operation might break TypedDict consistency\">clear</warning>()\n" +
                 "name = 'name'\n" +
                 "m.pop('based_on_book')\n" +
                 "m.<warning descr=\"Key 'year' of TypedDict 'Horror' cannot be deleted\">pop</warning>('year')\n" +
                 "m.<weak_warning descr=\"This operation might break TypedDict consistency\">popitem</weak_warning>()\n" +
                 "m.setdefault('based_on_book', <warning descr=\"Expected type 'bool', got 'int' instead\">42</warning>)");
  }

  public void testUpdateMethods() {
    doTestByText("from typing import TypedDict, Optional\n" +
                 "class Movie(TypedDict):\n" +
                 "    name: str\n" +
                 "    year: Optional[int]\n" +
                 "class Horror(Movie, total=False):\n" +
                 "    based_on_book: bool\n" +
                 "m = Horror(name='Alien', year=1979)\n" +
                 "d={'name':'Garden State', 'year':2004}\n" +
                 "m.update(d)\n" +
                 "m.update({'name':'Garden State', 'year':<warning descr=\"Expected type 'int | None', got 'str' instead\">'2004'</warning>, <warning descr=\"TypedDict \\\"Horror\\\" cannot have key 'based_on'\">'based_on'</warning>: 'book'})\n" +
                 "m.update(name=<warning descr=\"Expected type 'str', got 'int' instead\">1984</warning>, year=1984, based_on_book=<warning descr=\"Expected type 'bool', got 'str' instead\">'yes'</warning>)\n" +
                 "m.update([('name',<warning descr=\"Expected type 'str', got 'int' instead\">1984</warning>), ('year',None)])");
  }

  public void testDocString() {
    doTestByText("from typing import TypedDict\n" +
                 "class Cinema(TypedDict):\n" +
                 "    \"\"\"\n" +
                 "        It's doc string\n" +
                 "    \"\"\"");
  }

  public void testFieldOverwrittenByInheritance() {
    doTestByText("from typing import TypedDict\n" +
                 "class X(TypedDict):\n" +
                 "    y: int\n" +
                 "class Y(TypedDict):\n" +
                 "    y: str\n" +
                 "class XYZ<warning descr=\"Cannot overwrite TypedDict field 'y' while merging\">(X, Y)</warning>:\n" +
                 "    <warning descr=\"Cannot overwrite TypedDict field\">y</warning>: bool");
  }

  public void testIncorrectTypedDictArguments() {
    doTestByText("from typing import TypedDict\n" +
                 "c = TypedDict(\"c\", [1, 2, 3])");
  }

  public void testTypedDictNonStringKey() {
    doTestByText("from typing import TypedDict\n" +
                 "Movie = TypedDict('Movie', {'name': str, 'year': int}, total=False)\n" +
                 "class Movie2(TypedDict, total=False):\n" +
                 "    name: str\n" +
                 "    year: int\n" +
                 "movie = Movie()\n" +
                 "movie2 = Movie2()\n" +
                 "movie[<warning descr=\"TypedDict key must be a string literal; expected one of ('name', 'year')\">2</warning>]\n" +
                 "movie2[<warning descr=\"TypedDict key must be a string literal; expected one of ('name', 'year')\">2</warning>]");
  }

  public void testTypedDictKeyValueWrite() {
    doTestByText("from typing import TypedDict\n" +
                 "Movie = TypedDict('Movie', {'name': str, 'year': int}, total=False)\n" +
                 "class Movie2(TypedDict, total=False):\n" +
                 "    name: str\n" +
                 "    year: int\n" +
                 "movie = Movie()\n" +
                 "movie2 = Movie2()\n" +
                 "movie['year'], movie2['year'] = <warning descr=\"Expected type 'int', got 'str' instead\">'1984'</warning>, " +
                 "<warning descr=\"Expected type 'int', got 'str' instead\">'1984'</warning>\n");
  }

  public void testTypedDictKeyValueWriteToTypedDictField() {
    doTestByText("from typing import TypedDict\n" +
                 "X = TypedDict('X', {'d': dict}, total=False)\n" +
                 "class X2(TypedDict, total=False):\n" +
                 "    d: dict\n" +
                 "x = X()\n" +
                 "x2 = X2()\n" +
                 "x['d']['k'], x2['d']['k'] = 'v', 'v'");
  }

  public void testIncorrectTotalityValue() {
    doTestByText("from typing import TypedDict\n" +
                 "class X(TypedDict, total=<warning descr=\"Value of 'total' must be True or False\">1</warning>):\n" +
                 "    x: int\n");
  }

  public void testIncorrectTotalityValueAlternativeSyntax() {
    doTestByText("from typing import TypedDict\n" +
                 "X = TypedDict('X', {'x': int}, total=<warning descr=\"Value of 'total' must be True or False\">1</warning>)");
  }

  public void testGetWithIncorrectKeyType() {
    doTestByText("from typing import TypedDict\n" +
                 "class X(TypedDict):\n" +
                 "    x: int\n" +
                 "x = X()\n" +
                 "x.get(<warning descr=\"Key should be string\">42</warning>, 67)");
  }

  public void testGetWithIncorrectKeyValue() {
    doTestByText("from typing import TypedDict\n" +
                 "class X(TypedDict):\n" +
                 "    x: int\n" +
                 "x = X()\n" +
                 "x.get(<warning descr=\"TypedDict \\\"X\\\" has no key 'y'\">'y'</warning>, 67)\n" +
                 "x.get('x', '')");
  }

  // PY-39404
  public void testImportedTypedDict() {
    doMultiFileTest();
  }

  // PY-40906
  public void testLiteralAsTypedDictKey() {
    doTestByText("from typing import TypedDict, Literal, Union\n" +
                 "class Movie(TypedDict):\n" +
                 "    name: str\n" +
                 "    year: int\n" +
                 "def get_value(movie: Movie, key: Literal['year', 'name']) -> Union[int, str]:\n" +
                 "    return movie[key]\n" +
                 "def get_value(movie: Movie, key: Literal['name']) -> Union[int, str]:\n" +
                 "    return movie[key]\n" +
                 "def get_value(movie: Movie, key: Literal['name1']) -> Union[int, str]:\n" +
                 "    return movie[<warning descr=\"TypedDict \\\"Movie\\\" has no key 'name1'\">key</warning>]\n" +
                 "def get_value(movie: Movie, key: Literal['year', 42]) -> Union[int, str]:\n" +
                 "    return movie[<warning descr=\"TypedDict key must be a string literal; expected one of ('name', 'year')\">key</warning>]\n" +
                 "def get_value(movie: Movie, key: Literal['year', 'name1', '42']) -> Union[int, str]:\n" +
                 "    return movie[<warning descr=\"TypedDict \\\"Movie\\\" has no keys ('name1', '42')\">key</warning>]\n" +
                 "def get_value(movie: Movie, key: Literal[42]) -> Union[int, str]:\n" +
                 "    return movie[<warning descr=\"TypedDict key must be a string literal; expected one of ('name', 'year')\">key</warning>]");
  }

  // PY-44714
  public void testNoneAsType() {
    doTestByText("from typing import TypedDict\n" +
                 "class X(TypedDict):\n" +
                 "    n: None\n" +
                 "Y = TypedDict('Y', {'n': None})\n");
  }

  // PY-50025
  public void testNoWarningInSubscriptionExpressionOnDictLiteral() {
    doTestByText("ROMAN = {'I': 1, 'V': 5, 'X': 10, 'L': 50, 'C': 100, 'D': 500, 'M': 1000}\n" +
                 "class Solution:\n" +
                 "    def romanToInt(self, roman: str) -> int:\n" +
                 "        result = 0\n" +
                 "        for letter in roman:\n" +
                 "            int_value = ROMAN[letter]\n" +
                 "        return 42");
  }

  // PY-50113
  public void testNoWarningInGetMethodOnDictLiteral() {
    doTestByText("def baz(param: str):\n" +
                 "    {'a': 1}.get(param)");
  }

  // PY-43689
  public void testNoWarningOnTypesUsingForwardReferences() {
    doTestByText("from typing import TypedDict\n" +
                 "class MyDict(TypedDict):\n" +
                 "    sub: 'SubDict'\n" +
                 "class SubDict(TypedDict):\n" +
                 "    foo: str");
  }

  // PY-43689
  public void testNoWarningOnUnionTypesUsingBinaryOrOperator() {
    doTestByText("from typing import TypedDict\n" +
                 "class A(TypedDict):\n" +
                 "    a: int | str");
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTypedDictInspection.class;
  }
}