package de.plushnikov.intellij.plugin.configsystem;

import java.io.IOException;

public class FieldNameConstantsTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/fieldnameconstants";
  }

  public void testConfiguration$FieldNameConstantsConfigKeys() throws IOException {
    doTest();
  }
}
