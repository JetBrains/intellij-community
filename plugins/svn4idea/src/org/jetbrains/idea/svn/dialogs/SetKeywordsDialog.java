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
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: Jun 21, 2006
 * Time: 6:37:41 PM
 */
public class SetKeywordsDialog extends DialogWrapper {

  private final String myPropValue;

  private JCheckBox myLastChangedDateCheckbox;
  private JCheckBox myLastChangedRevisionCheckbox;
  private JCheckBox myLastChangedByCheckbox;
  private JCheckBox myURLCheckbox;
  private JCheckBox myIDCheckbox;

  protected SetKeywordsDialog(Project project, String propValue) {
    super(project, false);
    myPropValue = propValue;
    setTitle("SVN Keywords");
    setResizable(false);
    init();
  }

  public String getKeywords() {
    StringBuffer result = new StringBuffer();
    if (myLastChangedDateCheckbox.isSelected()) {
      result.append("LastChangedDate ");
    }
    if (myLastChangedByCheckbox.isSelected()) {
      result.append("LastChangedBy ");
    }
    if (myLastChangedRevisionCheckbox.isSelected()) {
      result.append("LastChangedRevision ");
    }
    if (myURLCheckbox.isSelected()) {
      result.append("HeadURL ");
    }
    if (myIDCheckbox.isSelected()) {
      result.append("Id");
    }
    if (result.length() > 0) {
      return result.toString().trim();
    }
    return null;
  }

  @Nullable
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel("Select keywords to set: "), BorderLayout.NORTH);
    JPanel buttonsPanel = new JPanel(new GridLayout(5, 1));

    myIDCheckbox = new JCheckBox("Id");
    myURLCheckbox = new JCheckBox("HeadURL");
    myLastChangedDateCheckbox = new JCheckBox("LastChangedDate");
    myLastChangedRevisionCheckbox = new JCheckBox("LastChangedRevision");
    myLastChangedByCheckbox = new JCheckBox("LastChangedBy");

    buttonsPanel.add(myIDCheckbox);
    buttonsPanel.add(myURLCheckbox);
    buttonsPanel.add(myLastChangedByCheckbox);
    buttonsPanel.add(myLastChangedDateCheckbox);
    buttonsPanel.add(myLastChangedRevisionCheckbox);

    panel.add(buttonsPanel, BorderLayout.CENTER);
    initValues();

    return panel;
  }

  private void initValues() {
    Map keywords = SVNTranslator.computeKeywords(myPropValue, "u", "a", "d", "r", null);
    myLastChangedDateCheckbox.setSelected(keywords.containsKey("LastChangedDate"));
    myLastChangedByCheckbox.setSelected(keywords.containsKey("LastChangedBy"));
    myLastChangedRevisionCheckbox.setSelected(keywords.containsKey("LastChangedRevision"));
    myURLCheckbox.setSelected(keywords.containsKey("HeadURL"));
    myIDCheckbox.setSelected(keywords.containsKey("Id"));
  }
}
