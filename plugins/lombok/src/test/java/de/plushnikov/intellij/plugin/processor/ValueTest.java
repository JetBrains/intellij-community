package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.util.RecursionManager;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

public class ValueTest extends AbstractLombokParsingTestCase {

  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testValue$ValueIssue78() {
    doTest(true);
  }

  public void testValue$ValueIssue94() {
    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());

    doTest(true);
  }

  public void testValue$ValuePlain() {
    doTest(true);
  }

  public void testValue$ValueStarImport() {
    doTest(true);
  }

  public void testValue$ValueStaticConstructor() {
    doTest(true);
  }

  public void testValue$ValueBuilder() {
    doTest(true);
  }

  public void testValue$ValueAndBuilder93() {
    doTest(true);
  }

  public void testValue$ValueAndWither() {
    doTest(true);
  }

  public void testValue$ValueAndWitherAndRequiredConstructor() {
    doTest(true);
  }

  public void testValue$ValueWithGeneric176() {
    doTest(true);
  }

  public void testValue$ValueWithPackagePrivate() {
    //TODO disable assertions for the moment
    RecursionManager.disableMissedCacheAssertions(myFixture.getProjectDisposable());

    doTest(true);
  }
}
