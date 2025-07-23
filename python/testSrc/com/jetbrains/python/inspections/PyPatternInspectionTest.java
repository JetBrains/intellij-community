// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class PyPatternInspectionTest extends PyInspectionTestCase {

  public void testNoMatchArgsPositional() {
    doTestByText("""
class C:
    def __init__(self, a, b):
        self.a = a
        self.b = b

obj = C(1, 2)

match obj:
    case C(<warning descr="Class C does not support pattern matching with positional arguments">1</warning>, <warning descr="Class C does not support pattern matching with positional arguments">2</warning>):
        pass
        """);
  }

  public void testTooManyPositional() {
    doTestByText("""
class Point:
    __match_args__ = ("x", "y")
    def __init__(self, x, y, z=0):
        self.x = x
        self.y = y
        self.z = z

def f(p):
    match p:
        case Point(x, y, <warning descr="Too many positional patterns, expected 2">z</warning>):
            pass
        """);
  }

  public void testPositionalKeywordConflict() {
    doTestByText("""
class Point:
    __match_args__ = ("x", "y")
    def __init__(self, x, y):
        self.x = x
        self.y = y

def f(p):
    match p:
        case Point(0, <warning descr="Attribute 'x' is already specified as positional pattern at position 1">x=0</warning>):
            pass
        """);
  }

  public void testInvalidMatchArgsType() {
    doTestByText("""
class D:
    __match_args__ = <warning descr="__match_args__ must be a tuple[str, ...]">42</warning>
        """);
  }

  public void testUnknownAttributeInMatchArgs() {
    doTestByText("""
class E:
    def __init__(self):
        self.x = 0
    __match_args__ = ("x", <warning descr="String 'y' does not refer to any instance attribute">'y'</warning>)
        """);
  }

  public void testSimplifyAsPattern() {
    doTestByText("""
x = []

match x:
    case <weak_warning descr="Pattern can be simplified">list() as xs</weak_warning>:
        pass
        """);
  }

  public void testKeywordsOnlyNoMatchArgsShouldNotWarn() {
    doTestByText("""
class C:
    def __init__(self, a, b):
        self.a = a
        self.b = b

def f(obj):
    match obj:
        case C(a=1, b=2):
            pass
    """);
  }

  public void testBuiltinClassPatternNoWarnings() {
    doTestByText("""
x = []

match x:
    case list():
        pass
    """);
  }

  public void testZeroLengthMatchArgsAndTooMany() {
    doTestByText("""
class Z:
    __match_args__ = ()

def f(z):
    match z:
        case Z():
            pass
        case Z(<warning descr=\"Too many positional patterns, expected 0\">1</warning>):
            pass
    """);
  }

  public void testConflictOnSecondPositional() {
    doTestByText("""
class Point:
    __match_args__ = ("x", "y")
    def __init__(self, x, y):
        self.x = x
        self.y = y

def f(p):
    match p:
        case Point(0, 0, <warning descr=\"Attribute 'y' is already specified as positional pattern at position 2\">y=0</warning>):
            pass
    """);
  }

  public void testMultipleExtraPositionals() {
    doTestByText("""
class A:
    __match_args__ = ("x",)
    def __init__(self, x):
        self.x = x

def f(a):
    match a:
        case A(x, <warning descr=\"Too many positional patterns, expected 1\">y</warning>, <warning descr=\"Too many positional patterns, expected 1\">z</warning>):
            pass
    """);
  }

  public void testNonStringElementInSequenceNoTypeError() {
    doTestByText("""
class D:
    def __init__(self):
        self.x = 0
    __match_args__ = ("x", 1)
    """);
  }

  public void testMatchArgsInheritedFromBase() {
    doTestByText("""
class Base:
    __match_args__ = ("x",)
    def __init__(self, x):
        self.x = x

class Derived(Base):
    __match_args__ = ("x",)

def f(d):
    match d:
        case Derived(1):
            pass
    """);
  }

  public void testSimplifyAsPatternNoTarget() {
    doTestByText("""
x = []

match x:
    case list():
        pass
    """);
  }

  public void testSimplifyAsPatternNonBuiltinNoWarning() {
    doTestByText("""
class C:
    pass

c = C()
match c:
    case C() as cc:
        pass
    """);
  }

  public void testKeywordNotInMatchArgsWithExtraPositionalNoConflict() {
    doTestByText("""
class P:
    __match_args__ = ("x", "y")
    def __init__(self, x, y):
        self.x = x
        self.y = y

def f(p):
    match p:
        case P(1, 2, <warning descr=\"Too many positional patterns, expected 2\">3</warning>, z=0):
            pass
    """);
  }

  public void testExactPositionalCountNoWarning() {
    doTestByText("""
class P:
    __match_args__ = ("x", "y")
    def __init__(self, x, y):
        self.x = x
        self.y = y

def f(p):
    match p:
        case P(1, 2):
            pass
    """);
  }

  public void testAllAttributesKnownInMatchArgs() {
    doTestByText("""
class E:
    def __init__(self):
        self.x = 0
        self.y = 0
    __match_args__ = ("x", "y")
    """);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyPatternInspection.class;
  }
}
