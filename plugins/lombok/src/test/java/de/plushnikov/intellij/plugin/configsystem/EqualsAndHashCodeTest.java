package de.plushnikov.intellij.plugin.configsystem;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class EqualsAndHashCodeTest extends AbstractLombokConfigSystemTestCase {

  protected boolean shouldCompareAnnotations() {
    return true;
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/equalsandhashcode";
  }

  public void testDoNotUseGetters$SomeTest() throws IOException {
    doTest();
  }

  public void testDoNotUseGetters$AnnotationOverwriteTest() throws IOException {
    doTest();
  }

  public void testCallSuper$WithSuperTest() throws IOException {
    doTest();
  }

  public void testCallSuper$WithoutSuperTest() throws IOException {
    doTest();
  }
}