package com.jetbrains.python;

import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.util.Consumer;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

import static com.intellij.testFramework.PlatformTestUtil.assertTreeEqual;

/**
 * @author vlan
 */
public class PyStructureViewTest extends PyLightFixtureTestCase {
  private static String TEST_DIRECTORY = "structureView/";

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
           " D3(lib1.C)\n");
  }

  public void testAttributes() { // PY-3371
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
           " g2\n");
  }

  private void doTest(final String expected) {
    myFixture.testStructureView(new Consumer<StructureViewComponent>() {
      @Override
      public void consume(StructureViewComponent component) {
        assertTreeEqual(component.getTree(), expected);
      }
    });
  }
}
