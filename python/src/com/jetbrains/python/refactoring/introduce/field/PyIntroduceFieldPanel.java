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
