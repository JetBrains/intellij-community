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

import com.jetbrains.python.PyBundle;

import javax.swing.*;
import javax.swing.event.ListDataListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: catherine
 */
public class PythonTestConfigurationsModel implements ComboBoxModel {
  public static final String PYTHONS_UNITTEST_NAME = PyBundle.message("runcfg.unittest.display_name");
  public static final String PYTHONS_NOSETEST_NAME = PyBundle.message("runcfg.nosetests.display_name");
  public static final String PY_TEST_NAME = PyBundle.message("runcfg.pytest.display_name");
  private static final PythonTestConfigurationsModel INSTANCE = new PythonTestConfigurationsModel();

  private final List<String> myConfigurationTypes = new ArrayList<String>();
  private Set<ListDataListener> myListDataListeners = new HashSet<ListDataListener>();

  private String myDefault;
  private String myProjectConfiguration;
  private String myGlobalSelected;

  private PythonTestConfigurationsModel() {
    myDefault = PYTHONS_UNITTEST_NAME;
    myProjectConfiguration = PYTHONS_UNITTEST_NAME;
    myGlobalSelected = myDefault;
  }

  public void addConfiguration(final String newConfiguration, boolean changeSelection) {
    myConfigurationTypes.add(newConfiguration);
    if (changeSelection) {
      setSelectedItem(newConfiguration);
    }
  }

  public void reset() {
    myDefault = myProjectConfiguration;
    setSelectedItem(myProjectConfiguration);
  }

  public void apply() {
    myProjectConfiguration = myGlobalSelected;
  }

  @Override
  public void setSelectedItem(Object o) {
    if (myGlobalSelected != o) {
      myGlobalSelected = (String)o;
    }
  }

  @Override
  public Object getSelectedItem() {
    return myGlobalSelected;
  }

  @Override
  public int getSize() {
    return myConfigurationTypes.size();
  }

  @Override
  public Object getElementAt(int i) {
    return myConfigurationTypes.get(i);
  }

  @Override
  public void addListDataListener(ListDataListener listDataListener) {
    myListDataListeners.add(listDataListener);
  }

  @Override
  public void removeListDataListener(ListDataListener listDataListener) {
    myListDataListeners.remove(listDataListener);
  }

  public static PythonTestConfigurationsModel getInstance() {
    return INSTANCE;
  }

  public Object getProjectConfiguration() {
    return myProjectConfiguration;
  }
}
