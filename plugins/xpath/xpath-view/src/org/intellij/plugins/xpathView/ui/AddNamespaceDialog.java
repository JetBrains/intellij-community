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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ArrayUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collection;
import java.util.Set;

public class AddNamespaceDialog extends DialogWrapper {
    public enum Mode {
        EDITABLE, PREFIX_EDITABLE, URI_EDITABLE, FIXED
    }

    private JPanel myRoot;
    private JLabel myIcon;

    private ComboBox myPrefix;
    private ComboBox myURI;

    public AddNamespaceDialog(Project project, Set<String> unresolvedPrefixes, Collection<String> uriList, Mode mode) {
        super(project, false);

        myIcon.setText(null);
        myIcon.setIcon(Messages.getQuestionIcon());

        myURI.setModel(new DefaultComboBoxModel(ArrayUtil.toStringArray(uriList)));
        myURI.setSelectedItem("");
        myURI.setEditable(mode == Mode.EDITABLE || mode == Mode.URI_EDITABLE);
        addUpdateListener(myURI);

        myPrefix.setModel(new DefaultComboBoxModel(ArrayUtil.toStringArray(unresolvedPrefixes)));
        myPrefix.setEditable(mode == Mode.EDITABLE || mode == Mode.PREFIX_EDITABLE);
        if (unresolvedPrefixes.size() == 1) {
            myPrefix.setSelectedItem(unresolvedPrefixes.iterator().next());
        }
        addUpdateListener(myPrefix);

        updateOkAction();
        init();
    }

    private void addUpdateListener(ComboBox comboBox) {
        final ComboBoxEditor boxEditor = comboBox.getEditor();
        if (boxEditor != null) {
            final Component component = boxEditor.getEditorComponent();
            if (component instanceof JTextField) {
                ((JTextField)component).getDocument().addDocumentListener(new DocumentAdapter() {
                    protected void textChanged(DocumentEvent e) {
                        updateOkAction();
                    }
                });
            }
        }
        comboBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                updateOkAction();
            }
        });
    }

    private void updateOkAction() {
        getOKAction().setEnabled(getURI().length() > 0 && getPrefix().length() > 0);
    }

    protected JComponent createCenterPanel() {
        return myRoot;
    }

    public String getPrefix() {
        final Object item = (myPrefix.isEditable() ? myPrefix.getEditor().getItem() : myPrefix.getSelectedItem());
        return ((String)item).trim();
    }

    public String getURI() {
        final Object item = (myURI.isEditable() ? myURI.getEditor().getItem() : myURI.getSelectedItem());
        return ((String)item).trim();
    }
}
