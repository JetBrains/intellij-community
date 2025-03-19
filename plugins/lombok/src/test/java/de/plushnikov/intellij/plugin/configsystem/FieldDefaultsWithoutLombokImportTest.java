package de.plushnikov.intellij.plugin.configsystem;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

/**
 * Unit tests for IntelliJPlugin for Lombok with activated config system
 */
public class FieldDefaultsWithoutLombokImportTest extends AbstractLombokConfigSystemTestCase {

  @Override
  protected String getBasePath() {
    return super.getBasePath() + "/configsystem/fieldDefaults";
  }

  public void testDefaultFinal$DefaultFinalFieldTest() throws IOException {
    doTest();
  }

  public void testDefaultPrivate$DefaultPrivateFieldTest() throws IOException {
    doTest();
  }

  @Override
  protected @NotNull List<ModeRunnerType> modes() {
    //incomplete mode is not supported, because files don't contain any lombok annotations,
    //checking lombok.config is expensive, so skip such cases
    //after returning to normal mode, caches will be dropped
    return super.modes()
      .stream().filter(t -> t != ModeRunnerType.INCOMPLETE)
      .toList();
  }
}
