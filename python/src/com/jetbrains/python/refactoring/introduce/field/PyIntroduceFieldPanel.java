// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.introduce.field;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.introduce.inplace.KeyboardComboSwitcher;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.refactoring.introduce.IntroduceHandler;

import javax.swing.*;
import java.util.EnumSet;


public class PyIntroduceFieldPanel {
  private JPanel myRootPanel;
  private JComboBox myInitializerPlaceCombo;

  public PyIntroduceFieldPanel(Project project, EnumSet<IntroduceHandler.InitPlace> initPlaces) {
    KeyboardComboSwitcher.setupActions(myInitializerPlaceCombo, project);
    if (initPlaces.contains(IntroduceHandler.InitPlace.SET_UP)) {
      ((DefaultComboBoxModel) myInitializerPlaceCombo.getModel()).addElement(PyBundle.message("refactoring.introduce.field.setup.method"));
    }
  }

  public JPanel getRootPanel() {
    return myRootPanel;
  }

  public IntroduceHandler.InitPlace getInitPlace() {
    final int index = myInitializerPlaceCombo.getSelectedIndex();
    return switch (index) {
      case 1 -> IntroduceHandler.InitPlace.CONSTRUCTOR;
      case 2 -> IntroduceHandler.InitPlace.SET_UP;
      default -> IntroduceHandler.InitPlace.SAME_METHOD;
    };
  }
}
