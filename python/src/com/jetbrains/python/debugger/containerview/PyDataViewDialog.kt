// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.JBUI;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.PyDebugValue;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PyDataViewDialog extends DialogWrapper {
  private final JPanel myMainPanel;
  private static final int TABLE_DEFAULT_WIDTH = 700;
  private static final int TABLE_DEFAULT_HEIGHT = 500;
  private final Project myProject;
  private final PyDataViewerPanel myDataViewerPanel;

  public PyDataViewDialog(@NotNull Project project, final @NotNull PyDebugValue value) {
    super(project, false);
    myProject = project;
    setModal(false);
    setCancelButtonText(PyBundle.message("debugger.data.view.close"));
    setCrossClosesWindow(true);

    myMainPanel = new JPanel(new GridBagLayout());
    myDataViewerPanel = new PyDataViewerPanel(project, value.getFrameAccessor());
    myDataViewerPanel.apply(value, false, /* commandSource = */ null);
    myDataViewerPanel.setPreferredSize(JBUI.size(TABLE_DEFAULT_WIDTH, TABLE_DEFAULT_HEIGHT));

    myMainPanel.add(myDataViewerPanel, createDataViewPanelConstraints());

    myDataViewerPanel.addListener(new PyDataViewerPanel.Listener() {
      @Override
      public void onNameChanged(@NlsContexts.TabTitle @NotNull String name) {
        setTitle(name);
      }
    });

    addBottomElements();

    setTitle(value.getFullName());
    init();
  }

  protected void addBottomElements() {
    final JBCheckBox colored = new JBCheckBox(PyBundle.message("debugger.data.view.colored.cells"));
    final JBCheckBox resize = new JBCheckBox(PyBundle.message("debugger.data.view.resize.automatically"));

    resize.setSelected(PyDataView.isAutoResizeEnabled(myProject));
    colored.setSelected(PyDataView.isColoringEnabled(myProject));

    colored.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDataViewerPanel.setColored(colored.isSelected());
        myDataViewerPanel.updateUI();
      }
    });
    resize.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myDataViewerPanel.resize(resize.isSelected());
        myDataViewerPanel.updateUI();
      }
    });

    GridBagConstraints checkBoxConstraints = createCheckBoxConstraints();
    checkBoxConstraints.gridy = 1;
    myMainPanel.add(colored, checkBoxConstraints);
    checkBoxConstraints.gridy = 2;
    myMainPanel.add(resize, checkBoxConstraints);
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getCancelAction()};
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  protected GridBagConstraints createDataViewPanelConstraints() {
    GridBagConstraints c = new GridBagConstraints();
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 0.9;
    c.weighty = 0.9;
    return c;
  }

  protected GridBagConstraints createCheckBoxConstraints() {
    GridBagConstraints c = new GridBagConstraints();
    c.weighty = 0.05;
    c.anchor = GridBagConstraints.LINE_START;
    return c;
  }
}
