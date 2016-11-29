/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.changeSignature;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.changeSignature.ParameterTableModelBase;
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase;
import com.intellij.refactoring.ui.CodeFragmentTableCellRenderer;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ui.ColumnInfo;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyParameterList;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * User : ktisha
 */

public class PyParameterTableModel extends ParameterTableModelBase<PyParameterInfo, PyParameterTableModelItem> {

  private final Project myProject;

  public PyParameterTableModel(PyParameterList typeContext,
                               PsiElement defaultValueContext,
                               Project project) {
    super(typeContext, defaultValueContext, getColumns(project));
    myProject = project;
  }

  private static ColumnInfo[] getColumns(Project project) {
    final Collection<ColumnInfo> result = new ArrayList<>();
    result.add(new PyParameterColumn(project));
    result.add(new PyDefaultValueColumn(project));
    return result.toArray(new ColumnInfo[result.size()]);
  }

  @Override
  protected PyParameterTableModelItem createRowItem(@Nullable PyParameterInfo parameterInfo) {
    if (parameterInfo == null) {
      parameterInfo = new PyParameterInfo(-1);
    }
    final String defaultValue = parameterInfo.getDefaultValue();
    final PsiCodeFragment defaultValueFragment = new PyExpressionCodeFragment(myProject, StringUtil.notNullize(defaultValue),
                                                                    StringUtil.notNullize(defaultValue));
    final boolean defaultInSignature = parameterInfo.getDefaultInSignature();
    return new PyParameterTableModelItem(parameterInfo, defaultValueFragment, defaultValueFragment, defaultInSignature);
  }

  private static class PyParameterColumn extends NameColumn<PyParameterInfo, PyParameterTableModelItem> {
    public PyParameterColumn(Project project) {
      super(project);
    }
  }

  protected static class PyDefaultValueColumn<P extends ParameterInfo, TableItem extends ParameterTableModelItemBase<P>> extends ColumnInfoBase<P, TableItem, Pair<PsiCodeFragment, Boolean>> {

    private final Project myProject;

    public PyDefaultValueColumn(Project project) {
      super(RefactoringBundle.message("column.name.default.value"));
      myProject = project;
    }

    @Override
    public boolean isCellEditable(TableItem item) {
      return true;
    }

    @Override
    public Pair<PsiCodeFragment, Boolean> valueOf(TableItem item) {
      return new Pair<>(item.defaultValueCodeFragment, ((PyParameterTableModelItem)item).isDefaultInSignature());
    }

    @Override
    public void setValue(TableItem item, Pair<PsiCodeFragment, Boolean> value) {
      final PyParameterInfo parameter = (PyParameterInfo)item.parameter;
      parameter.setDefaultValue(value.getFirst().getText().trim());
      parameter.setDefaultInSignature(value.getSecond());
    }

    public TableCellRenderer doCreateRenderer(TableItem item) {
      return new MyCodeFragmentTableCellRenderer(myProject);
    }

    public TableCellEditor doCreateEditor(TableItem item) {
      return new MyCodeFragmentTableCellEditor(myProject);
    }
  }

  private static class MyCodeFragmentTableCellRenderer extends CodeFragmentTableCellRenderer {

    public MyCodeFragmentTableCellRenderer(Project project) {
      super(project);
    }
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, final boolean hasFocus, int row, int column) {
      JPanel panel = new JPanel();
      final Component component = super.getTableCellRendererComponent(table, ((Pair)value).getFirst(), isSelected, hasFocus, row, column);
      panel.add(component);

      final Component component1 =
        new BooleanTableCellRenderer().getTableCellRendererComponent(table, ((Pair)value).getSecond(), isSelected, hasFocus, row, column);
      panel.add(component1);
      return panel;
    }
  }

  private static class MyCodeFragmentTableCellEditor extends AbstractCellEditor implements TableCellEditor {
    private Document myDocument;
    protected PsiCodeFragment myCodeFragment;
    private final Project myProject;
    private final FileType myFileType;
    protected EditorTextField myEditorTextField;
    private Set<DocumentListener> myListeners = new HashSet<>();

    public MyCodeFragmentTableCellEditor(Project project) {
      myProject = project;
      myFileType = PythonFileType.INSTANCE;
    }

    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      myCodeFragment = (PsiCodeFragment)((Pair)value).getFirst();

      myDocument = PsiDocumentManager.getInstance(myProject).getDocument(myCodeFragment);
      JPanel panel = new JPanel();
      myEditorTextField = createEditorField(myDocument);
      if (myEditorTextField != null) {
        for (DocumentListener listener : myListeners) {
          myEditorTextField.addDocumentListener(listener);
        }
        myEditorTextField.setDocument(myDocument);
        myEditorTextField.setBorder(new LineBorder(table.getSelectionBackground()));
      }

      panel.add(myEditorTextField);
      panel.add(new JCheckBox());
      return panel;
    }

    protected EditorTextField createEditorField(Document document) {
      EditorTextField field = new EditorTextField(document, myProject, myFileType) {
        protected boolean shouldHaveBorder() {
          return false;
        }
      };
      field.setBorder(new EmptyBorder(1, 1, 1, 1));
      return field;
    }

    public PsiCodeFragment getCellEditorValue() {
      return myCodeFragment;
    }
  }

}
