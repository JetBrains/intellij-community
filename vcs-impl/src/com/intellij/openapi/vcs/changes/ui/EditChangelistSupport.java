package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.extensions.ExtensionPointName;

import javax.swing.*;
import javax.swing.text.JTextComponent;

/**
 * @author Dmitry Avdeev
 */
public interface EditChangelistSupport {

  ExtensionPointName<EditChangelistSupport> EP_NAME = ExtensionPointName.create("com.intellij.editChangelistSupport");

  void installSearch(JTextComponent name, JTextComponent comment);
  void addControls(JPanel bottomPanel);
}
