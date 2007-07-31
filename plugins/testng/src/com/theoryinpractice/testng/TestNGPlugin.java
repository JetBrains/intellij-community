/*
 * User: anna
 * Date: 30-Jul-2007
 */
package com.theoryinpractice.testng;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.codeInsight.intention.IntentionManager;
import com.theoryinpractice.testng.intention.TestNGOrderEntryFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class TestNGPlugin implements ApplicationComponent {
  public TestNGPlugin(final IntentionManager manager) {
    manager.addAction(new TestNGOrderEntryFix());
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "TestNGPlugin";
  }

  public void initComponent() {}

  public void disposeComponent() {}
}