/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author yole
 */
public class ListEditorDialog extends DialogWrapper {
  private JPanel myRootPanel;
  private JTextArea myLinesTextArea;

  protected ListEditorDialog(final Project project, String propertyName) {
    super(project, true);
    init();
    setTitle(UIDesignerBundle.message("list.editor.title", propertyName));
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myRootPanel;
  }

  @Override @NonNls
  protected String getDimensionServiceKey() {
    return "UIDesigner.ListEditorDialog";
  }

  public String[] getValue() {
    final String text = myLinesTextArea.getText();
    if (text.length() == 0) return ArrayUtil.EMPTY_STRING_ARRAY;
    return text.split("\n");
  }

  public void setValue(final String[] value) {
    myLinesTextArea.setText(value == null ? "" : StringUtil.join(value, "\n"));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myLinesTextArea;
  }
}
