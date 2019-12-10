// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyTypedDictInspectionTest extends PyInspectionTestCase {

  public void testClassBasedSyntax() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON38,
      () -> doTestByText("from typing import TypedDict\n" +
                         "\n" +
                         "class Employee(TypedDict):\n" +
                         "    name: str\n" +
                         "    id: int\n" +
                         "\n" +
                         "\n" +
                         "class Employee2(Employee, total=False):\n" +
                         "    director: str\n" +
                         "\n" +
                         "\n" +
                         "em = Employee2(name='John Dorian', id=1234)\n" +
                         "em['director'] = 'Robert Kelso'\n" +
                         "em[<warning descr=\"TypedDict 'Employee2' cannot have key 'slave'\">'slave'</warning>] = 'Doug Murphy'\n"));
  }

  public void testAlternativeSyntax() {
    doTestByText("from typing import TypedDict\n" +
                 "\n" +
                 "Movie = TypedDict('Movie', {'name': str, 'year': int}, total=False)\n" +
                 "movie = Movie(name='Blade Runner')\n" +
                 "movie[<warning descr=\"TypedDict 'Movie' cannot have key 'based_on_book'\">'based_on_book'</warning>] = True\n" +
                 "movie['year'] = 1984");
  }

  public void testMetaclass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON38,
      () -> doTestByText("from typing import TypedDict\n" +
                         "\n" +
                         "class Movie(TypedDict, <warning descr=\"Specifying a metaclass is not allowed in TypedDict\">metaclass=Meta</warning>):\n" +
                         "    name: str"));
  }

  public void testExtraClassDeclarations() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON38,
      () -> doTestByText("from typing import TypedDict\n" +
                         "class Movie(TypedDict):\n" +
                         "    name: str\n" +
                         "    def <weak_warning descr=\"Invalid statement in TypedDict definition; expected 'field_name: field_type'\">my_method</weak_warning>(self):\n" +
                         "        pass\n" +
                         "    class <weak_warning descr=\"Invalid statement in TypedDict definition; expected 'field_name: field_type'\">Horror</weak_warning>:\n" +
                         "        def __init__(self):\n" +
                         "            ..."));
  }

  public void testInitializer() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON38,
      () -> doTestByText("from typing import TypedDict\n" +
                         "class Movie(TypedDict):\n" +
                         "    name: str\n" +
                         "    year: int = <warning descr=\"Right hand side values are not supported in TypedDict\">42</warning>"));
  }

  public void testPass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON38,
      () -> doTestByText("from typing import TypedDict\n" +
                         "class Movie(TypedDict):\n" +
                         "    <weak_warning descr=\"Invalid statement in TypedDict definition; expected 'field_name: field_type'\">...</weak_warning>\n" +
                         "class HorrorMovie(TypedDict):\n" +
                         "    pass"));
  }

  public void testNonTypedDictAsSuperclass() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON38,
      () -> doTestByText("from typing import TypedDict, NamedTuple\n" +
                         "class Bastard:\n" +
                         "    pass\n" +
                         "class X(TypedDict):\n" +
                         "    x: int\n" +
                         "class Y(TypedDict):\n" +
                         "    y: str\n" +
                         "class XYZ(X, <warning descr=\"TypedDict cannot inherit from a non-TypedDict base class\">Bastard</warning>):\n" +
                         "    z: bool\n" +
                         "class MyNT(NamedTuple):\n" +
                         "    a: str"));
  }

  public void testIncorrectTotalityValue() {
    runWithLanguageLevel(
      LanguageLevel.PYTHON38,
      () -> doTestByText("from typing import TypedDict\n" +
                         "class X(TypedDict, total=<warning descr=\"Value of 'total' must be True or False\">1</warning>):\n" +
                         "    x: int\n" +
                         "Movie = TypedDict(\"Movie\", {}, total=<warning descr=\"Value of 'total' must be True or False\">2</warning>)"));
  }

  public void testNameAndVariableNameDoNotMatch() {
    doTestByText("from typing import TypedDict\n" +
                 "Movie2 = TypedDict(<warning descr=\"First argument has to match the variable name\">'Movie'</warning>, {'name': str, 'year': int}, total=False)");
  }

  public void testKeyTypes() {
    doTestByText("from typing import TypedDict, Any\n" +
                 "Movie = TypedDict('Movie', {'name': Any, 'year': <warning descr=\"Value must be a type\">2</warning>}, total=False)");
  }

  public void testIncorrectKeyValue() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, () ->
      doTestByText("from typing import TypedDict\n" +
                   "class A(TypedDict):\n" +
                   "    x: int\n" +
                   "a: A\n" +
                   "a = A(x=2)\n" +
                   "a['x'] = 1\n" +
                   "a[<warning descr=\"TypedDict 'A' cannot have key 'new'\">'new'</warning>] = 10\n" +
                   "a[<warning descr=\"TypedDict key type must be string\">1</warning>] = 2"));
  }

  public void testFinalKey() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, () ->
      doTestByText("from typing import TypedDict, Final\n" +
                   "class Movie(TypedDict):\n" +
                   "    name: str\n" +
                   "    year: int\n" +
                   "YEAR: Final = 'year'\n" +
                   "m = Movie(name='Alien', year=1979)\n" +
                   "years_since_epoch = m[YEAR] - 1970"));
  }

  public void testStringVariableAsKey() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, () ->
      doTestByText("from typing import TypedDict, Final\n" +
                   "class Movie(TypedDict):\n" +
                   "    name: str\n" +
                   "    year: int\n" +
                   "year = 'year'\n" +
                   "year2 = year\n" +
                   "m = Movie(name='Alien', year=1979)\n" +
                   "years_since_epoch = m[year2] - 1970\n" +
                   "year = 42\n" +
                   "print(m[<warning descr=\"TypedDict key type must be string\">year</warning>])"));
  }

  public void testDelStatement() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, () ->
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
                   "del m[<warning descr=\"Key 'year' of TypedDict 'HorrorMovie' cannot be deleted\">year2</warning>], m['based_on_book']"));
  }

  public void testDictModificationMethods() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, () ->
      doTestByText("from typing import TypedDict\n" +
                   "class Movie(TypedDict):\n" +
                   "    name: str\n" +
                   "    year: int\n" +
                   "class Horror(Movie, total=False):\n" +
                   "    based_on_book: bool\n" +
                   "m = Horror(name='Alien', year=1979)\n" +
                   "m.<weak_warning descr=\"This operation might break TypedDict consistency\">clear</weak_warning>()\n" +
                   "name = 'name'\n" +
                   "m.pop('based_on_book')\n" +
                   "m.<warning descr=\"Key 'year' of TypedDict 'Horror' cannot be deleted\">pop</warning>('year')\n" +
                   "m.<weak_warning descr=\"This operation might break TypedDict consistency\">popitem</weak_warning>()\n" +
                   "m.setdefault('based_on_book', <warning descr=\"Expected type 'bool', got 'int' instead\">42</warning>)"));
  }

  public void testUpdateMethods() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, () ->
      doTestByText("from typing import TypedDict, Optional\n" +
                   "class Movie(TypedDict):\n" +
                   "    name: str\n" +
                   "    year: Optional[int]\n" +
                   "class Horror(Movie, total=False):\n" +
                   "    based_on_book: bool\n" +
                   "m = Horror(name='Alien', year=1979)\n" +
                   "d={'name':'Garden State', 'year':2004}\n" +
                   "m.update(d)\n" +
                   "m.update({'name':'Garden State', 'year':<warning descr=\"Expected type 'Optional[int]', got 'str' instead\">'2004'</warning>, <warning descr=\"TypedDict Horror cannot have key based_on\">'based_on'</warning>: 'book'})\n" +
                   "m.update(name=<warning descr=\"Expected type 'str', got 'int' instead\">1984</warning>, year=1984, based_on_book=<warning descr=\"Expected type 'bool', got 'str' instead\">'yes'</warning>)\n" +
                   "m.update([('name',<warning descr=\"Expected type 'str', got 'int' instead\">1984</warning>), ('year',None)])"));
  }

  public void testDocString() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, () ->
      doTestByText("from typing import TypedDict\n" +
                   "class Cinema(TypedDict):\n" +
                   "    \"\"\"\n" +
                   "        It's doc string\n" +
                   "    \"\"\""));
  }

  public void testFieldOverwrittenByInheritance() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, () ->
      doTestByText("from typing import TypedDict\n" +
                   "class X(TypedDict):\n" +
                   "    y: int\n" +
                   "class Y(TypedDict):\n" +
                   "    y: str\n" +
                   "class XYZ<warning descr=\"Cannot overwrite TypedDict field 'y' while merging\">(X, Y)</warning>:\n" +
                   "    <warning descr=\"Cannot overwrite TypedDict field\">y</warning>: bool"));
  }

  public void testIncorrectTypedDictArguments() {
    runWithLanguageLevel(LanguageLevel.PYTHON38, () ->
      doTestByText("from typing import TypedDict\n" +
                   "c = TypedDict(\"c\", [1, 2, 3])"));
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyTypedDictInspection.class;
  }
}