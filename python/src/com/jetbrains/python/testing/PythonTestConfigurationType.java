// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing;

import com.intellij.execution.configurations.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.PythonConfigurationFactoryBase;
import com.jetbrains.python.testing.doctest.PythonDocTestRunConfiguration;
import com.jetbrains.python.testing.nosetestLegacy.PythonNoseTestRunConfiguration;
import com.jetbrains.python.testing.pytestLegacy.PyTestRunConfiguration;
import com.jetbrains.python.testing.unittestLegacy.PythonUnitTestRunConfiguration;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

/**
 * User : catherine
 * <p>
 * This type is used both with Legacy and New test runners.
 * {@link PyTestLegacyInteropKt} is used to support legacy. To drop legacy support, remove all code that depends on it.
 */
public final class PythonTestConfigurationType extends ConfigurationTypeBase {
  public static final String ID = "tests";

  @SuppressWarnings("SpellCheckingInspection")
  public final PythonConfigurationFactoryBase PY_DOCTEST_FACTORY = new PythonDocTestConfigurationFactory(this);
  public final PythonConfigurationFactoryBase LEGACY_UNITTEST_FACTORY = new PythonLegacyUnitTestConfigurationFactory(this);
  @SuppressWarnings("SpellCheckingInspection")
  public final PythonConfigurationFactoryBase LEGACY_NOSETEST_FACTORY = new PythonLegacyNoseTestConfigurationFactory(this);
  @SuppressWarnings("SpellCheckingInspection")
  public final PythonConfigurationFactoryBase LEGACY_PYTEST_FACTORY = new PythonLegacyPyTestConfigurationFactory(this);

  // due to PyTestLegacyInterop must be lazy
  private final NotNullLazyValue<ConfigurationFactory[]> myFactories = NotNullLazyValue.createValue(() -> {
    // use new or legacy factories depending to new config
    if (PyTestLegacyInteropKt.isNewTestsModeEnabled()) {
      for (PythonConfigurationFactoryBase factory : PyTestsSharedKt.getPythonFactories()) {
        addFactory(factory);
      }
    }
    else {
      addFactory(LEGACY_UNITTEST_FACTORY);
      addFactory(LEGACY_NOSETEST_FACTORY);
      addFactory(LEGACY_PYTEST_FACTORY);
    }
    addFactory(PY_DOCTEST_FACTORY);
    return super.getConfigurationFactories();
  });

  @NotNull
  public static PythonTestConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(PythonTestConfigurationType.class);
  }

  public PythonTestConfigurationType() {
    //noinspection SpellCheckingInspection
    super(ID, PyBundle.message("runcfg.test.display_name"), PyBundle.message("runcfg.test.description"),
          NotNullLazyValue.createValue(() -> PythonIcons.Python.PythonTests));
  }

  @NotNull
  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return myFactories.getValue();
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.tests";
  }

  @NotNull
  @Override
  public String getTag() {
    return "pythonTest";
  }

  private static class PythonLegacyUnitTestConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonLegacyUnitTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new PythonUnitTestRunConfiguration(project, this);
    }

    @NotNull
    @Override
    public String getName() {
      //noinspection SpellCheckingInspection
      return PyBundle.message("runcfg.unittest.display_name");
    }
  }

  private static class PythonDocTestConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonDocTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new PythonDocTestRunConfiguration(project, this);
    }

    @NotNull
    @Override
    public String getName() {
      //noinspection SpellCheckingInspection
      return PyBundle.message("runcfg.doctest.display_name");
    }
  }

  private static class PythonLegacyPyTestConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonLegacyPyTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new PyTestRunConfiguration(project, this);
    }

    @NotNull
    @Override
    public String getName() {
      //noinspection SpellCheckingInspection
      return PyBundle.message("runcfg.pytest.display_name");
    }
  }

  private static class PythonLegacyNoseTestConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonLegacyNoseTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new PythonNoseTestRunConfiguration(project, this);
    }

    @NotNull
    @Override
    public String getName() {
      //noinspection SpellCheckingInspection
      return PyBundle.message("runcfg.nosetests.display_name");
    }
  }
}
