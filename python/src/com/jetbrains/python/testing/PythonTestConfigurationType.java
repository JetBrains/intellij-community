// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing;

import com.intellij.execution.configurations.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.run.PythonConfigurationFactoryBase;
import com.jetbrains.python.testing.doctest.PythonDocTestRunConfiguration;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;

public final class PythonTestConfigurationType extends ConfigurationTypeBase {
  public static final String ID = "tests";

  @SuppressWarnings("SpellCheckingInspection")
  public final PythonConfigurationFactoryBase PY_DOCTEST_FACTORY = new PythonDocTestConfigurationFactory(this);

  // due to PyTestLegacyInterop must be lazy
  private final NotNullLazyValue<ConfigurationFactory[]> myFactories = NotNullLazyValue.createValue(() -> {
    // use new or legacy factories depending to new config
    for (PythonConfigurationFactoryBase factory : PyTestsSharedKt.getPythonFactories()) {
      addFactory(factory);
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

  @Override
  public boolean isDumbAware() {
    return true;
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

    @Override
    public @NotNull String getId() {
      //noinspection SpellCheckingInspection
      return "Doctests";
    }
  }
}
