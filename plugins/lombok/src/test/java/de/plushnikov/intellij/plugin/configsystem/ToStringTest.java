package de.plushnikov.intellij.plugin.configsystem;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class ToStringTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected boolean shouldCompareAnnotations() {
    return true;
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/tostring";
  }

  public void testConfiguration$ToStringConfiguration() throws IOException {
    doTest();
  }

  public void testDoNotUseGetters$SomeTest() throws IOException {
    doTest();
  }

  public void testDoNotUseGetters$AnnotationOverwriteTest() throws IOException {
    doTest();
  }

  public void testIncludeFieldNames$SomeTest() throws IOException {
    doTest();
  }

  public void testIncludeFieldNames$AnnotationOverwriteTest() throws IOException {
    doTest();
  }

  public void testCallSuper$AnnotationOverwriteTest() throws IOException {
    doTest();
  }

  public void testCallSuper$SomeTestWithSuper() throws IOException {
    doTest();
  }

  public void testCallSuper$SomeTestWithoutSuper() throws IOException {
    doTest();
  }
}
