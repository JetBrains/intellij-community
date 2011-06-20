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

package com.jetbrains.python.testing;

import com.intellij.openapi.project.Project;
import com.intellij.ui.CollectionComboBoxModel;
import com.jetbrains.python.PyBundle;

import java.util.List;

/**
 * User: catherine
 */

public class PythonTestConfigurationsModel extends CollectionComboBoxModel {
  public static final String PYTHONS_UNITTEST_NAME = PyBundle.message("runcfg.unittest.display_name");
  public static final String PYTHONS_NOSETEST_NAME = PyBundle.message("runcfg.nosetests.display_name");
  public static final String PY_TEST_NAME = PyBundle.message("runcfg.pytest.display_name");
  public static final String PYTHONS_ATTEST_NAME = PyBundle.message("runcfg.attest.display_name");

  private String myProjectConfiguration;
  private Project myProject;

  public PythonTestConfigurationsModel(final List items, final Object selection, Project project) {
    super(items, selection);
    myProject = project;
    myProjectConfiguration = TestRunnerService.getInstance(myProject).getProjectConfiguration();
  }
  public void reset() {
    setSelectedItem(myProjectConfiguration);
  }

  public void apply() {
    myProjectConfiguration = (String)getSelectedItem();
    TestRunnerService.getInstance(myProject).setProjectConfiguration(myProjectConfiguration);
  }

  public Object getProjectConfiguration() {
    return myProjectConfiguration;
  }
}
