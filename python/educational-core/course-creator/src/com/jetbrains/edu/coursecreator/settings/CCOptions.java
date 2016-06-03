package com.jetbrains.edu.coursecreator.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.jetbrains.edu.learning.settings.StudyOptionsProvider;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CCOptions implements StudyOptionsProvider {
  private JRadioButton myHtmlRadioButton;
  private JRadioButton myMarkdownRadioButton;
  private JPanel myPanel;
  private boolean myIsModified = false;


  public CCOptions() {
    myHtmlRadioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myIsModified = true;
      }
    });
    
    myMarkdownRadioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myIsModified = true;
      }
    });
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (CCSettings.getInstance().useHtmlAsDefaultTaskFormat()) {
      myHtmlRadioButton.setSelected(true);
      myMarkdownRadioButton.setSelected(false);
    }
    else {
      myHtmlRadioButton.setSelected(false);
      myMarkdownRadioButton.setSelected(true);
    }
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return myIsModified;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myHtmlRadioButton.isSelected()) {
      CCSettings.getInstance().setUseHtmlAsDefaultTaskFormat(true);
    }
    else if (myMarkdownRadioButton.isSelected()) {
      CCSettings.getInstance().setUseHtmlAsDefaultTaskFormat(false);
    }
  }

  @Override
  public void reset() {
  }

  @Override
  public void disposeUIResources() {

  }
}
