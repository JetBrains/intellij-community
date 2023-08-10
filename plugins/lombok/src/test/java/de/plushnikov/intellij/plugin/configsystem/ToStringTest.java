package de.plushnikov.intellij.plugin.configsystem;

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

  public void testConfiguration$ToStringConfiguration() {
    doTest();
  }

  public void testDoNotUseGetters$SomeTest() {
    doTest();
  }

  public void testDoNotUseGetters$AnnotationOverwriteTest() {
    doTest();
  }

  public void testIncludeFieldNames$SomeTest() {
    doTest();
  }

  public void testIncludeFieldNames$AnnotationOverwriteTest() {
    doTest();
  }

  public void testCallSuper$AnnotationOverwriteTest() {
    doTest();
  }

  public void testCallSuper$SomeTestWithSuper() {
    doTest();
  }

  public void testCallSuper$SomeTestWithoutSuper() {
    doTest();
  }

  public void testOnlyExplicitlyIncluded$SomeTest() {
    doTest();
  }
}
