package de.plushnikov.intellij.plugin.processor;

import com.intellij.testFramework.LightProjectDescriptor;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Unit tests for @FieldNameConstants annotation from old version of lombok (1.18.2)
 */
public class FieldNameConstantsOldTest extends AbstractLombokParsingTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LombokTestUtil.LOMBOK_OLD_DESCRIPTOR;
  }

  public void testFieldnameconstants$FieldNameConstantsOldBasic() {
    doTest(true);
  }
}
