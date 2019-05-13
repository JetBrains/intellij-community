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
package com.jetbrains.python.refactoring.introduce.field;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.introduce.inplace.KeyboardComboSwitcher;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;

import javax.swing.*;
import java.util.EnumSet;

/**
 * @author yole
 */
public class PyIntroduceFieldPanel {
  private JPanel myRootPanel;
  private JComboBox myInitializerPlaceCombo;

  public PyIntroduceFieldPanel(Project project, EnumSet<IntroduceHandler.InitPlace> initPlaces) {
    KeyboardComboSwitcher.setupActions(myInitializerPlaceCombo, project);
    if (initPlaces.contains(IntroduceHandler.InitPlace.SET_UP)) {
      ((DefaultComboBoxModel) myInitializerPlaceCombo.getModel()).addElement("setUp() method");
    }
  }

  public JPanel getRootPanel() {
    return myRootPanel;
  }

  public IntroduceHandler.InitPlace getInitPlace() {
    final int index = myInitializerPlaceCombo.getSelectedIndex();
    switch (index) {
      case 1:
        return IntroduceHandler.InitPlace.CONSTRUCTOR;
      case 2:
        return IntroduceHandler.InitPlace.SET_UP;
      default:
        return IntroduceHandler.InitPlace.SAME_METHOD;
    }
  }
}
