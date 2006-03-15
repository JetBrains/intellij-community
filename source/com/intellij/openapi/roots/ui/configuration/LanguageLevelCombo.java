package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: ven
 * Date: Mar 13, 2006
 * Time: 4:16:57 PM
 * To change this template use File | Settings | File Templates.
 */
public class LanguageLevelCombo extends ComboBox {
  public LanguageLevelCombo(Project project) {
    addItem(LanguageLevel.JDK_1_3);
    addItem(LanguageLevel.JDK_1_4);
    addItem(LanguageLevel.JDK_1_5);
    setRenderer(new MyDefaultListCellRenderer());
    setSelectedItem(ProjectRootManagerEx.getInstanceEx(project).getLanguageLevel());
  }


  public LanguageLevel getSelectedItem() {
    return (LanguageLevel)super.getSelectedItem();
  }

  private static class MyDefaultListCellRenderer extends DefaultListCellRenderer {
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      setText(((LanguageLevel)value).getPresentableText());
      return this;
    }
  }
}
