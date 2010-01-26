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
package org.intellij.plugins.xpathView.eval;

import org.intellij.plugins.xpathView.ui.InputForm;
import org.intellij.plugins.xpathView.ui.InputFormPanel;

import javax.swing.*;

public class EvalFormPanel extends JPanel implements InputForm {
    @SuppressWarnings({ "UNUSED_SYMBOL", "FieldCanBeLocal" })
    private JPanel myRoot;
    private InputFormPanel myInputPanel;

    private JCheckBox myNewTabCheckbox;
    private JCheckBox myHighlightCheckbox;
    private JCheckBox myUsageViewCheckbox;

    private void createUIComponents() {
        myRoot = this;
    }

    public JComponent getComponent() {
        return this;
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

    public JCheckBox getHighlightCheckbox() {
        return myHighlightCheckbox;
    }

    public JCheckBox getUsageViewCheckbox() {
        return myUsageViewCheckbox;
    }

    public void dispose() {}
}
