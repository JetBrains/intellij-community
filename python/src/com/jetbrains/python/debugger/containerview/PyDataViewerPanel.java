/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.debugger.containerview;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.intellij.util.TextFieldCompletionProvider;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.debugger.ArrayChunk;
import com.jetbrains.python.debugger.PyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.debugger.array.JBTableWithRowHeaders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PyDataViewerPanel extends JPanel {
  private final static int COLUMNS_IN_DEFAULT_VIEW = 1000;
  private final static int ROWS_IN_DEFAULT_VIEW = 1000;
  private static final Logger LOG = Logger.getInstance(PyDataViewerPanel.class);
  private final Project myProject;
  private EditorTextField mySliceTextField;
  private JBTableWithRowHeaders myTable;
  private EditorTextField myFormatTextField;
  private JPanel myMainPanel;
  private JBLabel myErrorLabel;
  private boolean myColored;
  List<Listener> myListeners;

  public PyDataViewerPanel(@NotNull Project project) {
    super(new BorderLayout());
    myProject = project;
    myErrorLabel.setVisible(false);
    myErrorLabel.setForeground(JBColor.RED);
    myMainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
    add(myMainPanel, BorderLayout.CENTER);
    myColored = PropertiesComponent.getInstance(myProject).getBoolean(PyDataView.COLORED_BY_DEFAULT, true);
    myListeners = new CopyOnWriteArrayList<>();
  }

  public JBTable getTable() {
    return myTable;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  protected void createUIComponents() {
    myFormatTextField = createEditorField();
    mySliceTextField = createEditorField();
    addCompletion();

    myTable = new JBTableWithRowHeaders();
  }

  private void addCompletion() {
    new PyDataViewCompletionProvider().apply(mySliceTextField);
  }

  @NotNull
  private EditorTextField createEditorField() {
    return new EditorTextField(EditorFactory.getInstance().createDocument(""), myProject, PythonFileType.INSTANCE, false, true) {
      @Override
      protected EditorEx createEditor() {
        EditorEx editor = super.createEditor();
        editor.getContentComponent().addKeyListener(new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
              apply(mySliceTextField.getText());
            }
          }
        });
        return editor;
      }
    };
  }

  public void apply(String name) {
    myErrorLabel.setVisible(false);
    PyDebugValue debugValue = getDebugValue(name);
    if (debugValue == null) {
      return;
    }
    apply(debugValue);
  }

  public void apply(@NotNull PyDebugValue debugValue) {
    myErrorLabel.setVisible(false);
    String type = debugValue.getType();
    DataViewStrategy strategy = DataViewStrategy.getStrategy(type);
    if (strategy == null) {
      setError(type + " is not supported");
      return;
    }
    try {
      ArrayChunk arrayChunk = debugValue.getFrameAccessor().getArrayItems(debugValue, 0, 0, -1, -1, getFormat());
      updateUI(arrayChunk, debugValue, strategy);
    }
    catch (PyDebuggerException e) {
      LOG.error(e);
    }
  }

  private void updateUI(@NotNull ArrayChunk chunk, @NotNull PyDebugValue debugValue, @NotNull DataViewStrategy strategy) {
    AsyncArrayTableModel model = strategy.createTableModel(Math.min(chunk.getRows(), ROWS_IN_DEFAULT_VIEW),
                                                           Math.min(chunk.getColumns(), COLUMNS_IN_DEFAULT_VIEW), this, debugValue);
    model.addToCache(chunk);

    UIUtil.invokeLaterIfNeeded(() -> {
      myTable.setModel(model);
      String text = debugValue.getName().equals(debugValue.getTempName()) ? chunk.getSlicePresentation() : debugValue.getName();
      mySliceTextField.setText(text);
      if (mySliceTextField.getEditor() != null) {
        mySliceTextField.getCaretModel().moveToOffset(text.length());
      }
      for (Listener listener : myListeners) {
        listener.onNameChanged(text);
      }
      myFormatTextField.setText(chunk.getFormat());
      ColoredCellRenderer cellRenderer = strategy.createCellRenderer(Double.MIN_VALUE, Double.MAX_VALUE, chunk);
      cellRenderer.setColored(myColored);
      ((AsyncArrayTableModel)myTable.getModel()).fireTableDataChanged();
      ((AsyncArrayTableModel)myTable.getModel()).fireTableCellUpdated(0, 0);
      if (myTable.getColumnCount() > 0) {
        myTable.setDefaultRenderer(myTable.getColumnClass(0), cellRenderer);
      }
    });
  }

  private PyDebugValue getDebugValue(String expression) {
    PyDebugProcess process = getDebugProcess();
    if (process == null) {
      return null;
    }
    try {
      PyDebugValue value = process.evaluate(expression, false, true);
      if (value.isErrorOnEval()) {
        setError(value.getValue());
        return null;
      }
      return value;
    }
    catch (PyDebuggerException e) {
      setError(e.getTracebackError());
      return null;
    }
  }

  private void setError(String text) {
    myErrorLabel.setVisible(true);
    myErrorLabel.setText(text);
    myTable.setEmpty();
    for (Listener listener : myListeners) {
      listener.onNameChanged(PyDataView.EMPTY_TAB_NAME);
    }
  }

  @Nullable
  private PyDebugProcess getDebugProcess() {
    XDebugSession session = XDebuggerManager.getInstance(myProject).getCurrentSession();
    if (session == null) {
      return null;
    }
    XDebugProcess process = session.getDebugProcess();
    return process instanceof PyDebugProcess ? ((PyDebugProcess)process) : null;
  }

  public String getFormat() {
    String format = myFormatTextField.getText();
    return format.isEmpty() ? "%" : format;
  }

  public boolean isColored() {
    return myColored;
  }

  public void setColored(boolean state) {
    myColored = state;
    if (!myTable.isEmpty()) {
      ((ColoredCellRenderer)myTable.getDefaultRenderer(myTable.getColumnClass(0))).setColored(state);
    }
  }

  public EditorTextField getSliceTextField() {
    return mySliceTextField;
  }

  @Nullable
  public AsyncArrayTableModel getModel() {
    TableModel model = myTable.getModel();
    if (model instanceof AsyncArrayTableModel) {
      return ((AsyncArrayTableModel)myTable.getModel());
    }
    return null;
  }

  public void addListener(Listener listener) {
    myListeners.add(listener);
  }

  public interface Listener {
    void onNameChanged(String name);
  }

  private class PyDataViewCompletionProvider extends TextFieldCompletionProvider {
    @Override
    protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
      List<PyDebugValue> values = getAvailableValues();
      Collections.sort(values, Comparator.comparing(XNamedValue::getName));
      for (int i = 0; i < values.size(); i++) {
        PyDebugValue value = values.get(i);
        LookupElementBuilder element = LookupElementBuilder.create(value.getName()).withTypeText(value.getType(), true);
        result.addElement(PrioritizedLookupElement.withPriority(element, -i));
      }
    }

    private List<PyDebugValue> getAvailableValues() {
      List<PyDebugValue> values = new ArrayList<>();
      try {
        PyDebugProcess process = getDebugProcess();
        if (process == null) {
          return values;
        }
        XValueChildrenList list = process.loadFrame();
        if (list == null) {
          return values;
        }
        for (int i = 0; i < list.size(); i++) {
          PyDebugValue value = (PyDebugValue)list.getValue(i);
          String type = value.getType();
          if (DataViewStrategy.getStrategy(type) != null) {
            values.add(value);
          }
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
      return values;
    }
  }
}
