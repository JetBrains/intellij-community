package de.plushnikov.intellij.plugin.processor;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.util.BuildNumber;
import de.plushnikov.intellij.plugin.AbstractLombokParsingTestCase;

import java.io.IOException;

public class UtilityClassTest extends AbstractLombokParsingTestCase {
  protected boolean shouldCompareModifiers() {
    return ApplicationInfo.getInstance().getBuild().compareTo(BuildNumber.fromString("146.1154")) >= 0;
  }

  public void testUtilityclass$UtilityClassPlain() throws IOException {
    doTest(true);
  }
}
