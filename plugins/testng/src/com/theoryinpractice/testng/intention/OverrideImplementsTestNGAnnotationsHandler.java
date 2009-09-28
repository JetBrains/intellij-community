/*
 * User: anna
 * Date: 19-Aug-2008
 */
package com.theoryinpractice.testng.intention;

import com.intellij.codeInsight.generation.OverrideImplementsAnnotationsHandler;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;

public class OverrideImplementsTestNGAnnotationsHandler implements OverrideImplementsAnnotationsHandler{
  public String[] getAnnotations() {
    return TestNGUtil.CONFIG_ANNOTATIONS_FQN;
  }


  @NotNull
  public String[] annotationsToRemove(@NotNull final String fqName) {
    return new String[0];
  }
}