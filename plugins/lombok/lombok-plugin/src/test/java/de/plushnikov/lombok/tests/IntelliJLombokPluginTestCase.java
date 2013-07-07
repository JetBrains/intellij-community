package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import org.junit.Test;

import java.io.IOException;


/**
 * Unit tests for IntelliJPlugin for Lombok
 * For this to work, the correct system property idea.home.path needs to be passed to the test runner.
 */
public class IntelliJLombokPluginTestCase extends LombokParsingTestCase {

  @Test
  public void testNonNullPlain() throws IOException {
    doTest();
  }

  @Test
  public void testSynchronizedName() throws IOException {
    doTest();
  }

  @Test
  public void testSynchronizedPlain() throws IOException {
    doTest();
  }

  @Test
  public void testClassNamedAfterGetter()throws IOException  {
    doTest();
  }

  @Test
  public void testCommentsInterspersed()throws IOException  {
    doTest();
  }

  @Test
  public void testConstructors()throws IOException  {
    doTest();
  }


}
