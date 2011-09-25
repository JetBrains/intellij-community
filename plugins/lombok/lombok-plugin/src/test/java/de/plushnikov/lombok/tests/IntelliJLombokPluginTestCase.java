package de.plushnikov.lombok.tests;

import de.plushnikov.lombok.LombokParsingTestCase;
import org.junit.Test;


/**
 * Unit tests for IntelliJPlugin for Lombok
 * For this to work, the correct system property idea.home.path needs to be passed to the test runner.
 */
public class IntelliJLombokPluginTestCase extends LombokParsingTestCase {

  @Test
  public void testNonNullPlain() {
    doTest();
  }

  @Test
  public void testSynchronizedName() {
    doTest();
  }

  @Test
  public void testSynchronizedPlain() {
    doTest();
  }

  @Test
  public void testClassNamedAfterGetter() {
    doTest();
  }

  @Test
  public void testCommentsInterspersed() {
    doTest();
  }

  @Test
  public void testConstructors() {
    doTest();
  }


}
