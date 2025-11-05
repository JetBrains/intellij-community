// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyProtocolInspectionTest extends PyInspectionTestCase {

  // PY-26628
  public void testValidProtocolSubclass() {
    doTest();
  }

  // PY-26628
  public void testIncompatibleProtocolSubclass() {
    doTest();
  }

  // PY-26628
  public void testProtocolBases() {
    doTest();
  }

  // PY-26628
  public void testNewTypeBasedOnProtocol() {
    doTest();
  }

  // PY-26628
  public void testInstanceAndClassChecksOnProtocol() {
    doTest();
  }

  // PY-26628
  public void testProtocolExtBases() {
    doTest();
  }

  // PY-61857
  public void testClassWithTypeParameterListNotReported() {
    runWithLanguageLevel(LanguageLevel.PYTHON312, () -> {
      super.doTest();
    });
  }

  // PY-76903
  public void testProtocolCannotBeInstantiated() {
    doTestByText("""
                   from typing import Protocol
                   
                   class P(Protocol):
                           ...
                   
                   p = <warning descr="Cannot instantiate protocol class 'P'">P()</warning>
                   """);
  }

  // PY-76903
  public void testProtocolSubclassCanBeInstantiated() {
    doTestByText("""
                   from typing import Protocol
                   
                   class P(Protocol):
                       ...
                   
                   
                   class C(P):
                       ...
                   
                   c = C()
                   """);
  }

  // PY-76903
  public void testProtocolFunctionCall() {
    doTestByText("""
                   from typing import Protocol
                   
                   class P(Protocol):
                       def foo(self) -> None: ...
                   
                   
                   def f(p: P):
                       p.foo()
                   """);
  }

  // PY-76903
  public void testProtocolTypeButConcreteValue() {
    doTestByText("""
                   from typing import Protocol, reveal_type
                   
                   class P(Protocol):
                       def foo(self) -> None: ...
                   
                   
                   class I(P):
                       def foo(self) -> None: ...
                   
                   
                   def f() -> type[P]:
                       return I
                   
                   t = f()
                   t()
                   """);
  }

  @Override
  protected void doTest() {
    runWithLanguageLevel(LanguageLevel.PYTHON37, () -> super.doTest());
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyProtocolInspection.class;
  }
}
