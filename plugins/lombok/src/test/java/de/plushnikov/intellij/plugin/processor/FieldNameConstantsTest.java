package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Unit tests for @FieldNameConstants annotation from current version of lombok
 */
public class FieldNameConstantsTest extends AbstractLombokParsingTestCase {

  public void testFieldnameconstants$FieldNameConstantsBasic() {
    doTest(true);
  }

  public void testFieldnameconstants$FieldNameConstantsEnum() {
    doTest(true);
  }

  public void testFieldnameconstants$FieldNameConstantsHandrolled() {
    doTest(true);
  }

  public void testFieldnameconstants$FieldNameConstantsOnRecord() {
    doTest(true);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptorForNormalMode() {
    return LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR;
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
