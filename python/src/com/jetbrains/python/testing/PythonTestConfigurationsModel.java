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

import com.intellij.openapi.module.Module;
import com.intellij.ui.CollectionComboBoxModel;
import com.jetbrains.python.PyBundle;

import java.util.List;


public class PythonTestConfigurationsModel extends CollectionComboBoxModel {
  public static final String PYTHONS_UNITTEST_NAME = PyBundle.message("runcfg.unittest.display_name");

  private String myTestRunner;
  private Module myModule;

  public PythonTestConfigurationsModel(final List items, final Object selection, Module module) {
    super(items, selection);
    myModule = module;
    myTestRunner = TestRunnerService.getInstance(myModule).getProjectConfiguration();
  }
  public void reset() {
    setSelectedItem(myTestRunner);
  }

  public void apply() {
    myTestRunner = (String)getSelectedItem();
    TestRunnerService.getInstance(myModule).setProjectConfiguration(myTestRunner);
  }

  public Object getTestRunner() {
    return myTestRunner;
  }
}
