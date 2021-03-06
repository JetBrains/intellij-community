// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@State(name = "PyTestFrameworkService", storages = @Storage("other.xml"))
public class PyTestFrameworkService implements PersistentStateComponent<PyTestFrameworkService> {

  public static PyTestFrameworkService getInstance() {
    return ApplicationManager.getApplication().getService(PyTestFrameworkService.class);
  }

  public Map<String, Boolean> SDK_TO_PYTEST = new HashMap<>();
  public Map<String, Boolean> SDK_TO_NOSETEST = new HashMap<>();
  public Map<String, Boolean> SDK_TO_TRIALTEST = new HashMap<>();

  private static final String[] FRAMEWORK_NAMES = {PyNames.PY_TEST, PyNames.NOSE_TEST, PyNames.TRIAL_TEST};

  @Override
  public PyTestFrameworkService getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PyTestFrameworkService state) {
    XmlSerializerUtil.copyBean(state, this);
  }


  @NotNull
  public static Set<String> getFrameworkNamesSet() {
    return ContainerUtil.newHashSet(FRAMEWORK_NAMES);
  }

  public static String @NotNull [] getFrameworkNamesArray() {
    return FRAMEWORK_NAMES.clone();
  }

  @NotNull
  public static @Nls String getSdkReadableNameByFramework(@NotNull final String frameworkName) {
    switch (frameworkName) {
      case PyNames.PY_TEST: {
        return PyBundle.message("runcfg.pytest.display_name");
      }
      case PyNames.NOSE_TEST: {
        return PyBundle.message("runcfg.nosetests.display_name");
      }
      case PyNames.TRIAL_TEST: {
        return PyBundle.message("runcfg.trial.display_name");
      }
    }
    throw new IllegalArgumentException("Unknown framework " + frameworkName);
  }

  @NotNull
  Map<String, Boolean> getSdkToTestRunnerByName(@NotNull final String frameworkName) {
    switch (frameworkName) {
      case PyNames.PY_TEST: {
        return SDK_TO_PYTEST;
      }
      case PyNames.NOSE_TEST: {
        return SDK_TO_NOSETEST;
      }
      case PyNames.TRIAL_TEST: {
        return SDK_TO_TRIALTEST;
      }
    }
    throw new IllegalArgumentException("Unknown framework " + frameworkName);
  }
}
