/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.testing;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.PythonConfigurationFactoryBase;
import com.jetbrains.python.testing.doctest.PythonDocTestRunConfiguration;
import com.jetbrains.python.testing.nosetest.PythonNoseTestRunConfiguration;
import com.jetbrains.python.testing.pytest.PyTestRunConfiguration;
import com.jetbrains.python.testing.unittest.PythonUnitTestRunConfiguration;
import com.jetbrains.python.testing.universalTests.PyUniversalTestsKt;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 */
public class PythonTestOldConfigurationType extends PythonTestConfigurationType {
  public static final String ID = "tests";

  public final PythonDocTestConfigurationFactory PY_DOCTEST_FACTORY = new PythonDocTestConfigurationFactory(this);
  public final PythonUnitTestConfigurationFactory PY_UNITTEST_FACTORY = new PythonUnitTestConfigurationFactory(this);
  public final PythonNoseTestConfigurationFactory PY_NOSETEST_FACTORY = new PythonNoseTestConfigurationFactory(this);
  public final PythonPyTestConfigurationFactory PY_PYTEST_FACTORY = new PythonPyTestConfigurationFactory(this);

  public static PythonTestOldConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(PythonTestOldConfigurationType.class);
  }


  @Override
  public String getDisplayName() {
    // Only doctests are supported when new mode (isUniversalModeEnabled)
    return (PyUniversalTestsKt.isUniversalModeEnabled()
            ? super.getDisplayName() + ' ' + PY_DOCTEST_FACTORY.getName()
            : super.getDisplayName());
  }

  private static class PythonUnitTestConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonUnitTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonUnitTestRunConfiguration(project, this);
    }

    @Override
    public String getName() {
      return PyBundle.message("runcfg.unittest.display_name");
    }
  }

  private static class PythonDocTestConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonDocTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonDocTestRunConfiguration(project, this);
    }

    @Override
    public String getName() {
      return PyBundle.message("runcfg.doctest.display_name");
    }
  }

  private static class PythonPyTestConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonPyTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PyTestRunConfiguration(project, this);
    }

    @Override
    public String getName() {
      return PyBundle.message("runcfg.pytest.display_name");
    }
  }

  private static class PythonNoseTestConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonNoseTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonNoseTestRunConfiguration(project, this);
    }

    @Override
    public String getName() {
      return PyBundle.message("runcfg.nosetests.display_name");
    }
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    if (PyUniversalTestsKt.isUniversalModeEnabled()) {
      return new ConfigurationFactory[]{PY_DOCTEST_FACTORY};
    }
    return new ConfigurationFactory[]{PY_UNITTEST_FACTORY, PY_DOCTEST_FACTORY, PY_NOSETEST_FACTORY,
      PY_PYTEST_FACTORY};
  }
}
