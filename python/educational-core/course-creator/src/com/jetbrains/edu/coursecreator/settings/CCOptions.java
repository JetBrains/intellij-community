package com.jetbrains.edu.coursecreator.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.jetbrains.edu.learning.settings.StudyOptionsProvider;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CCOptions implements StudyOptionsProvider {
  private JRadioButton myHtmlRadioButton;
  private JRadioButton myMarkdownRadioButton;
  private JPanel myPanel;

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
    final boolean htmlAsDefaultTaskFormat = CCSettings.getInstance().useHtmlAsDefaultTaskFormat();
    return myHtmlRadioButton.isSelected() != htmlAsDefaultTaskFormat;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (isModified()) {
      CCSettings.getInstance().setUseHtmlAsDefaultTaskFormat(myHtmlRadioButton.isSelected());
    }
  }

  @Override
  public void reset() {
    createComponent();    
  }

  @Override
  public void disposeUIResources() {

  }
}
