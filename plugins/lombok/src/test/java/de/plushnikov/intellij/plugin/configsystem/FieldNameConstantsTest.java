package de.plushnikov.intellij.plugin.configsystem;

public class FieldNameConstantsTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/fieldnameconstants";
  }

  public void testConfiguration$FieldNameConstantsConfigKeys() {
    doTest();
  }

  public void testUppercase$FieldNameConstantsUppercased() {
    doTest();
  }
}
