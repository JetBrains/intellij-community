/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.svn.SvnBundle;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AddRepositoryLocationDialog extends DialogWrapper {
  private final List<String> myPreviousLocations;
  private JComboBox myCombo;
  private String mySelected;
  private JTextField myComboField;

  public AddRepositoryLocationDialog(final Project project, final List<String> values) {
    super(project, true);
    myPreviousLocations = new ArrayList<>(values);
    Collections.sort(myPreviousLocations);

    setTitle(getTitle());
    init();
    myComboField.setText(initText());
  }

  @Override
  public String getTitle() {
    return SvnBundle.message("repository.browser.add.location.title");
  }

  protected String initText() {
    return "http://";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCombo;
  }

  protected JComponent createCenterPanel() {
    final JLabel selectText = new JLabel(SvnBundle.message("repository.browser.add.location.prompt"));
    selectText.setUI(new MultiLineLabelUI());

    final JPanel mainPanel = new JPanel(new GridBagLayout());
    final GridBagConstraints gb =
      new GridBagConstraints(0, 0, 1, 1, 1, 0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(5, 5, 5, 5), 0, 0);

    mainPanel.add(selectText, gb);

    ++ gb.gridy;

    myCombo = new JComboBox(ArrayUtil.toObjectArray(myPreviousLocations));
    myCombo.setEditable(true);
    myCombo.setMinimumSize(new JBDimension(250, 20));
    gb.fill = GridBagConstraints.HORIZONTAL;
    mainPanel.add(myCombo, gb);
    gb.fill = GridBagConstraints.NONE;

    myComboField = (JTextField)myCombo.getEditor().getEditorComponent();
    myComboField.addInputMethodListener(new InputMethodListener() {
      public void inputMethodTextChanged(final InputMethodEvent event) {
        validateMe();
      }

      public void caretPositionChanged(final InputMethodEvent event) {
        validateMe();
      }
    });
    myComboField.addKeyListener(new KeyListener() {
      public void keyTyped(final KeyEvent e) {
        validateMe();
      }

      public void keyPressed(final KeyEvent e) {
        validateMe();
      }

      public void keyReleased(final KeyEvent e) {
        validateMe();
      }
    });

    myCombo.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        validateMe();
      }
    });
    validateMe();

    final JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.add(mainPanel, new GridBagConstraints(0,0,1,1,1,1,GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                  new Insets(0,0,0,0), 0,0));
    wrapper.setPreferredSize(new Dimension(400, 70));
    return wrapper;
  }

  private void validateMe() {
    final String inputString = myComboField.getText();
    setOKActionEnabled(urlValid(inputString));
  }

  private boolean urlValid(final String inputString) {
    if (inputString == null) {
      return false;
    }
    try {
      final SVNURL svnurl = SVNURL.parseURIDecoded(inputString.trim());
      return svnurl != null;
    } catch (SVNException e) {
      //
    }
    return false;
  }

  @Override
  protected void doOKAction() {
    mySelected = myComboField.getText().trim();
    super.doOKAction();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  public String getSelected() {
    return mySelected;
  }
}
