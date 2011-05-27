package com.jetbrains.python;

import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.util.Consumer;
import com.jetbrains.python.fixtures.PyLightFixtureTestCase;

import static com.intellij.testFramework.PlatformTestUtil.assertTreeEqual;

/**
 * @author vlan
 */
public class PyStructureViewTest extends PyLightFixtureTestCase {
  public void testBaseClassNames() {
    myFixture.configureByFiles("structureView/baseClassNames.py",
                               "structureView/lib1.py");
    myFixture.testStructureView(new Consumer<StructureViewComponent>() {
      @Override
      public void consume(StructureViewComponent component) {
        assertTreeEqual(component.getTree(), "-baseClassNames.py\n" +
                                             " -B1\n" +
                                             "  f(self, x)\n" +
                                             " -B2(object)\n" +
                                             "  g(x)\n" +
                                             " C(B1, B2)\n" +
                                             " D1(C)\n" +
                                             " D2(C)\n" +
                                             " D3(lib1.C)\n");
      }
    });
  }
}
