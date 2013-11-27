package org.jetbrains.postfixCompletion.SettingsPage;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EditableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.postfixCompletion.Infrastructure.PostfixTemplatesService;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProviderInfo;

import javax.swing.table.AbstractTableModel;
import java.util.List;

final class PostfixTemplatesModel extends AbstractTableModel implements EditableModel {
  @NotNull private final String[] myColumns;
  @NotNull private final List<TemplateProviderInfo> myTemplates;

  public PostfixTemplatesModel(@NotNull String[] columns) {
    PostfixTemplatesService templatesService = ServiceManager.getService(PostfixTemplatesService.class);
    myTemplates = templatesService != null
        ? templatesService.getAllTemplates()
        : ContainerUtil.<TemplateProviderInfo>newArrayList();
    myColumns = columns;
  }

  @Override public int getColumnCount() {
    return myColumns.length;
  }

  @Override public int getRowCount() {
    return myTemplates.size();
  }

  @Override public String getColumnName(int columnIndex) {
    return myColumns[columnIndex];
  }

  @Override public Class<?> getColumnClass(int columnIndex) {
    if (columnIndex == 0) {
      return Boolean.class;
    } else {
      return String.class;
    }
  }

  @Override public boolean isCellEditable(int rowIndex, int columnIndex) {
    return (columnIndex == 0);
  }

  @Override public Object getValueAt(int rowIndex, int columnIndex) {
    TemplateProviderInfo info = myTemplates.get(rowIndex);
    switch (columnIndex) {
      case 0: return Boolean.TRUE;
      case 1: return info.annotation.templateName();
      case 2: return info.annotation.description();
      case 3: return info.annotation.example();
    }

    return null;
  }

  @Override public void setValueAt(Object aValue, int rowIndex, int columnIndex) {

  }

  @Override public void addRow() {
    throw new UnsupportedOperationException();
  }

  @Override public void removeRow(int i) {
    throw new UnsupportedOperationException();
  }

  @Override public void exchangeRows(int i, int i2) {
    throw new UnsupportedOperationException();
  }

  @Override public boolean canExchangeRows(int i, int i2) {
    return false;
  }
}
