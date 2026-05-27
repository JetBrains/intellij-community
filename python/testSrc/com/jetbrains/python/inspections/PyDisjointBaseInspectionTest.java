// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class PyDisjointBaseInspectionTest extends PyInspectionTestCase {
  
  // PY-89915
  public void testFunction() {
    doTestByText("""
                   from typing_extensions import disjoint_base
                   
                   <warning descr="'disjoint_base' cannot be used on a function">@disjoint_base</warning>
                   def foo():
                       pass
                   """);
  }
  
  // PY-89915
  public void testTypedDict() {
    doTestByText("""
                   from typing_extensions import disjoint_base
                   from typing import TypedDict
                   
                   <warning descr="'disjoint_base' cannot be used on a TypedDict">@disjoint_base</warning>
                   class Wrong(TypedDict):
                       pass
                   
                   
                   """);
  }

  // PY-89915
  public void testProtocol() {
    doTestByText("""
                   from typing_extensions import disjoint_base
                   from typing import Protocol
                   
                   <warning descr="'disjoint_base' cannot be used on a protocol">@disjoint_base</warning>
                   class Wrong(Protocol):
                       pass
                   
                   """);
  }

  // PY-84827
  public void testExplicitlySlottedBasesConflict() {
    doTestByText("""
                   class A:
                       __slots__ = ('x',)

                   class B:
                       __slots__ = ('y',)

                   class <warning descr="'A' and 'B' have incompatible disjoint base classes">C</warning>(A, B):
                       pass""");
  }

  // PY-84827
  public void testSingleDisjointBaseDoesNotConflict() {
    doTestByText("""
                   class A:
                       __slots__ = ('x',)

                   class B:
                       pass

                   class C(A, B):
                       pass""");
  }

  // PY-84827
  public void testEmptySlotsAreIgnored() {
    doTestByText("""
                   class A:
                       __slots__ = ()

                   class B:
                       __slots__ = ()

                   class C(A, B):
                       pass""");
  }

  // PY-84827
  public void testSharedDisjointBaseDoesNotConflict() {
    doTestByText("""
                   class Base:
                       __slots__ = ('x',)

                   class A(Base):
                       pass

                   class B(Base):
                       pass

                   class C(A, B):
                       pass""");
  }

  // PY-84827
  public void testSubclassOfOtherBaseDoesNotConflict() {
    doTestByText("""
                   class A:
                       __slots__ = ('x',)

                   class B(A):
                       __slots__ = ('y',)

                   class C(A, B):
                       pass""");
  }

  // PY-84827
  public void testTypingExtensionsDisjointBaseDecoratorsConflict() {
    doTestByText("""
                   from typing_extensions import disjoint_base

                   @disjoint_base
                   class A:
                       pass

                   @disjoint_base
                   class B:
                       pass

                   class <warning descr="'A' and 'B' have incompatible disjoint base classes">C</warning>(A, B):
                       pass""");
  }

  // PY-84827
  public void testDisjointBaseDecoratorHierarchyDoesNotConflict() {
    runWithLanguageLevel(LanguageLevel.PYTHON315, () -> doTestByText("""
                   from typing import disjoint_base

                   @disjoint_base
                   class A:
                       pass

                   class B(A):
                       pass

                   class C(A, B):
                       pass"""));
  }

  // PY-84827
  public void testBuiltinDisjointBases() {
    doTestByText("""
                   class <warning descr="'int' and 'str' have incompatible disjoint base classes">C</warning>(int, str):
                       pass""");
  }

  // PY-84827
  public void testInheritedDisjointBasesConflict() {
    doTestByText("""
                   class Base1:
                       __slots__ = ('x',)

                   class Base2:
                       __slots__ = ('y',)

                   class A(Base1):
                       pass

                   class B(Base2):
                       pass

                   class <warning descr="'A' and 'B' have incompatible disjoint base classes">C</warning>(A, B):
                       pass""");
  }

  // PY-84827
  public void testSlottedDataclassesConflict() {
    doTestByText("""
                   from dataclasses import dataclass

                   @dataclass(slots=True)
                   class A:
                       x: int

                   @dataclass(slots=True)
                   class B:
                       y: str

                   class <warning descr="'A' and 'B' have incompatible disjoint base classes">C</warning>(A, B):
                       pass""");
  }

  // PY-84827
  public void testSlottedSubclassOfRegularBaseConflictsWithBuiltin() {
    doTestByText("""
                   class Base:
                       pass

                   class A(Base):
                       __slots__ = ('x',)

                   class <warning descr="'A' and 'str' have incompatible disjoint base classes">C</warning>(A, str):
                       pass""");
  }

  // PY-84827
  public void testSlottedDataclassSiblingsWithSharedBaseDoNotConflict() {
    doTestByText("""
                   from dataclasses import dataclass

                   @dataclass(slots=True)
                   class Base:
                       x: int

                   @dataclass(slots=True)
                   class A(Base):
                       pass

                   @dataclass(slots=True)
                   class B(Base):
                       pass

                   class C(A, B):
                       pass""");
  }

  // PY-84827
  public void testSlottedDataclassTransformClassesConflict() {
    doTestByText("""
                   from typing import dataclass_transform

                   @dataclass_transform()
                   def my_dataclass(**kwargs):
                       ...

                   @my_dataclass(slots=True)
                   class A:
                       x: int

                   @my_dataclass(slots=True)
                   class B:
                       y: str

                   class <warning descr="'A' and 'B' have incompatible disjoint base classes">C</warning>(A, B):
                       pass""");
  }

  // PY-84827
  public void testDataclassTransformWithoutSlotsDoesNotConflict() {
    doTestByText("""
                   from typing import dataclass_transform

                   @dataclass_transform()
                   def my_dataclass(**kwargs):
                       ...

                   @my_dataclass
                   class A:
                       x: int

                   @my_dataclass
                   class B:
                       y: str

                   class C(A, B):
                       pass""");
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyDisjointBaseInspection.class;
  }
}
