package pl.mg6.hrisey.tests;

import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

public class ParcelableTestCase extends AbstractLombokParsingTestCase {

  @Override
  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testParcelableSimple() throws IOException {
    doTest();
  }
}
