// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.icons.PythonIcons;
import com.jetbrains.python.run.PythonConfigurationFactoryBase;
import com.jetbrains.python.testing.autoDetectTests.PyAutoDetectionConfigurationFactory;
import com.jetbrains.python.testing.doctest.PythonDocTestRunConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class PythonTestConfigurationType extends ConfigurationTypeBase {
  public static final String ID = "tests";

  private final PythonConfigurationFactoryBase myDocTestFactory = new PythonDocTestConfigurationFactory(this);
  private final PyAutoDetectionConfigurationFactory myAutoFactory = new PyAutoDetectionConfigurationFactory(this);
  private final PyUnitTestFactory myUnitTestFactory = new PyUnitTestFactory(this);
  private final PyTestFactory myPyTestFactory = new PyTestFactory(this);

  private final List<PyAbstractTestFactory<?>> myTypedFactories;

  public static @NotNull PythonTestConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(PythonTestConfigurationType.class);
  }

  public PythonTestConfigurationType() {
    super(ID, PyBundle.message("runcfg.test.display_name"), PyBundle.message("runcfg.test.description"),
          NotNullLazyValue.createValue(() -> PythonIcons.Python.PythonTests));

    myTypedFactories = List.of(
      myAutoFactory,
      myPyTestFactory,
      new PyNoseTestFactory(this),
      new PyTrialTestFactory(this),
      myUnitTestFactory
    );
    for (var factory : myTypedFactories) {
      addFactory(factory);
    }
    addFactory(myDocTestFactory);
  }

  public @NotNull PyTestFactory getPyTestFactory() {
    return myPyTestFactory;
  }

  public @NotNull PythonConfigurationFactoryBase getDocTestFactory() {
    return myDocTestFactory;
  }

  public @NotNull PyUnitTestFactory getUnitTestFactory() {
    return myUnitTestFactory;
  }

  public @NotNull PyAutoDetectionConfigurationFactory getAutoDetectFactory() {
    return myAutoFactory;
  }

  public @NotNull List<PyAbstractTestFactory<?>> getTypedFactories() {
    return myTypedFactories;
  }

  @Override
  public String getHelpTopic() {
    return "reference.dialogs.rundebug.tests";
  }

  @Override
  public @NotNull String getTag() {
    return "pythonTest";
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }


  private static class PythonDocTestConfigurationFactory extends PythonConfigurationFactoryBase {
    protected PythonDocTestConfigurationFactory(ConfigurationType configurationType) {
      super(configurationType);
    }

    @Override
    public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new PythonDocTestRunConfiguration(project, this);
    }

    @Override
    public @NotNull String getName() {
      return PyBundle.message("runcfg.doctest.display_name");
    }

    @Override
    public @NotNull String getId() {
      return "Doctests";
    }
  }
}
