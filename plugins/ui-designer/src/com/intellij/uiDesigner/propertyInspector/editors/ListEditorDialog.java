// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

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

  @Override
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
