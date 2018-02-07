/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.python.testing;

import com.google.common.collect.ObjectArrays;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.PythonConfigurationFactoryBase;
import com.jetbrains.python.testing.doctest.PythonDocTestRunConfiguration;
import com.jetbrains.python.testing.nosetestLegacy.PythonNoseTestRunConfiguration;
import com.jetbrains.python.testing.pytestLegacy.PyTestRunConfiguration;
import com.jetbrains.python.testing.unittestLegacy.PythonUnitTestRunConfiguration;
import com.jetbrains.python.testing.PyTestLegacyInteropKt;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User : catherine
 * <p>
 * This type is used both with Legacy and New test runners.
 * {@link PyTestLegacyInteropKt} is used to support legacy. To drop legacy support, remove all code that depends on it.
 */
public final class PythonTestConfigurationType implements ConfigurationType {
  public static final String ID = "tests";

  public final PythonConfigurationFactoryBase PY_DOCTEST_FACTORY = new PythonDocTestConfigurationFactory(this);
  public final PythonConfigurationFactoryBase LEGACY_UNITTEST_FACTORY = new PythonLegacyUnitTestConfigurationFactory(this);
  public final PythonConfigurationFactoryBase LEGACY_NOSETEST_FACTORY = new PythonLegacyNoseTestConfigurationFactory(this);
  public final PythonConfigurationFactoryBase LEGACY_PYTEST_FACTORY = new PythonLegacyPyTestConfigurationFactory(this);

  public static PythonTestConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(PythonTestConfigurationType.class);
  }

  public PythonTestConfigurationType() {
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


  private static class PythonLegacyUnitTestConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonLegacyUnitTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @NotNull
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

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PythonDocTestRunConfiguration(project, this);
    }

    @Override
    public String getName() {
      return PyBundle.message("runcfg.doctest.display_name");
    }
  }

  private static class PythonLegacyPyTestConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonLegacyPyTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new PyTestRunConfiguration(project, this);
    }

    @Override
    public String getName() {
      return PyBundle.message("runcfg.pytest.display_name");
    }
  }

  private static class PythonLegacyNoseTestConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonLegacyNoseTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @NotNull
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
    // Use new or legacy factories depending to new config
    final ConfigurationFactory[] factories = PyTestLegacyInteropKt.isNewTestsModeEnabled()
                                             ? PyTestsSharedKt.getFactories()
                                             : new ConfigurationFactory[]
                                               {LEGACY_UNITTEST_FACTORY, LEGACY_NOSETEST_FACTORY, LEGACY_PYTEST_FACTORY};
    return ObjectArrays.concat(factories, PY_DOCTEST_FACTORY);
  }
}
