package de.plushnikov.intellij.plugin.configsystem;

/**
 * Unit tests for Constructor and @ConstructorProperties generation
 */
public class ConstructorTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected boolean shouldCompareAnnotations() {
    return true;
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/constructor";
  }

  public void testDefault$NoArgsConstructorTest() {
    doTest();
  }

  public void testDefault$AllArgsConstructorTest() {
    doTest();
  }

  public void testDefault$RequiredArgsConstructorTest() {
    doTest();
  }

  public void testSuppressConstructorProperties$NoArgsConstructorTest() {
    doTest();
  }

  public void testSuppressConstructorProperties$AllArgsConstructorTest() {
    doTest();
  }

  public void testSuppressConstructorProperties$RequiredArgsConstructorTest() {
    doTest();
  }

  public void testAddConstructorProperties$NoArgsConstructorTest() {
    doTest();
  }

  public void testAddConstructorProperties$AllArgsConstructorTest() {
    doTest();
  }

  public void testAddConstructorProperties$RequiredArgsConstructorTest() {
    doTest();
  }

  public void testCopyableAnnotations$FooServiceTest() {
    doTest();
  }
}
