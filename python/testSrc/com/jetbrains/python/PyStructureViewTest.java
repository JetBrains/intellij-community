// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.PlatformTestUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.structureView.PyStructureViewElement;

import javax.swing.*;

import static com.intellij.testFramework.PlatformTestUtil.assertTreeEqual;

/**
 * @author vlan
 */
public class PyStructureViewTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "structureView/";

  public void testBaseClassNames() {
    myFixture.configureByFiles(TEST_DIRECTORY + "baseClassNames.py",
                               TEST_DIRECTORY + "lib1.py");
    doTest("-baseClassNames.py\n" +
           " -B1\n" +
           "  f(self, x)\n" +
           " -B2(object)\n" +
           "  g(x)\n" +
           " C(B1, B2)\n" +
           " D1(C)\n" +
           " D2(C)\n" +
           " D3(lib1.C)\n" +
           " D4(foo.bar.C)\n",
           false);
  }

  // PY-3371
  public void testAttributes() {
    myFixture.configureByFile(TEST_DIRECTORY + "attributes.py");
    doTest("-attributes.py\n" +
           " -B(object)\n" +
           "  f(self, x)\n" +
           "  __init__(self, x, y)\n" +
           "  g(cls, x)\n" +
           "  c1\n" +
           "  c2\n" +
           "  i1\n" +
           "  i2\n" +
           "  i3\n" +
           " g1\n" +
           " -C(B)\n" +
           "  __init__(self, x, y)\n" +
           "  h(self)\n" +
           "  c2\n" +
           "  c3\n" +
           "  i3\n" +
           "  i4\n" +
           "  i5\n" +
           " g2\n",
           false);
  }

  // PY-3936
  public void testInherited() {
    myFixture.configureByFile(TEST_DIRECTORY + "inherited.py");
    doTest("-inherited.py\n" +
           " -C(object)\n" +
           "  f(self, x)\n" +
           "  __str__(self)\n" +
           "  x\n" +
           "  __init__(self)\n" +
           "  __new__(cls)\n" +
           "  __setattr__(self, name, value)\n" +
           "  __eq__(self, o)\n" +
           "  __ne__(self, o)\n" +
           "  __repr__(self)\n" +
           "  __hash__(self)\n" +
           "  __format__(self, format_spec)\n" +
           "  __getattribute__(self, name)\n" +
           "  __delattr__(self, name)\n" +
           "  __sizeof__(self)\n" +
           "  __reduce__(self)\n" +
           "  __reduce_ex__(self, protocol)\n" +
           "  __class__\n" +
           "  __dict__\n" +
           "  __doc__\n" +
           "  __module__\n" +
           "  __slots__\n",
           true);
  }

  // EA-83566
  public void testInvalidatedElement() {
    myFixture.configureByText("a.py",
                              "def f():\n" +
                              "    pass");
    final PyFunction function = myFixture.findElementByText("f", PyFunction.class);
    final PyStructureViewElement node = new PyStructureViewElement(function);
    WriteCommandAction.runWriteCommandAction(myFixture.getProject(), function::delete);
    assertNull(node.getValue());
    final ItemPresentation presentation = node.getPresentation();
    assertNotNull(presentation);
    final Icon icon = presentation.getIcon(false);
    assertNull(icon);
  }

  // PY-19078
  public void testParentImportedWithAs() {
    myFixture.configureByFiles(TEST_DIRECTORY + "parentImportedWithAs.py", TEST_DIRECTORY + "lib2.py");
    doTest("-parentImportedWithAs.py\n" +
           " -CLS(P)\n" +
           "  foo(self)\n" +
           "  __init__(self)\n" +
           "  __new__(cls)\n" +
           "  __setattr__(self, name, value)\n" +
           "  __eq__(self, o)\n" +
           "  __ne__(self, o)\n" +
           "  __str__(self)\n" +
           "  __repr__(self)\n" +
           "  __hash__(self)\n" +
           "  __format__(self, format_spec)\n" +
           "  __getattribute__(self, name)\n" +
           "  __delattr__(self, name)\n" +
           "  __sizeof__(self)\n" +
           "  __reduce__(self)\n" +
           "  __reduce_ex__(self, protocol)\n" +
           "  __class__\n" +
           "  __dict__\n" +
           "  __doc__\n" +
           "  __module__\n" +
           "  __slots__\n",
           true);
  }

  private void doTest(final String expected, final boolean inherited) {
    myFixture.testStructureView(component -> {
      component.setActionActive("SHOW_INHERITED", !inherited);
      PlatformTestUtil.waitWhileBusy(component.getTree());
      assertTreeEqual(component.getTree(), expected);
    });
  }
}
