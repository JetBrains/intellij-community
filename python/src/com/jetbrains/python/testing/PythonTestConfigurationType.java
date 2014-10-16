/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.jetbrains.python.testing.attest.PythonAtTestRunConfiguration;
import com.jetbrains.python.testing.doctest.PythonDocTestRunConfiguration;
import com.jetbrains.python.testing.nosetest.PythonNoseTestRunConfiguration;
import com.jetbrains.python.testing.pytest.PyTestRunConfiguration;
import com.jetbrains.python.testing.unittest.PythonUnitTestRunConfiguration;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User : catherine
 */
public class PythonTestConfigurationType implements ConfigurationType {
  public static final String ID = "tests";

  public final PythonDocTestConfigurationFactory PY_DOCTEST_FACTORY = new PythonDocTestConfigurationFactory(this);
  public final PythonUnitTestConfigurationFactory PY_UNITTEST_FACTORY = new PythonUnitTestConfigurationFactory(this);
  public final PythonNoseTestConfigurationFactory PY_NOSETEST_FACTORY = new PythonNoseTestConfigurationFactory(this);
  public final PythonPyTestConfigurationFactory PY_PYTEST_FACTORY = new PythonPyTestConfigurationFactory(this);
  public final PythonAtTestConfigurationFactory PY_ATTEST_FACTORY = new PythonAtTestConfigurationFactory(this);

  public static PythonTestConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(PythonTestConfigurationType.class);
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

  private static class PythonAtTestConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonAtTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonAtTestRunConfiguration(project, this);
    }

    @Override
    public String getName() {
      return PyBundle.message("runcfg.attest.display_name");
    }
  }

  @Override
  public String getDisplayName() {
    return PyBundle.message("runcfg.test.display_name");
  }

  @Override
  public String getConfigurationTypeDescription() {
    return PyBundle.message("runcfg.test.description");
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.PythonTests;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] {PY_UNITTEST_FACTORY, PY_DOCTEST_FACTORY, PY_NOSETEST_FACTORY,
        PY_PYTEST_FACTORY, PY_ATTEST_FACTORY};
  }
}
