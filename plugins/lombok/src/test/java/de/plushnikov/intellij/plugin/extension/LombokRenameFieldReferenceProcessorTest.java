package de.plushnikov.intellij.plugin.extension;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.AbstractLombokLightCodeInsightTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link LombokRenameFieldReferenceProcessor}
 */
public class LombokRenameFieldReferenceProcessorTest extends AbstractLombokLightCodeInsightTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/extension";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_NEW_DESCRIPTOR;
  }

  public void testRenameFieldInDataClass() {
    doTest("RenameFieldInDataClass", "oldField", "newField");
  }

  public void testRenameFieldInBuilderClass() {
    doTest("RenameFieldInBuilderClass", "oldField", "newField");
  }

  public void testRenameFieldInDataBuilderClass() {
    doTest("RenameFieldInDataBuilderClass", "oldField", "newField");
  }

  public void testRenameFieldInRecord() {
    doTest("RenameFieldInRecord", "oldField", "newField");
  }

  public void testRenameFieldInRecordWithBuilder() {
    doTest("RenameFieldInRecordWithBuilder", "oldField", "newField");
  }

  public void testRenameFieldWithWithAnnotation() {
    doTest("RenameFieldWithWithAnnotation", "oldField", "newField");
  }

  public void testRenameFieldWithFieldNameConstants() {
    doTest("RenameFieldWithFieldNameConstants", "oldField", "newField");
  }

  public void testRenameFieldWithWithAndFieldNameConstants() {
    doTest("RenameFieldWithWithAndFieldNameConstants", "oldField", "newField");
  }

  public void testRenameFieldWithWitherAndFieldNameConstants() {
    doTest("RenameFieldWithWitherAndFieldNameConstants", "oldField", "newField");
  }

  private void doTest(final String className, final String oldFieldName, final String newFieldName) {
    myFixture.configureByFile(getTestName(false) + ".java");
    PsiClass aClass = myFixture.findClass(className);
    assertNotNull("Tested class not found", aClass);

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

    myFixture.checkResultByFile(getTestName(false) + "_after.java", true);
  }
}
