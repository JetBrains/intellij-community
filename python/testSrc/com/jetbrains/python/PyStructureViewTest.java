// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.structureView.PyStructureViewElement;

import javax.swing.*;

import static com.intellij.testFramework.PlatformTestUtil.assertTreeEqual;

public class PyStructureViewTest extends PyTestCase {
  private static final String TEST_DIRECTORY = "structureView/";

  public void testBaseClassNames() {
    myFixture.configureByFiles(TEST_DIRECTORY + "baseClassNames.py",
                               TEST_DIRECTORY + "lib1.py");
    doTest("""
             -baseClassNames.py
              -B1
               f(self, x)
              -B2(object)
               g(x)
              C(B1, B2)
              D1(C)
              D2(C)
              D3(lib1.C)
              D4(foo.bar.C)
             """,
           false);
  }

  // PY-3371
  public void testAttributes() {
    myFixture.configureByFile(TEST_DIRECTORY + "attributes.py");
    doTest("""
             -attributes.py
              -B(object)
               f(self, x)
               __init__(self, x, y)
               g(cls, x)
               c1
               c2
               i1
               i2
               i3
              g1
              -C(B)
               __init__(self, x, y)
               h(self)
               c2
               c3
               i3
               i4
               i5
              g2
             """,
           false);
  }

  // PY-3936
  public void testInherited() {
    myFixture.configureByFile(TEST_DIRECTORY + "inherited.py");
    doTest("""
             -inherited.py
              -C(object)
               f(self, x)
               __str__(self)
               x
               __class__(self)
               __init__(self)
               __new__(cls)
               __setattr__(self, name, value, /)
               __delattr__(self, name, /)
               __eq__(self, value, /)
               __ne__(self, value, /)
               __repr__(self)
               __hash__(self)
               __format__(self, format_spec, /)
               __getattribute__(self, name, /)
               __sizeof__(self)
               __reduce__(self)
               __reduce_ex__(self, protocol, /)
               __getstate__(self)
               __dir__(self)
               __init_subclass__(cls)
               __subclasshook__(cls, subclass, /)
               __annotations__
               __dict__
               __doc__
               __module__
             """,
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
    doTest("""
             -parentImportedWithAs.py
              -CLS(P)
               foo(self)
               __class__(self)
               __init__(self)
               __new__(cls)
               __setattr__(self, name, value, /)
               __delattr__(self, name, /)
               __eq__(self, value, /)
               __ne__(self, value, /)
               __str__(self)
               __repr__(self)
               __hash__(self)
               __format__(self, format_spec, /)
               __getattribute__(self, name, /)
               __sizeof__(self)
               __reduce__(self)
               __reduce_ex__(self, protocol, /)
               __getstate__(self)
               __dir__(self)
               __init_subclass__(cls)
               __subclasshook__(cls, subclass, /)
               __annotations__
               __dict__
               __doc__
               __module__
             """,
           true);
  }

  private void doTest(final String expected, final boolean inherited) {
    myFixture.testStructureView(component -> {
      component.setActionActive("SHOW_INHERITED", !inherited);
      final JTree tree = component.getTree();
      PlatformTestUtil.waitWhileBusy(tree);
      PlatformTestUtil.waitForPromise(TreeUtil.promiseExpandAll(tree));
      assertFalse(tree.isRootVisible());
      assertTreeEqual(tree, expected);
    });
  }
}
