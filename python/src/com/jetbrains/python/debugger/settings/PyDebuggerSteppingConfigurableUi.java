// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.settings;

import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.NonEmptyInputValidator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.Function;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.table.TableModelEditor;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PyDebuggerSteppingConfigurableUi implements ConfigurableUi<PyDebuggerSettings> {
  private static final ColumnInfo[] COLUMNS = {
    new EnabledColumn(), new FilterColumn()
  };
  private JPanel myPanel;
  private JPanel mySteppingPanel;
  private JBCheckBox myLibrariesFilterCheckBox;
  private JBCheckBox myStepFilterEnabledCheckBox;
  private JBCheckBox myAlwaysDoSmartStepIntoCheckBox;
  private TableModelEditor<PySteppingFilter> myPySteppingFilterEditor;

  public PyDebuggerSteppingConfigurableUi() {
    myStepFilterEnabledCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@Nullable ActionEvent e) {
        myPySteppingFilterEditor.enabled(myStepFilterEnabledCheckBox.isSelected());
      }
    });
  }

  private void createUIComponents() {
    TableModelEditor.DialogItemEditor<PySteppingFilter> itemEditor = new DialogEditor();
    myPySteppingFilterEditor = new TableModelEditor<>(COLUMNS, itemEditor, PyBundle.message("debugger.stepping.no.script.filters"));
    mySteppingPanel = new JPanel(new BorderLayout());
    mySteppingPanel.add(myPySteppingFilterEditor.createComponent());
  }

  @Override
  public void reset(@NotNull PyDebuggerSettings settings) {
    myLibrariesFilterCheckBox.setSelected(settings.isLibrariesFilterEnabled());
    myStepFilterEnabledCheckBox.setSelected(settings.isSteppingFiltersEnabled());
    myAlwaysDoSmartStepIntoCheckBox.setSelected(settings.isAlwaysDoSmartStepInto());
    myPySteppingFilterEditor.reset(settings.getSteppingFilters());
    myPySteppingFilterEditor.enabled(myStepFilterEnabledCheckBox.isSelected());
  }

  @Override
  public boolean isModified(@NotNull PyDebuggerSettings settings) {
    return myLibrariesFilterCheckBox.isSelected() != settings.isLibrariesFilterEnabled()
           || myStepFilterEnabledCheckBox.isSelected() != settings.isSteppingFiltersEnabled()
           || myAlwaysDoSmartStepIntoCheckBox.isSelected() != settings.isAlwaysDoSmartStepInto()
           || myPySteppingFilterEditor.isModified();
  }

  @Override
  public void apply(@NotNull PyDebuggerSettings settings) {
    settings.setLibrariesFilterEnabled(myLibrariesFilterCheckBox.isSelected());
    settings.setSteppingFiltersEnabled(myStepFilterEnabledCheckBox.isSelected());
    settings.setAlwaysDoSmartStepIntoEnabled(myAlwaysDoSmartStepIntoCheckBox.isSelected());
    if (myPySteppingFilterEditor.isModified()) {
      settings.setSteppingFilters(myPySteppingFilterEditor.apply());
    }
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myPanel;
  }

  private static class EnabledColumn extends TableModelEditor.EditableColumnInfo<PySteppingFilter, Boolean> {
    @Override
    public @Nullable Boolean valueOf(PySteppingFilter filter) {
      return filter.isEnabled();
    }

    @Override
    public Class<?> getColumnClass() {
      return Boolean.class;
    }

    @Override
    public void setValue(PySteppingFilter filter, Boolean value) {
      filter.setEnabled(value);
    }
  }

  private static class FilterColumn extends TableModelEditor.EditableColumnInfo<PySteppingFilter, String> {
    @Override
    public @Nullable String valueOf(PySteppingFilter filter) {
      return filter.getFilter();
    }

    @Override
    public void setValue(PySteppingFilter filter, String value) {
      filter.setFilter(value);
    }
  }

  private class DialogEditor implements TableModelEditor.DialogItemEditor<PySteppingFilter> {
    @Override
    public PySteppingFilter clone(@NotNull PySteppingFilter item, boolean forInPlaceEditing) {
      return new PySteppingFilter(item.isEnabled(), item.getFilter());
    }

    @Override
    public @NotNull Class<PySteppingFilter> getItemClass() {
      return PySteppingFilter.class;
    }

    @Override
    public void edit(@NotNull PySteppingFilter item, @NotNull Function<? super PySteppingFilter, ? extends PySteppingFilter> mutator, boolean isAdd) {
      String pattern = Messages.showInputDialog(myPanel,
                                                PyBundle.message("debugger.stepping.filter.specify.pattern"),
                                                PyBundle.message("debugger.stepping.filter"), null, item.getFilter(),
                                                new NonEmptyInputValidator());
      if (pattern != null) {
        mutator.fun(item).setFilter(pattern);
        myPySteppingFilterEditor.getModel().fireTableDataChanged();
      }
    }

    @Override
    public void applyEdited(@NotNull PySteppingFilter oldItem, @NotNull PySteppingFilter newItem) {
      oldItem.setFilter(newItem.getFilter());
    }

    @Override
    public boolean isUseDialogToAdd() {
      return true;
    }
  }
}
