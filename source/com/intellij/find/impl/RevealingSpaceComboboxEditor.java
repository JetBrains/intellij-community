/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Sep 7, 2007
 * Time: 1:45:27 PM
 */
package com.intellij.find.impl;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.ui.StringComboboxEditor;

import javax.swing.*;

public class RevealingSpaceComboboxEditor extends StringComboboxEditor {
  public RevealingSpaceComboboxEditor(final Project project) {
    super(project, StdFileTypes.PLAIN_TEXT);

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        getEditor().getSettings().setWhitespacesShown(true);
      }
    });
  }

  public void setItem(Object anObject) {
    super.setItem(anObject);
    if (getEditor() != null) {
      getEditor().getSettings().setWhitespacesShown(true);
    }
  }
}