package de.plushnikov.intellij.plugin.configsystem;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class AccessorsTest extends AbstractLombokConfigSystemTestCase {

  protected boolean shouldCompareAnnotations() {
    return true;
  }

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/accessors";
  }

  public void testChain$GetterSetterFieldTest() throws IOException {
    doTest();
  }

  public void testChain$GetterSetterFieldAnnotationOverwriteTest() throws IOException {
    doTest();
  }

  public void testChain$GetterSetterClassTest() throws IOException {
    doTest();
  }

  public void testChain$GetterSetterClassAnnotationOverwriteTest() throws IOException {
    doTest();
  }

  public void testChain$GetterSetterWithoutAccessorsAnnotationTest() throws IOException {
    doTest();
  }

  ////////////

  public void testFluent$GetterSetterFieldTest() throws IOException {
    doTest();
  }

  public void testFluent$GetterSetterFieldAnnotationOverwriteTest() throws IOException {
    doTest();
  }

  public void testFluent$GetterSetterClassTest() throws IOException {
    doTest();
  }

  public void testFluent$GetterSetterClassAnnotationOverwriteTest() throws IOException {
    doTest();
  }

  public void testFluent$GetterSetterWithoutAccessorsAnnotationTest() throws IOException {
    doTest();
  }

  ////////////

  public void testPrefix$GetterSetterClassTest() throws IOException {
    doTest();
  }

  public void testPrefix$GetterSetterWithoutAccessorsAnnotationClassTest() throws IOException {
    doTest();
  }
}