// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin;

import com.intellij.ide.plugins.ContentModuleDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.PluginModuleId;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.jetbrains.performancePlugin.commands.RunClassInPluginModule;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

public class RunClassInPluginModuleCommandTest extends HeavyPlatformTestCase {
  public void test_execute_script_in_module_classloader() throws Exception {
    ContentModuleDescriptor module = null;
    for (ContentModuleDescriptor candidate : PluginManagerCore.getPluginSet().getUnsortedEnabledModules()) {
      if (candidate.getPluginClassLoader() != null) {
        module = candidate;
        break;
      }
    }
    Assert.assertNotNull("No loaded content module found", module);
    PluginModuleId moduleId = module.moduleId;

    System.setProperty(TestClass1.class.getName(), "not set");

    String line = RunClassInPluginModule.PREFIX +
                  " " + moduleId.getName() +
                  " " + moduleId.getNamespace() +
                  " " + TestClass1.class.getName() +
                  " method123 \"" +
                  PathManager.getJarPathForClass(TestClass1.class) +
                  "\"";

    new RunClassInPluginModule(line, 123).computePromise(getProject());
    Assert.assertEquals("123", System.getProperty(TestClass1.class.getName()));
  }

  public static class TestClass1 {
    @SuppressWarnings("unused")
    public void method123(@NotNull Project project) {
      System.setProperty(getClass().getName(), "123");
    }
  }
}