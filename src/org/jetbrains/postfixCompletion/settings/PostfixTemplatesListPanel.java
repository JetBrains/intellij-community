package org.jetbrains.postfixCompletion.settings;

import com.intellij.ui.BooleanTableCellEditor;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.TableViewSpeedSearch;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.TableView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProviderInfo;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.List;
import java.util.Map;

public class PostfixTemplatesListPanel {
  @NotNull
  private final Map<String, Boolean> myTemplatesState = ContainerUtil.newHashMap();
  @NotNull
  private final JPanel myPanelWithTableView;

  public PostfixTemplatesListPanel(@NotNull List<TemplateProviderInfo> templates) {
    ColumnInfo[] columns = {new BooleanColumnInfo(), new ShortcutColumnInfo(), new DescriptionColumnInfo(), new ExampleColumnInfo()};
    ListTableModel<TemplateProviderInfo> templatesTableModel = new ListTableModel<TemplateProviderInfo>(columns, templates, 0);
    TableView<TemplateProviderInfo> templatesTableView = new TableView<TemplateProviderInfo>();
    templatesTableView.setModelAndUpdateColumns(templatesTableModel);
    templatesTableView.setShowGrid(false);
    templatesTableView.setStriped(true);
    templatesTableView.setBorder(null);

    new TableViewSpeedSearch<TemplateProviderInfo>(templatesTableView) {
      @Override
      protected String getItemText(@NotNull TemplateProviderInfo template) {
        return template.annotation.templateName();
      }
    };

    myPanelWithTableView = ToolbarDecorator.createDecorator(templatesTableView)
      .disableAddAction()
      .disableRemoveAction()
      .disableUpDownActions().createPanel();
  }

  @NotNull
  public JComponent getComponent() {
    return myPanelWithTableView;
  }

  public void setState(@NotNull Map<String, Boolean> templatesState) {
    myTemplatesState.clear();
    for (Map.Entry<String, Boolean> entry : templatesState.entrySet()) {
      myTemplatesState.put(entry.getKey(), entry.getValue());
    }
  }

  @NotNull
  public Map<String, Boolean> getState() {
    return myTemplatesState;
  }

  private class BooleanColumnInfo extends ColumnInfo<TemplateProviderInfo, Boolean> {
    private final BooleanTableCellRenderer CELL_RENDERER = new BooleanTableCellRenderer();
    private final BooleanTableCellEditor CELL_EDITOR = new BooleanTableCellEditor();
    private final int WIDTH = new JBCheckBox().getPreferredSize().width;

    public BooleanColumnInfo() {
      super("");
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(TemplateProviderInfo template) {
      return CELL_RENDERER;
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(TemplateProviderInfo template) {
      return CELL_EDITOR;
    }

    @Override
    public int getWidth(JTable table) {
      return WIDTH;
    }

    @NotNull
    @Override
    public Class getColumnClass() {
      return Boolean.class;
    }

    @Override
    public boolean isCellEditable(TemplateProviderInfo bean) {
      return true;
    }

    @Nullable
    @Override
    public Boolean valueOf(@NotNull TemplateProviderInfo template) {
      return ContainerUtil.getOrElse(myTemplatesState, template.annotation.templateName(), true);
    }

    @Override
    public void setValue(@NotNull TemplateProviderInfo template, Boolean value) {
      myTemplatesState.put(template.annotation.templateName(), value);
    }
  }

  private static class ShortcutColumnInfo extends ColumnInfo<TemplateProviderInfo, String> {
    public ShortcutColumnInfo() {
      super("Shortcut");
    }

    public String valueOf(final TemplateProviderInfo template) {
      return template.annotation.templateName();
    }
  }

  private static class DescriptionColumnInfo extends ColumnInfo<TemplateProviderInfo, String> {
    public DescriptionColumnInfo() {
      super("Description");
    }

    public String valueOf(final TemplateProviderInfo template) {
      return template.annotation.description();
    }
  }

  private static class ExampleColumnInfo extends ColumnInfo<TemplateProviderInfo, String> {
    public ExampleColumnInfo() {
      super("Example");
    }

    public String valueOf(final TemplateProviderInfo template) {
      return template.annotation.example();
    }
  }
}
