package de.plushnikov.intellij.plugin.configsystem;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class ToStringTest extends AbstractLombokConfigSystemTestCase {

  protected boolean shouldCompareAnnotations() {
    return true;
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/tostring";
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
}