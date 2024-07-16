package de.plushnikov.intellij.plugin.configsystem;

import org.jetbrains.annotations.NotNull;

import java.util.List;

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

  @Override
  protected @NotNull List<ModeRunnerType> modes() {
    //now incomplete mode is not supported for this processor, because it depends on the lombok version
    //after returning to normal mode, caches will be dropped
    return super.modes()
      .stream().filter(t -> t != ModeRunnerType.INCOMPLETE)
      .toList();
  }
}
