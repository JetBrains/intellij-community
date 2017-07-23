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
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.ui.CollectionComboBoxModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.sorted;
import static com.intellij.util.ui.JBUI.*;
import static java.awt.GridBagConstraints.*;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.createUrl;

public class AddRepositoryLocationDialog extends DialogWrapper {
  @NotNull private final List<String> myPreviousLocations;
  private JComboBox myCombo;
  private String mySelected;
  private JTextField myComboField;

  public AddRepositoryLocationDialog(@NotNull Project project, @NotNull List<String> values) {
    super(project, true);
    myPreviousLocations = sorted(values);

    setTitle(getTitle());
    init();
    myComboField.setText(initText());
  }

  @Override
  public String getTitle() {
    return message("repository.browser.add.location.title");
  }

  protected String initText() {
    return "http://";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myCombo;
  }

  protected JComponent createCenterPanel() {
    JLabel selectText = new JLabel(message("repository.browser.add.location.prompt"));
    selectText.setUI(new MultiLineLabelUI());

    JPanel mainPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gb = new GridBagConstraints(0, 0, 1, 1, 1, 0, NORTHWEST, NONE, insets(5), 0, 0);

    mainPanel.add(selectText, gb);

    ++gb.gridy;

    myCombo = new ComboBox<>(new CollectionComboBoxModel<>(myPreviousLocations));
    myCombo.setEditable(true);
    myCombo.setMinimumSize(size(250, 20));
    gb.fill = HORIZONTAL;
    mainPanel.add(myCombo, gb);
    gb.fill = NONE;

    myComboField = (JTextField)myCombo.getEditor().getEditorComponent();
    myComboField.addInputMethodListener(new InputMethodListener() {
      public void inputMethodTextChanged(InputMethodEvent event) {
        validateMe();
      }

      public void caretPositionChanged(InputMethodEvent event) {
        validateMe();
      }
    });
    myComboField.addKeyListener(new KeyListener() {
      public void keyTyped(KeyEvent e) {
        validateMe();
      }

      public void keyPressed(KeyEvent e) {
        validateMe();
      }

      public void keyReleased(KeyEvent e) {
        validateMe();
      }
    });

    myCombo.addActionListener(e -> validateMe());
    validateMe();

    JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.add(mainPanel, new GridBagConstraints(0, 0, 1, 1, 1, 1, NORTHWEST, HORIZONTAL, emptyInsets(), 0, 0));
    wrapper.setPreferredSize(size(400, 70));
    return wrapper;
  }

  private void validateMe() {
    setOKActionEnabled(isUrlValid(myComboField.getText()));
  }

  private static boolean isUrlValid(@Nullable String inputString) {
    if (inputString == null) {
      return false;
    }
    try {
      createUrl(inputString.trim(), false);
      return true;
    }
    catch (SvnBindException ignore) {
      return false;
    }
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
