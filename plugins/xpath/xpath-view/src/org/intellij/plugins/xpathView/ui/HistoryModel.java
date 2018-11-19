/*
 * Copyright 2002-2005 Sascha Weinreuter
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Comparing;
import org.intellij.plugins.xpathView.HistoryElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

class HistoryModel extends AbstractListModel implements ComboBoxModel, MultilineEditor.EditorModel {
    private final HistoryElement[] history;
    private final Document myDocument;
    private final int length;
    private HistoryElement selectedItem;

    HistoryModel(HistoryElement[] history, Document document) {
        this.history = history;
        this.myDocument = document;
        if (history.length > 0) {
            setSelectedItem(history[history.length - 1]);
        }
        this.length = history.length;
    }

    @Override
    public HistoryElement getElementAt(int i) {
        return history[(length - 1) - i];
    }

    @Override
    public int getSize() {
        return length;
    }

    @Override
    @Nullable
    public HistoryElement getSelectedItem() {
        if (selectedItem != null) {
            if (Comparing.equal(selectedItem.expression, myDocument.getCharsSequence())) {
                return selectedItem;
            } else {
                return selectedItem.changeExpression(myDocument.getText().trim());
            }
        }

        assert length == 0;
        return selectedItem;
    }

    @Override
    public void setSelectedIndex(int index) {
        setSelectedItem(getElementAt(index));
    }

    @Override
    public int getSelectedIndex() {
        final int i = indexOf(this.selectedItem);
        return history.length - 1 - i;
    }

    private int indexOf(HistoryElement selectedItem) {
        return Arrays.asList(history).indexOf(selectedItem);
    }

    @Override
    public void setSelectedItem(Object object) {
        if (object instanceof String) {
            for (HistoryElement element : history) {
                if (object.equals(element.expression)) {
                    object = element;
                    break;
                }
            }
        }
        if (object instanceof String) {
            object = selectedItem.changeExpression((String)object);
        }
        if (selectedItem != null && selectedItem != object || selectedItem == null && object != null) {
            selectedItem = (HistoryElement)object;
            fireContentsChanged(this, -1, -1);
        }
    }

    @Override
    public String getItemString(int i) {
        return i > history.length ? getElementAt(i).expression : selectedItem.expression;
    }
}
