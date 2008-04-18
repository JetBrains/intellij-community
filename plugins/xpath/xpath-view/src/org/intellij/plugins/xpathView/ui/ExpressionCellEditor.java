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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.EditorTextField;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.EventObject;

public class ExpressionCellEditor extends AbstractCellEditor implements TableCellEditor {
    private final Project project;

    private Document myDocument;
    private Expression myExpression;

    public ExpressionCellEditor(Project project) {
        this.project = project;
    }

    public Component getTableCellEditorComponent(JTable ttable, Object value, boolean isSelected, int row, int col) {
        myExpression = (Expression)value;

        myDocument = PsiDocumentManager.getInstance(project).getDocument(myExpression.getFile());
        return new EditorTextField(myDocument, project, myExpression.getFileType()) {
            protected boolean shouldHaveBorder() {
                return false;
            }

            public void addNotify() {
                super.addNotify();
                Runnable runnable = new Runnable() {
                    public void run() {
                        final Editor editor = getEditor();
                        if (editor != null) {
                            editor.getContentComponent().requestFocus();
                        }
                    }
                };
                SwingUtilities.invokeLater(runnable);
            }
        };
    }

    public boolean isCellEditable(EventObject eventObject) {
        if (eventObject instanceof MouseEvent) {
            return ((MouseEvent)eventObject).getClickCount() >= 2;
        }
        return super.isCellEditable(eventObject);
    }

    public Expression getCellEditorValue() {
        return myExpression;
    }

    public boolean stopCellEditing() {
        super.stopCellEditing();
        PsiDocumentManager.getInstance(project).commitDocument(myDocument);
        return true;
    }
}
