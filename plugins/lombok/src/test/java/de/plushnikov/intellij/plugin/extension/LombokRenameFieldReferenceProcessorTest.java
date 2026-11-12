package de.plushnikov.intellij.plugin.extension;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static de.plushnikov.intellij.plugin.LombokFixturesKt.lombokCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link LombokRenameFieldReferenceProcessor}
 */
@TestApplication
public class LombokRenameFieldReferenceProcessorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lombokCodeInsightFixture(projectFixture, "/extension");

  @Test
  public void testRenameFieldInDataClass() {
    doTest("RenameFieldInDataClass", "oldField", "newField");
  }

  @Test
  public void testRenameFieldInBuilderClass() {
    doTest("RenameFieldInBuilderClass", "oldField", "newField");
  }

  @Test
  public void testRenameFieldInDataBuilderClass() {
    doTest("RenameFieldInDataBuilderClass", "oldField", "newField");
  }

  @Test
  public void testRenameFieldInRecord() {
    doTest("RenameFieldInRecord", "oldField", "newField");
  }

  @Test
  public void testRenameFieldInRecordWithBuilder() {
    doTest("RenameFieldInRecordWithBuilder", "oldField", "newField");
  }

  @Test
  public void testRenameFieldWithWithAnnotation() {
    doTest("RenameFieldWithWithAnnotation", "oldField", "newField");
  }

  @Test
  public void testRenameFieldWithFieldNameConstants() {
    doTest("RenameFieldWithFieldNameConstants", "oldField", "newField");
  }

  @Test
  public void testRenameFieldWithWithAndFieldNameConstants() {
    doTest("RenameFieldWithWithAndFieldNameConstants", "oldField", "newField");
  }

  @Test
  public void testRenameFieldWithWitherAndFieldNameConstants() {
    doTest("RenameFieldWithWitherAndFieldNameConstants", "oldField", "newField");
  }

  private void doTest(final String className, final String oldFieldName, final String newFieldName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      PsiClass aClass = myFixture.findClass(className);
      assertNotNull(aClass, "Tested class not found");

      PsiField field = aClass.findFieldByName(oldFieldName, false);
      if (field == null) {
        // Try to find record component
        for (PsiRecordComponent recordComponent : aClass.getRecordComponents()) {
          if (oldFieldName.equals(recordComponent.getName())) {
            // Position caret at the record component
            myFixture.getEditor().getCaretModel().moveToOffset(recordComponent.getTextOffset());
            myFixture.renameElementAtCaret(newFieldName);
            break;
          }
        }
      }
      else {
        // Position caret at the field
        myFixture.getEditor().getCaretModel().moveToOffset(field.getTextOffset());
        myFixture.renameElementAtCaret(newFieldName);
      }

      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java", true);
    });
  }
}
