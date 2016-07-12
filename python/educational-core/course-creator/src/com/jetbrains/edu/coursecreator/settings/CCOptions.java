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
      myHtmlRadioButton.requestFocus();
    }
    else {
      myMarkdownRadioButton.setSelected(true);
      myMarkdownRadioButton.requestFocus();
    }
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return myIsModified;
  }

  @Override
  public void apply() throws ConfigurationException {
    CCSettings.getInstance().setUseHtmlAsDefaultTaskFormat(myHtmlRadioButton.isSelected());
  }

  @Override
  public void reset() {
    createComponent();    
  }

  @Override
  public void disposeUIResources() {

  }
}
