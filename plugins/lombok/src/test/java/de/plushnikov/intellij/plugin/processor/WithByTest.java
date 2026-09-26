package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

public class WithByTest extends AbstractLombokParsingTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptorForNormalMode() {
    return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
  }

  //public void testWithby$WithByInAnonymousClass() {
  //  doTest(true);
  //}

  public void testWithby$WithByNullAnnos() {
    doTest(true);
  }

  public void testWithby$WithByOnRecord() {
    doTest(true);
  }

  public void testWithby$WithByOnRecordComponent() {
    doTest(true);
  }

  public void testWithby$WithByTypes() {
    doTest(true);
  }
}