package de.plushnikov.intellij.plugin.processor.modifier;

import com.intellij.java.codeInspection.DataFlowInspectionTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

public class ValModifierDataFlowInspection21Test extends DataFlowInspectionTestCase {

  @Override
  protected @NotNull String getTestDataPath() {
    return LombokTestUtil.getTestDataPath() + "/augment/modifier";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
  }


  public void testJSpecifyLocalWithGenericsWithVar() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    doTest();
  }
}