/*
 * User: anna
 * Date: 14-Feb-2008
 */
package com.intellij.application.options.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;

import javax.swing.*;

public class JavaEditorOptions implements EditorOptionsProvider {
  private static final String INSERT_IMPORTS_ALWAYS = ApplicationBundle.message("combobox.insert.imports.all");
  private static final String INSERT_IMPORTS_ASK = ApplicationBundle.message("combobox.insert.imports.ask");
  private static final String INSERT_IMPORTS_NONE = ApplicationBundle.message("combobox.insert.imports.none");


  private JCheckBox myCbInsertJavadocStubOnEnter;
  private JComboBox mySmartPasteCombo;
  private JCheckBox myCbRenameLocalVariablesInplace;
  private JCheckBox myCbShowImportPopup;
  private JPanel myWholePanel;

  public JavaEditorOptions() {
    mySmartPasteCombo.addItem(INSERT_IMPORTS_ALWAYS);
    mySmartPasteCombo.addItem(INSERT_IMPORTS_ASK);
    mySmartPasteCombo.addItem(INSERT_IMPORTS_NONE);
  }

  public void reset() {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    DaemonCodeAnalyzerSettings daemonSettings = DaemonCodeAnalyzerSettings.getInstance();

    switch (codeInsightSettings.ADD_IMPORTS_ON_PASTE) {
      case CodeInsightSettings.YES:
        mySmartPasteCombo.setSelectedItem(INSERT_IMPORTS_ALWAYS);
        break;

      case CodeInsightSettings.NO:
        mySmartPasteCombo.setSelectedItem(INSERT_IMPORTS_NONE);
        break;

      case CodeInsightSettings.ASK:
        mySmartPasteCombo.setSelectedItem(INSERT_IMPORTS_ASK);
        break;
    }
    myCbInsertJavadocStubOnEnter.setSelected(codeInsightSettings.JAVADOC_STUB_ON_ENTER);

    // Refactoring
    myCbRenameLocalVariablesInplace.setSelected(editorSettings.isVariableInplaceRenameEnabled());

    myCbShowImportPopup.setSelected(daemonSettings.isImportHintEnabled());
  }

  public void disposeUIResources() {

  }

  public void apply() {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    DaemonCodeAnalyzerSettings daemonSettings = DaemonCodeAnalyzerSettings.getInstance();

    codeInsightSettings.JAVADOC_STUB_ON_ENTER = myCbInsertJavadocStubOnEnter.isSelected();
    editorSettings.setVariableInplaceRenameEnabled(myCbRenameLocalVariablesInplace.isSelected());
    codeInsightSettings.ADD_IMPORTS_ON_PASTE = getSmartPasteValue();
    daemonSettings.setImportHintEnabled(myCbShowImportPopup.isSelected());
  }

  public JComponent createComponent() {
    return myWholePanel;
  }

  public boolean isModified() {
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    CodeInsightSettings codeInsightSettings = CodeInsightSettings.getInstance();
    DaemonCodeAnalyzerSettings daemonSettings = DaemonCodeAnalyzerSettings.getInstance();

    boolean isModified = isModified(myCbInsertJavadocStubOnEnter, codeInsightSettings.JAVADOC_STUB_ON_ENTER);
    // Refactoring
    isModified |= isModified(myCbRenameLocalVariablesInplace, editorSettings.isVariableInplaceRenameEnabled());

    isModified |= isModified(myCbShowImportPopup, daemonSettings.isImportHintEnabled());
    
    isModified |= getSmartPasteValue() != codeInsightSettings.ADD_IMPORTS_ON_PASTE;
    return isModified;
  }

  private int getSmartPasteValue() {
    Object selectedItem = mySmartPasteCombo.getSelectedItem();
    if (INSERT_IMPORTS_ALWAYS.equals(selectedItem)) {
      return CodeInsightSettings.YES;
    }
    else if (INSERT_IMPORTS_NONE.equals(selectedItem)) {
      return CodeInsightSettings.NO;
    }
    else {
      return CodeInsightSettings.ASK;
    }
  }

  private static boolean isModified(JToggleButton checkBox, boolean value) {
    return checkBox.isSelected() != value;
  }

  public String getDisplayName() {
    return "JAVA";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }
}