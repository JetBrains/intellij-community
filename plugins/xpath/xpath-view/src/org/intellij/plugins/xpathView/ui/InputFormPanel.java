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
package org.intellij.plugins.xpathView.ui;

import javax.swing.*;

public class InputFormPanel extends JPanel implements InputForm {
    @SuppressWarnings({ "UNUSED_SYMBOL", "FieldCanBeLocal" })
    private JPanel myRoot;

    private JLabel myIcon;
    private JButton myEditContextButton;
    private JPanel myEditorPanel;

    private JButton mySaveTemplate;
    private JButton myOpenTemplate;

    private void createUIComponents() {
        myRoot = this;
    }

    public JComponent getComponent() {
        return this;
    }

    public JLabel getIcon() {
        return myIcon;
    }

    public JButton getEditContextButton() {
        return myEditContextButton;
    }

    public JPanel getEditorPanel() {
        return myEditorPanel;
    }

    public void dispose() {}
}
