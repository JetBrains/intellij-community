package de.plushnikov.intellij.plugin.extension;

import com.intellij.ide.structureView.impl.java.JavaAnonymousClassesNodeProvider;
import com.intellij.ide.structureView.impl.java.PropertiesGrouper;
import com.intellij.ide.structureView.impl.java.SuperTypesGrouper;
import com.intellij.testFramework.PlatformTestUtil;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import org.intellij.lang.annotations.Language;

import javax.swing.*;

public class LombokStructureViewExtensionTest extends AbstractLombokLightCodeInsightTestCase {

  @Language("JAVA")
  private static final String LOMBOKED_TEST_CLASS = """
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.experimental.Accessors(fluent = true, prefix = "my")
    public class Test {
     private float fff;
     private String myString;
     @lombok.With
     private Boolean myActive;
     @lombok.experimental.Accessors(fluent = false)
     private int myX;
    }""";


  public void testLombokPropertiesGrouping() {
    doPropertiesTest(LOMBOKED_TEST_CLASS,
                     """
                       -Test.java
                        -Test
                         Test(float, String, Boolean, int)
                         Test()
                         equals(Object): boolean
                         canEqual(Object): boolean
                         hashCode(): int
                         toString(): String
                         -fff: float
                          fff(): float
                          fff(float): Test
                          fff: float
                         -myString: String
                          string(): String
                          string(String): Test
                          myString: String
                         -myActive: Boolean
                          active(): Boolean
                          active(Boolean): Test
                          withActive(Boolean): Test
                          myActive: Boolean
                         -myX: int
                          getX(): int
                          setX(int): void
                          myX: int
                       """);
  }

  private void doPropertiesTest(String classText, String expected) {
    doTest(classText, expected, false, true);
  }

  private void doTest(String classText,
                      String expected,
                      boolean showInterfaces,
                      boolean showProperties) {
    myFixture.configureByText("Test.java", classText);
    myFixture.testStructureView(svc -> {
      svc.setActionActive(SuperTypesGrouper.ID, showInterfaces);
      svc.setActionActive(PropertiesGrouper.ID, showProperties);
      svc.setActionActive(JavaAnonymousClassesNodeProvider.ID, true);
      JTree tree = svc.getTree();
      PlatformTestUtil.waitWhileBusy(tree);
      PlatformTestUtil.expandAll(tree);
      PlatformTestUtil.assertTreeEqual(tree, expected);
    });
  }
}
