/*
 * User: anna
 * Date: 30-Jul-2007
 */
package com.theoryinpractice.testng;

import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.theoryinpractice.testng.intention.TestNGOrderEntryFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class TestNGPlugin implements ApplicationComponent {
  public TestNGPlugin(final IntentionManager manager, ExternalResourceManager externalResourceManager) {
    manager.addAction(new TestNGOrderEntryFix());
    externalResourceManager.addStdResource("http://testng.org/testng-1.0.dtd", "/resources/standardSchemas/testng-1.0.dtd", getClass());
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "TestNGPlugin";
  }

  public void initComponent() {}

  public void disposeComponent() {}
}