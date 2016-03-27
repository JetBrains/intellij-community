package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

/**
 * Unit tests for IntelliJPlugin for Lombok, based on lombok test classes
 */
public class BuilderTest extends AbstractLombokParsingTestCase {

  // This test is lombok's homepage example.
  public void testBuilderExample() throws IOException {
    doTest();
  }

  // This test is lombok's homepage customized example.
  public void testBuilderExampleCustomized() throws IOException {
    doTest();
  }

  public void testBuilderSimple() throws IOException {
    doTest();
  }

  public void testBuilderComplex() throws IOException {
    doTest();
  }

  public void testBuilderChainAndFluent() throws IOException {
    doTest();
  }

  public void testBuilderWithAccessors() throws IOException {
    doTest();
  }

  // This test is lombok's homepage example with predefined elements and another inner class.
  public void testBuilderPredefined() throws IOException {
    doTest();
  }

  public void testBuilderWithExistingBuilderClass() throws IOException {
    doTest();
  }

  public void testBuilderConstructorException() throws IOException {
    doTest();
  }

  public void testBuilderAndAllArgsConstructor() throws IOException {
    doTest();
  }

  public void testBuilderMethodException() throws IOException {
    doTest();
  }

  //  TODO fixme
  public void ignore_testBuilderWithToBuilderOnClass() throws IOException {
    doTest();
  }

  //  TODO fixme
  public void ignore_testBuilderWithToBuilderOnConstructor() throws IOException {
    doTest();
  }

  //  TODO fixme
  public void ignore_testBuilderWithToBuilderOnMethod() throws IOException {
    doTest();
  }
}
