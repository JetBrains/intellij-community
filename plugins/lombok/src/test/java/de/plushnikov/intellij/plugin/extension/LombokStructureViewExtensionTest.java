package de.plushnikov.intellij.plugin.extension;

import com.intellij.ide.structureView.impl.java.JavaAnonymousClassesNodeProvider;
import com.intellij.ide.structureView.impl.java.KindSorter;
import com.intellij.ide.structureView.impl.java.PropertiesGrouper;
import com.intellij.ide.structureView.impl.java.SuperTypesGrouper;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.StructureViewComponentTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import javax.swing.JTree;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;

@TestApplication
public class LombokStructureViewExtensionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture);

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

  @Test
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
                         fff: float
                       """);
  }

  private void doPropertiesTest(String classText, String expected) {
    doTest(classText, expected, false, true);
  }

  private void doTest(String classText,
                      String expected,
                      boolean showInterfaces,
                      boolean showProperties) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Test.java", classText);
    StructureViewComponentTestUtil.testStructureView(myFixture, svc -> {
      svc.setActionActive(SuperTypesGrouper.ID, showInterfaces);
      svc.setActionActive(PropertiesGrouper.ID, showProperties);
      svc.setActionActive(JavaAnonymousClassesNodeProvider.ID, true);
      svc.setActionActive(KindSorter.ID, true);
      JTree tree = svc.getTree();
      PlatformTestUtil.waitWhileBusy(tree);
      PlatformTestUtil.expandAll(tree);
      PlatformTestUtil.assertTreeEqual(tree, expected);
    });
  }
}
