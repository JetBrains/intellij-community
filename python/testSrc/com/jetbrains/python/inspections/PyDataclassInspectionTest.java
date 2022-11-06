/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.inspections;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class PyDataclassInspectionTest extends PyInspectionTestCase {

  // PY-27398
  public void testMutatingFrozen() {
    doTest();
  }

  // PY-26354
  public void testMutatingFrozenAttrs() {
    doTest();
  }

  // PY-28506
  public void testFrozenInheritance() {
    doTest();
  }

  // PY-28506, PY-31762
  public void testMutatingFrozenInInheritance() {
    doTest();
  }

  // PY-28506, PY-31762
  public void testMutatingFrozenInMixedInheritance() {
    doTest();
  }

  // PY-27398
  public void testOrderAndNotEq() {
    doTest();
  }

  // PY-27398
  public void testDefaultFieldValue() {
    doTest();
  }

  // PY-27398
  public void testFieldsOrder() {
    doTest();
  }

  // PY-26354
  public void testAttrsFieldsOrder() {
    doTest();
  }

  // PY-28506, PY-31762
  public void testFieldsOrderInInheritance() {
    doTest();
  }

  // PY-28506, PY-31762
  public void testFieldsOrderInMixedInheritance() {
    doTest();
  }

  // PY-34374, PY-33189
  public void testFieldsOrderInInheritanceKwOnlyNoDefaultBase() {
    doTest();
  }

  // PY-34374, PY-33189
  public void testFieldsOrderInInheritanceKwOnlyDefaultBase() {
    doTest();
  }

  // PY-34374, PY-33189
  public void testFieldsOrderInInheritanceKwOnlyNoDefaultDerived() {
    doTest();
  }

  // PY-34374, PY-33189
  public void testFieldsOrderInInheritanceKwOnlyDefaultDerived() {
    doTest();
  }

  // PY-27398
  public void testComparisonForOrdered() {
    doTest();
  }

  // PY-27398
  public void testComparisonForUnordered() {
    doTest();
  }

  // PY-27398
  public void testComparisonForOrderedAndUnordered() {
    doTest();
  }

  // PY-32078
  public void testComparisonForManuallyOrdered() {
    doTest();
  }

  // PY-26354
  public void testComparisonForOrderedAttrs() {
    doTest();
  }

  // PY-26354
  public void testComparisonForUnorderedAttrs() {
    doTest();
  }

  // PY-26354
  public void testComparisonForOrderedAndUnorderedAttrs() {
    doTest();
  }

  // PY-32078
  public void testComparisonForManuallyOrderedAttrs() {
    doTestByText("""
                   from attr import s

                   @s(cmp=False)
                   class Test:
                       def __gt__(self, other):
                           pass

                   print(Test() < Test())
                   print(Test() > Test())

                   print(Test < Test)
                   print(Test > Test)""");
  }

  // PY-28506
  public void testComparisonInStdInheritance() {
    doTest();
  }

  // PY-28506
  public void testComparisonForManuallyOrderedInStdInheritance() {
    doTest();
  }

  // PY-31762
  public void testComparisonInAttrsInheritance() {
    doTest();
  }

  // PY-31762
  public void testComparisonForManuallyOrderedInAttrsInheritance() {
    doTest();
  }

  // PY-27398
  public void testHelpersArgument() {
    doTest();
  }

  // PY-26354
  public void testAttrsHelpersArgument() {
    doTest();
  }

  // PY-28506
  public void testHelpersArgumentInStdInheritance() {
    doTest();
  }

  // PY-31762
  public void testHelpersArgumentInAttrsInheritance() {
    doTest();
  }

  // PY-27398
  public void testAccessToInitVar() {
    doTest();
  }

  // PY-28506, PY-31762
  public void testAccessToInitVarInHierarchy() {
    doTest();
  }

  // PY-33445
  public void testDontConsiderUnresolvedFieldsAsInitOnly() {
    doTestByText("""
                   class A:
                       pass

                   a = A()
                   b = a.b""");
  }

  // PY-27398
  public void testUselessInitVar() {
    doTest();
  }

  // PY-27398
  public void testUselessDunderPostInit() {
    doTest();
  }

  // PY-27398
  public void testWrongDunderPostInitSignature() {
    doTest();
  }

  // PY-26354
  public void testUselessDunderAttrsPostInit() {
    doTest();
  }

  // PY-26354
  public void testWrongDunderAttrsPostInitSignature() {
    doTest();
  }

  // PY-28506
  public void testWrongDunderPostInitSignatureInStdHierarchy() {
    doTest();
  }

  // PY-43359
  public void testSuppressedDunderPostInitSignature() {
    doTestByText("""
                   import dataclasses

                   @dataclasses.dataclass
                   class A:
                       a: int
                       b: dataclasses.InitVar[str]
                       c: dataclasses.InitVar[bytes]

                       def __post_init__(self, *args, **kwargs):
                           pass""");
  }

  // PY-27398
  public void testFieldDefaultAndDefaultFactory() {
    doTest();
  }

  // PY-26354
  public void testAttrsFieldDefaultAndFactory() {
    doTest();
  }

  // PY-27398
  public void testUselessInitReprEq() {
    doTest();
  }

  // PY-27398
  public void testUselessOrder() {
    doTest();
  }

  // PY-27398
  public void testUselessFrozen() {
    doTest();
  }

  // PY-27398
  public void testUselessUnsafeHash() {
    doTest();
  }

  // PY-26354
  public void testAttrsUselessInitReprStrEq() {
    doTest();
  }

  // PY-26354
  public void testAttrsUselessOrder() {
    doTest();
  }

  // PY-26354
  public void testAttrsUselessFrozen() {
    doTest();
  }

  // PY-26354
  public void testAttrsUselessHash() {
    doTest();
  }

  // PY-26354
  public void testAttrsDefaultThroughKeywordAndDecorator() {
    doTest();
  }

  // PY-26354
  public void testAttrsInitializersAndValidators() {
    doTest();
  }

  // PY-29645
  public void testLackingTypeAnnotation() {
    doTest();
  }

  // PY-26354
  public void testAttrsLackingTypeAnnotation() {
    doTest();
  }

  // PY-40018
  public void testInheritingDefaultArgumentThroughEmptyDataclass() {
    doTest();
  }

  @Override
  protected void doTest() {
    myFixture.copyDirectoryToProject("packages/attr", "attr");
    myFixture.copyDirectoryToProject("packages/attrs", "attrs");
    super.doTest();
    assertProjectFilesNotParsed(myFixture.getFile());
  }

  @NotNull
  @Override
  protected Class<? extends PyInspection> getInspectionClass() {
    return PyDataclassInspection.class;
  }
}
