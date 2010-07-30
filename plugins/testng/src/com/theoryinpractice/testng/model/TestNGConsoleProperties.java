/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.theoryinpractice.testng.model;

import com.intellij.execution.Executor;
import com.intellij.execution.testframework.JavaAwareTestConsoleProperties;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.config.Storage;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import org.jetbrains.annotations.NonNls;

public class TestNGConsoleProperties extends JavaAwareTestConsoleProperties {
    @NonNls private static final String PREFIX = "TestNGSupport.";
    private final TestNGConfiguration myConfiguration;

    public TestNGConsoleProperties(TestNGConfiguration config, Executor executor)
    {
      super(new Storage.PropertiesComponentStorage(PREFIX, PropertiesComponent.getInstance()), config.getProject(), executor);
      myConfiguration = config;
    }

    public TestNGConfiguration getConfiguration()
    {
        return myConfiguration;
    }

  @Override
  public GlobalSearchScope getScope() {
    return myConfiguration.getPersistantData().getScope().getSourceScope(myConfiguration).getLibrariesScope();
  }
}
