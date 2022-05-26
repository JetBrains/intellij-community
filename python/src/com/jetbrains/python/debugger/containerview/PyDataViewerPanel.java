// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.containerview;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.Label;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.TextFieldCompletionProvider;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.debugger.*;
import com.jetbrains.python.debugger.array.AbstractDataViewTable;
import com.jetbrains.python.debugger.array.AsyncArrayTableModel;
import com.jetbrains.python.debugger.array.JBTableWithRowHeaders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PyDataViewerPanel extends JPanel {
  private static final Logger LOG = Logger.getInstance(PyDataViewerPanel.class);
  protected final Project myProject;
  @NotNull protected final PyFrameAccessor myFrameAccessor;
  private EditorTextField mySliceTextField;
  protected AbstractDataViewTable myTable;
  private EditorTextField myFormatTextField;
  private JPanel myMainPanel;
  private JBLabel myErrorLabel;
  @SuppressWarnings("unused") private JBScrollPane myScrollPane;
  protected JPanel bottomPanel;
  private boolean myColored;
  List<Listener> myListeners;
  private @NlsSafe String myOriginalVarName;
  private String myModifiedVarName;

  private static final String MODIFIED_VARIABLE_FORMAT = "%s*";
  protected PyDebugValue myDebugValue;

  public PyDataViewerPanel(@NotNull Project project, @NotNull PyFrameAccessor frameAccessor) {
    super(new BorderLayout());
    myProject = project;
    myFrameAccessor = frameAccessor;
    myErrorLabel.setVisible(false);
    myErrorLabel.setForeground(JBColor.RED);
    setBorder(JBUI.Borders.empty(5));
    add(myMainPanel, BorderLayout.CENTER);
    myColored = PropertiesComponent.getInstance(myProject).getBoolean(PyDataView.COLORED_BY_DEFAULT, true);
    myListeners = new CopyOnWriteArrayList<>();
    setupChangeListener();
  }

  private void setupChangeListener() {
    myFrameAccessor.addFrameListener(new PyFrameListener() {
      @Override
      public void frameChanged() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> updateModel());
      }
    });
  }

  private void updateModel() {
    AsyncArrayTableModel model = getModel();
    if (model == null) {
      return;
    }
    model.invalidateCache();
    if (isModified()) {
      apply(getModifiedVarName(), true);
    }
    else {
      updateDebugValue(model);
      ApplicationManager.getApplication().invokeLater(() -> {
        if (isShowing()) {
          model.fireTableDataChanged();
        }
      });
    }
  }

  private boolean myModified = false;

  public boolean isModified() {
    return myModified;
  }

  private void updateDebugValue(@NotNull AsyncArrayTableModel model) {
    PyDebugValue oldValue = model.getDebugValue();
    if (oldValue != null && !oldValue.isTemporary() || mySliceTextField.getText().isEmpty()) {
      return;
    }
    PyDebugValue newValue = getDebugValue(mySliceTextField.getText(), false, false);
    if (newValue != null) {
      model.setDebugValue(newValue);
    }
  }

  public String getOriginalVarName() {
    return myOriginalVarName;
  }

  public String getModifiedVarName() {
    return myModifiedVarName;
  }

  @NotNull
  public PyFrameAccessor getFrameAccessor() {
    return myFrameAccessor;
  }

  public AbstractDataViewTable getTable() {
    return myTable;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  protected AbstractDataViewTable createMainTable() {
    return new JBTableWithRowHeaders(PropertiesComponent.getInstance(myProject).getBoolean(PyDataView.AUTO_RESIZE, true));
  }

  protected void createUIComponents() {
    mySliceTextField = createEditorField();
    mySliceTextField.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
    addCompletion();
    myFormatTextField = createEditorField();
    myFormatTextField.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 7));

    myTable = createMainTable();
    myScrollPane = myTable.getScrollPane();
  }

  private void addCompletion() {
    new PyDataViewCompletionProvider().apply(mySliceTextField);
  }

  @NotNull
  private EditorTextField createEditorField() {
    return new EditorTextField(EditorFactory.getInstance().createDocument(""), myProject, PythonFileType.INSTANCE, false, true) {
      @Override
      protected @NotNull EditorEx createEditor() {
        EditorEx editor = super.createEditor();
        editor.getContentComponent().addKeyListener(new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
              apply(mySliceTextField.getText(), false);
            }
          }
        });
        return editor;
      }
    };
  }

  public void apply(String name, boolean modifier) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      PyDebugValue debugValue = getDebugValue(name, true, modifier);

      ApplicationManager.getApplication().invokeLater(() -> {
        if (debugValue != null) {
          apply(debugValue, modifier);
        }
      });
    });
  }

  public void apply(@NotNull PyDebugValue debugValue, boolean modifier) {
    myErrorLabel.setVisible(false);
    String type = debugValue.getType();
    DataViewStrategy strategy = DataViewStrategy.getStrategy(type);
    if (strategy == null) {
      setError(PyBundle.message("debugger.data.view.type.is.not.supported", type), modifier);
      return;
    }
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        doStrategyInitExecution(debugValue.getFrameAccessor(), strategy);
        ArrayChunk arrayChunk = debugValue.getFrameAccessor().getArrayItems(debugValue, 0, 0, 0, 0, getFormat());
        ApplicationManager.getApplication().invokeLater(() -> {
          updateUI(arrayChunk, debugValue, strategy, modifier);
          myModified = modifier;
          myDebugValue = debugValue;
        });
      }
      catch (IllegalArgumentException e) {
        ApplicationManager.getApplication().invokeLater(() -> setError(e.getLocalizedMessage(), modifier)); //NON-NLS
      }
      catch (PyDebuggerException e) {
        LOG.error(e);
      }
    });
  }

  protected void doStrategyInitExecution(PyFrameAccessor frameAccessor, DataViewStrategy strategy) throws PyDebuggerException { }

  private void updateUI(@NotNull ArrayChunk chunk, @NotNull PyDebugValue originalDebugValue,
                        @NotNull DataViewStrategy strategy, boolean modifier) {
    PyDebugValue debugValue = chunk.getValue();
    AsyncArrayTableModel model = strategy.createTableModel(chunk.getRows(), chunk.getColumns(), this, debugValue);
    model.addToCache(chunk);

    UIUtil.invokeLaterIfNeeded(() -> {
      myTable.setModel(model, modifier);
      // Debugger generates a temporary name for every slice evaluation, so we should select a correct name for it
      @NlsSafe String realName =
        debugValue.getName().equals(originalDebugValue.getTempName()) ? originalDebugValue.getName() : chunk.getSlicePresentation();

      String shownName = realName;
      if (modifier && !myOriginalVarName.equals(shownName)) {
        shownName = String.format(MODIFIED_VARIABLE_FORMAT, myOriginalVarName);
      }
      else {
        myOriginalVarName = realName;
      }
      mySliceTextField.setText(myOriginalVarName);

      // Modifier flag means that variable changes are temporary
      myModifiedVarName = realName;

      if (mySliceTextField.getEditor() != null) {
        mySliceTextField.getCaretModel().moveToOffset(myOriginalVarName.length());
      }
      for (Listener listener : myListeners) {
        listener.onNameChanged(shownName);
      }
      myFormatTextField.setText(chunk.getFormat());
      ColoredCellRenderer cellRenderer = strategy.createCellRenderer(Double.MIN_VALUE, Double.MAX_VALUE, chunk);
      cellRenderer.setColored(myColored);
      ((AsyncArrayTableModel)myTable.getModel()).fireTableDataChanged();
      ((AsyncArrayTableModel)myTable.getModel()).fireTableCellUpdated(0, 0);
      if (myTable.getColumnCount() > 0) {
        myTable.setDefaultRenderer(myTable.getColumnClass(0), cellRenderer);
      }
      myTable.setShowColumns(strategy.showColumnHeader());
    });
  }

  private PyDebugValue getDebugValue(@NlsSafe String expression, boolean pooledThread, boolean modifier) {
    try {
      PyDebugValue value = myFrameAccessor.evaluate(expression, false, true);
      if (value == null || value.isErrorOnEval()) {

        Runnable runnable = () -> setError(value != null ? value.getValue() : PyBundle.message("debugger.data.view.failed.to.evaluate.expression", expression), modifier);
        if (pooledThread) {
          ApplicationManager.getApplication().invokeLater(runnable);
        }
        else {
          runnable.run();
        }

        return null;
      }
      return value;
    }
    catch (PyDebuggerException e) {
      Runnable runnable = () -> setError(e.getTracebackError(), modifier); //NON-NLS
      if (pooledThread) {
        ApplicationManager.getApplication().invokeLater(runnable);
      }
      else {
        runnable.run();
      }
      return null;
    }
  }

  public void resize(boolean autoResize) {
    myTable.setAutoResize(autoResize);
    apply(getSliceTextField().getText(), false);
  }

  private void setError(@Label String text, boolean modifier) {
    if (modifier) {
      text = PyBundle.message("debugger.dataviewer.modifier.error", text);
    }
    myErrorLabel.setVisible(true);
    myErrorLabel.setText(text);
    if (!modifier) {
      myTable.setEmpty();
      for (Listener listener : myListeners) {
        listener.onNameChanged(PyBundle.message("debugger.data.view.empty.tab"));
      }
    }
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
    void onNameChanged(@NlsContexts.TabTitle String name);
  }

  private class PyDataViewCompletionProvider extends TextFieldCompletionProvider {
    @Override
    protected void addCompletionVariants(@NotNull String text, int offset, @NotNull String prefix, @NotNull CompletionResultSet result) {
      List<PyDebugValue> values = getAvailableValues();
      values.sort(Comparator.comparing(XNamedValue::getName));
      for (int i = 0; i < values.size(); i++) {
        PyDebugValue value = values.get(i);
        LookupElementBuilder element = LookupElementBuilder.create(value.getName()).withTypeText(value.getType(), true);
        result.addElement(PrioritizedLookupElement.withPriority(element, -i));
      }
    }

    private List<PyDebugValue> getAvailableValues() {
      List<PyDebugValue> values = new ArrayList<>();
      try {
        XValueChildrenList list = myFrameAccessor.loadFrame(null);
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

  public void closeEditorTabs() {}
}
