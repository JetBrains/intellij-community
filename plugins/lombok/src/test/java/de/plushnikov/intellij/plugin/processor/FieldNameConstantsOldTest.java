package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Unit tests for @FieldNameConstants annotation from old version of lombok (1.18.2)
 */
public class FieldNameConstantsOldTest extends AbstractLombokParsingTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptorForNormalMode() {
    return LombokTestUtil.LOMBOK_OLD_JAVA_1_8_DESCRIPTOR;
  }

  public void testFieldnameconstants$FieldNameConstantsOldBasic() {
    doTest(true);
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
