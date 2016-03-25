package pl.mg6.hrisey.tests;

import de.plushnikov.intellij.plugin.LombokParsingTestCase;

import java.io.IOException;

public class ParcelableTestCase extends LombokParsingTestCase {

  @Override
  protected boolean shouldCompareCodeBlocks() {
    return false;
  }

  public void testParcelableSimple() throws IOException {
    doTest();
  }
}
