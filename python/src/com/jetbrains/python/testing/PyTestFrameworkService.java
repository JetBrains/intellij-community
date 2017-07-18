/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

@State(name = "PyTestFrameworkService", storages = @Storage("other.xml"))
public class PyTestFrameworkService implements PersistentStateComponent<PyTestFrameworkService> {

  public static PyTestFrameworkService getInstance() {
    return ServiceManager.getService(PyTestFrameworkService.class);
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
  public void loadState(PyTestFrameworkService state) {
    XmlSerializerUtil.copyBean(state, this);
  }


  @NotNull
  public static Set<String> getFrameworkNamesSet() {
    return ContainerUtil.newHashSet(FRAMEWORK_NAMES);
  }

  @NotNull
  public static String[] getFrameworkNamesArray() {
    return FRAMEWORK_NAMES.clone();
  }

  /**
   * @return pypi package that contains this framework
   */
  @NotNull
  public static String getPackageByFramework(@NotNull final String frameworkName) {
    if (frameworkName.equals(PyNames.TRIAL_TEST)) {
      return "Twisted";
    }
    return frameworkName;
  }

  @NotNull
  public static String getSdkReadableNameByFramework(@NotNull final String frameworkName) {
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
