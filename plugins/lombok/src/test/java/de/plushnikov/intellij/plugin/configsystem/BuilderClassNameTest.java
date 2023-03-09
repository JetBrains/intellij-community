package de.plushnikov.intellij.plugin.configsystem;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class BuilderClassNameTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/builder";
  }

  public void testClassName$BuilderWithConfiguredClassName() {
    doTest();
  }
}
