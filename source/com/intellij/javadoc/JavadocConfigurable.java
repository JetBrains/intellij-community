package com.intellij.javadoc;

import com.intellij.openapi.options.Configurable;
import com.intellij.psi.PsiKeyword;

import javax.swing.*;
import java.io.File;

final class JavadocConfigurable implements Configurable {
  private JavadocGenerationPanel myPanel;
  private JavadocConfiguration myConfiguration;

  public JavadocConfigurable(JavadocConfiguration configuration) {
    myConfiguration = configuration;
  }

  public JComponent createComponent() {
    myPanel = new JavadocGenerationPanel();
    return myPanel.myPanel;
  }

  public void applyTo(JavadocConfiguration configuration) {
    configuration.OUTPUT_DIRECTORY = toSystemIndependentFormat(myPanel.myTfOutputDir.getText());
    configuration.OTHER_OPTIONS = convertString(myPanel.myOtherOptionsField.getText());
    configuration.HEAP_SIZE = convertString(myPanel.myHeapSizeField.getText());
    configuration.LOCALE = convertString(myPanel.myLocaleTextField.getText());
    configuration.OPEN_IN_BROWSER = myPanel.myOpenInBrowserCheckBox.isSelected();
    configuration.OPTION_SCOPE = convertString(myPanel.getScope());
    configuration.OPTION_HIERARCHY = myPanel.myHierarchy.isSelected();
    configuration.OPTION_NAVIGATOR = myPanel.myNavigator.isSelected();
    configuration.OPTION_INDEX = myPanel.myIndex.isSelected();
    configuration.OPTION_SEPARATE_INDEX = myPanel.mySeparateIndex.isSelected();
    configuration.OPTION_DOCUMENT_TAG_USE = myPanel.myTagUse.isSelected();
    configuration.OPTION_DOCUMENT_TAG_AUTHOR = myPanel.myTagAuthor.isSelected();
    configuration.OPTION_DOCUMENT_TAG_VERSION = myPanel.myTagVersion.isSelected();
    configuration.OPTION_DOCUMENT_TAG_DEPRECATED = myPanel.myTagDeprecated.isSelected();
    configuration.OPTION_DEPRECATED_LIST = myPanel.myDeprecatedList.isSelected();
  }

  public void loadFrom(JavadocConfiguration configuration) {
    myPanel.myTfOutputDir.setText(toUserSystemFormat(configuration.OUTPUT_DIRECTORY));
    myPanel.myOtherOptionsField.setText(configuration.OTHER_OPTIONS);
    myPanel.myHeapSizeField.setText(configuration.HEAP_SIZE);
    myPanel.myLocaleTextField.setText(configuration.LOCALE);
    myPanel.myOpenInBrowserCheckBox.setSelected(configuration.OPEN_IN_BROWSER);
    myPanel.setScope(configuration.OPTION_SCOPE);
    myPanel.myHierarchy.setSelected(configuration.OPTION_HIERARCHY);
    myPanel.myNavigator.setSelected(configuration.OPTION_NAVIGATOR);
    myPanel.myIndex.setSelected(configuration.OPTION_INDEX);
    myPanel.mySeparateIndex.setSelected(configuration.OPTION_SEPARATE_INDEX);
    myPanel.myTagUse.setSelected(configuration.OPTION_DOCUMENT_TAG_USE);
    myPanel.myTagAuthor.setSelected(configuration.OPTION_DOCUMENT_TAG_AUTHOR);
    myPanel.myTagVersion.setSelected(configuration.OPTION_DOCUMENT_TAG_VERSION);
    myPanel.myTagDeprecated.setSelected(configuration.OPTION_DOCUMENT_TAG_DEPRECATED);
    myPanel.myDeprecatedList.setSelected(configuration.OPTION_DEPRECATED_LIST);

    myPanel.mySeparateIndex.setEnabled(myPanel.myIndex.isSelected());
    myPanel.myDeprecatedList.setEnabled(myPanel.myTagDeprecated.isSelected());
  }

  public boolean isModified() {
    boolean isModified;

    final JavadocConfiguration configuration = myConfiguration;
    isModified = !compareStrings(myPanel.myTfOutputDir.getText(), toUserSystemFormat(configuration.OUTPUT_DIRECTORY));
    isModified |= !compareStrings(myPanel.myOtherOptionsField.getText(), configuration.OTHER_OPTIONS);
    isModified |= !compareStrings(myPanel.myHeapSizeField.getText(), configuration.HEAP_SIZE);
    isModified |= myPanel.myOpenInBrowserCheckBox.isSelected() != configuration.OPEN_IN_BROWSER;
    isModified |= !compareStrings(myPanel.getScope(), (configuration.OPTION_SCOPE == null ? PsiKeyword.PROTECTED : configuration.OPTION_SCOPE));
    isModified |= myPanel.myHierarchy.isSelected() != configuration.OPTION_HIERARCHY;
    isModified |= myPanel.myNavigator.isSelected() != configuration.OPTION_NAVIGATOR;
    isModified |= myPanel.myIndex.isSelected() != configuration.OPTION_INDEX;
    isModified |= myPanel.mySeparateIndex.isSelected() != configuration.OPTION_SEPARATE_INDEX;
    isModified |= myPanel.myTagUse.isSelected() != configuration.OPTION_DOCUMENT_TAG_USE;
    isModified |= myPanel.myTagAuthor.isSelected() != configuration.OPTION_DOCUMENT_TAG_AUTHOR;
    isModified |= myPanel.myTagVersion.isSelected() != configuration.OPTION_DOCUMENT_TAG_VERSION;
    isModified |= myPanel.myTagDeprecated.isSelected() != configuration.OPTION_DOCUMENT_TAG_DEPRECATED;
    isModified |= myPanel.myDeprecatedList.isSelected() != configuration.OPTION_DEPRECATED_LIST;

    return isModified;
  }

  public final void apply() {
    applyTo(myConfiguration);
  }

  public void reset() {
    loadFrom(myConfiguration);
  }

  private static boolean compareStrings(String string1, String string2) {
    if (string1 == null) {
      string1 = "";
    }
    if (string2 == null) {
      string2 = "";
    }
    return string1.equals(string2);
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  private static String convertString(String s) {
    if (s != null && s.trim().length() == 0) {
      return null;
    }
    return s;
  }

  private static String toSystemIndependentFormat(String directory) {
    if (directory.length() == 0) {
      return null;
    }
    return directory.replace(File.separatorChar, '/');
  }

  private static String toUserSystemFormat(String directory) {
    if (directory == null) {
      return "";
    }
    return directory.replace('/', File.separatorChar);
  }

  public String getDisplayName() {
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "project.propJavaDoc";
  }

  public String getOutputDir() {
    return myPanel.myTfOutputDir.getText();
  }
}