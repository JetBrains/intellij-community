package de.plushnikov.intellij.plugin.processor;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

public class OnXAnnotationTest extends AbstractLombokParsingTestCase {

  @Override
  protected boolean shouldCompareAnnotations() {
    return true;
  }

  @Override
  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testTestOnX() {
    doTest(false);
  }

}
