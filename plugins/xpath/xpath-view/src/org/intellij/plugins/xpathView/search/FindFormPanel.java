/*
 * Copyright 2006 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.intellij.plugins.xpathView.ui.InputForm;
import org.intellij.plugins.xpathView.ui.InputFormPanel;

import javax.swing.*;

public class FindFormPanel extends JPanel implements InputForm {
    @SuppressWarnings({ "FieldCanBeLocal", "UnusedDeclaration" })
    private JPanel myRoot;

    private final Project myProject;

    private InputFormPanel myInputPanel;
    private JCheckBox myNewTabCheckbox;
    private JRadioButton myMatchRootNode;
    private JRadioButton myMatchEachNode;
    private JPanel myOptionsPanel;
    private ScopePanel myScopePanel;

    public FindFormPanel(Project project, Module module, SearchScope searchScope) {
        myProject = project;
        myScopePanel.initComponent(module, searchScope);
    }

    private void createUIComponents() {
        myRoot = this;
        myScopePanel = new ScopePanel(myProject);
    }

    public JComponent getComponent() {
        return this;
    }

    public SearchScope getScope() {
        return myScopePanel.getSearchScope();
    }

    public JLabel getIcon() {
        return myInputPanel.getIcon();
    }

    public JButton getEditContextButton() {
        return myInputPanel.getEditContextButton();
    }

    public JPanel getEditorPanel() {
        return myInputPanel.getEditorPanel();
    }

    public JCheckBox getNewTabCheckbox() {
        return myNewTabCheckbox;
    }

    public JRadioButton getMatchRootNode() {
        return myMatchRootNode;
    }

    public JRadioButton getMatchEachNode() {
        return myMatchEachNode;
    }

    public ScopePanel getScopePanel() {
        return myScopePanel;
    }

    public JPanel getOptionsPanel() {
        return myOptionsPanel;
    }

  public void dispose() {
    Disposer.dispose(myScopePanel);
  }
}
