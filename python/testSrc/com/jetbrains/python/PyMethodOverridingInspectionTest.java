/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python;

import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.allure.Subsystems;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.inspections.PyInspection;
import com.jetbrains.python.inspections.PyMethodOverridingInspection;
import org.jetbrains.annotations.NotNull;

@Subsystems.Inspections
@Layers.Functional
public class PyMethodOverridingInspectionTest extends PyInspectionTestCase {
  public void testArgsKwargsOverrideArg() {
    doTest();
  }

  public void testNotOverridingMethod() {
    doTest();
  }

  public void testInitNew() {
    doTest();
  }

  public void testArgsKwargsAsAllowAnything() {
    doTest();
  }

  // PY-1083
  public void testExtraKwargs() {
    doTest();
  }

  // PY-6700
  public void testBothArgsKwargs() {
    doTest();
  }

  // PY-6700
  public void testArgAndKwargs() {
    doTest();
  }

  // PY-7157
  public void testDefaultArgument() {
    doTest();
  }

  // PY-7162
  public void testLessArgumentsPlusDefaults() {
    doTest();
  }

  public void testLessParametersAndKwargs() {
    doTest();
  }

  // PY-7159
  public void testRequiredParameterAndKwargs() {
    doTest();
  }

  // PY-7725
  public void testPropertySetter() {
    doTest();
  }

  // PY-10229
  public void testInstanceCheck() {
    doTest();
  }

  // PY-23513
  public void testOverriddingAbstractStaticMethodWithExpandedArguments() {
    doTest();
  }

  // PY-32556
  public void testOverriddingWithDecorator() {
    doTestByText("""
                   class BaseClass():
                       def method(self, arg1):
                           pass

                   def my_decorator(func):
                       pass

                   class Child(BaseClass):
                       @my_decorator
                       def method(self, arg1, arg2):
                           pass
                   """);
  }

  // PY-28506
  public void testDunderPostInitInDataclassHierarchy() {
    doMultiFileTest();
  }

  // PY-35512
  public void testPositionalOnlyParameters() {
    doTest();
  }

  // PY-17828
  public void testDunderPrepare() {
    doTest();
  }

  // PY-33917
  public void testInitSubclass() {
    doTest();
  }

  // PY-87372
  public void testIncompatibleTypeInSignature() {
    doTestByText("""
                   class Base():
                       def method(self, arg1: int):
                           pass
                   
                   class Child(Base):
                       def method<warning descr="Signature of method 'Child.method()' does not match signature of the base method in class 'Base'">(self, arg1: str)</warning>:
                           pass
                   """);
  }

  // PY-87372
  public void testIncompatibleReturnType() {
    doTestByText("""
                   class Base():
                       def method(self, arg1: int) -> float:
                           pass
                   
                   class Child(Base):
                       def method(self, arg1: int) -> <warning descr="Return type of method 'Child.method()' does not match return type the base method in class 'Base'">str</warning>:
                           pass
                   
                   class Child1(Base):
                       def method(self, arg1: int) -> int:
                           pass
                   """);
  }

  // PY-87372
  public void testIncompatiblePosContainer() {
    doTestByText("""
                   class Base():
                       def method(self, *args: int):
                           pass
                   
                   class Child(Base):
                       def method<warning descr="Signature of method 'Child.method()' does not match signature of the base method in class 'Base'">(self, *args: str)</warning>:
                           pass
                   """);
  }

  // PY-87372
  public void testOptionalVsNonOptionalParamType() {
    doTestByText("""
                   class Base():
                       def method(self, a: int | None):
                           pass
                   
                   class Child(Base):
                       def method<warning descr="Signature of method 'Child.method()' does not match signature of the base method in class 'Base'">(self, a: int)</warning>:
                           pass
                   """);
  }

  // PY-87372
  public void testParamWithDefaultValue() {
    doTestByText("""
                   class Base:
                       def foo(self, x: int):
                           pass
                   
                   class Derived(Base):
                       def foo(self, x: int = 1):
                           pass
                   
                   class Base1:
                       def foo(self, x: int = 1):
                           pass
                   
                   class Derived1(Base1):
                       def foo<warning descr="Signature of method 'Derived1.foo()' does not match signature of the base method in class 'Base1'">(self, x: int)</warning>:
                           pass
                   """);
  }

  // PY-87372
  public void testCovariantReturnType() {
    doTestByText("""
                   class Base():
                       def method(self) -> "Base":
                           pass
                   
                   class Child(Base):
                       def method(self) -> "Child":
                           pass
                   """);
  }

  // PY-87372
  public void testClassmethodIncompatibility() {
    doTestByText("""
                   class Base():
                       @classmethod
                       def method(cls, x: int):
                           pass
                   
                   class Child(Base):
                       @classmethod
                       def method<warning descr="Signature of method 'Child.method()' does not match signature of the base method in class 'Base'">(cls, x: str)</warning>:
                           pass
                   """);
  }

  // PY-87372
  public void testNoReturnTypeAnnotationNotReported() {
    doTestByText("""
                   class Base():
                       def method(self, x: int): ...
                   
                   class Child(Base):
                       def method(self, x: int) -> str: ...
                   """);
  }

  // PY-76896
  public void testIncompatibleConstructorsWithOverrideDecorator() {
    doTestByText("""
                   from typing import override

                   class Base:
                       def __init__(self, x: int) -> None: ...
                       def __new__(cls, x: int) -> "Base": ...

                   class Child(Base):
                       @override
                       def __init__<warning descr="Signature of method 'Child.__init__()' does not match signature of the base method in class 'Base'">(self, x: str)</warning> -> None: ...
                       @override
                       def __new__<warning descr="Signature of method 'Child.__new__()' does not match signature of the base method in class 'Base'">(cls, x: str)</warning> -> "Child": ...
                   """);
  }

  // PY-76896
  public void testIncompatibleConstructorsWithoutOverrideDecorator() {
    doTestByText("""
                   class Base:
                       def __init__(self, x: int) -> None: ...
                       def __new__(cls, x: int) -> "Base": ...

                   class Child(Base):
                       def __init__(self, x: str) -> None: ...
                       def __new__(cls, x: str) -> "Child": ...
                   """);
  }

  // PY-76896
  // We only inspect `self`/`cls` if Base declares an explicit receiver contract.
  //
  // 1. Unconstrained Base e.g., Base.method(self) or Base.method(self: Self):
  //    Base defines no special receiver requirements beyond standard subclassing.
  //    - If Child uses standard `Self`: Valid by construction (`Child` is a subclass of `Base`).
  //    - If Child adds a custom annotation (`self: Custom`): Child restricts calling on its own type. 
  //      Whether Child satisfies `Custom` is a class-self-consistency check, not an override violation.
  //
  // 2. Constrained Base e.g., Base.method(self: MixinProtocol):
  //    Base explicitly restricts its receiver (e.g., in mixins). Child overrides MUST honor this 
  //    contract, so we keep the receiver in the override compatibility check.
  public void testExplicitReceiverDomainNotHonored() {
    doTestByText("""
                   from typing import Protocol

                   class HasValue(Protocol):
                       value: int

                   class Base:
                       def method(self: HasValue) -> None: ...

                   class Child(Base):
                       def method<warning descr="Signature of method 'Child.method()' does not match signature of the base method in class 'Base'">(self)</warning> -> None: ...
                   """);
  }

  // PY-76896
  public void testExplicitReceiverDomainHonored() {
    doTestByText("""
                   from typing import Protocol

                   class HasValue(Protocol):
                       value: int

                   class Base:
                       def method(self: HasValue) -> None: ...

                   class Child(Base):
                       value: int

                       def method(self) -> None: ...
                   """);
  }

  // PY-76896
  public void testChildOnlyNarrowingReceiverIsNotThisInspectionsJob() {
    doTestByText("""
                   from typing import Protocol

                   class HasValue(Protocol):
                       value: int

                   class Base:
                       def method(self) -> None: ...

                   class Child(Base):
                       def method(self: HasValue) -> None: ...
                   """);
  }

  // PY-76896
  public void testImplicitReceiverStillDroppedForGenericMetaclassCall() {
    doTestByText("""
                   from typing import TypeVar

                   T = TypeVar("T")

                   class Meta(type):
                       def __call__(cls: type[T], *args, **kwargs) -> T:
                           return type.__call__(cls, *args, **kwargs)
                   """);
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyMethodOverridingInspection.class;
  }

  @Override
  protected boolean isLowerCaseTestFile() {
    return false;
  }
}
