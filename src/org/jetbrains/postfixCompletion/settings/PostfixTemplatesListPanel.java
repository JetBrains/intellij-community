package org.jetbrains.postfixCompletion.settings;

import com.intellij.ui.BooleanTableCellEditor;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.TableViewSpeedSearch;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.table.TableView;
import com.intellij.util.Function;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.postfixCompletion.Infrastructure.TemplateProviderInfo;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.util.List;
import java.util.Map;

public class PostfixTemplatesListPanel {
  private static final NotNullFunction<TemplateProviderInfo, String> GET_SHORTCUT_FUNCTION = new NotNullFunction<TemplateProviderInfo, String>() {
      @NotNull
      @Override
      public String fun(@NotNull TemplateProviderInfo info) {
        return info.annotation.templateName();
      }
    };

  private static final NotNullFunction<TemplateProviderInfo, String> GET_DESCRIPTION_FUNCTION = new NotNullFunction<TemplateProviderInfo, String>() {
      @NotNull
      @Override
      public String fun(@NotNull TemplateProviderInfo info) {
        return info.annotation.description();
      }
    };

  private static final NotNullFunction<TemplateProviderInfo, String> GET_EXAMPLE_FUNCTION = new NotNullFunction<TemplateProviderInfo, String>() {
      @NotNull
      @Override
      public String fun(@NotNull TemplateProviderInfo info) {
        return info.annotation.example();
      }
    };

  @NotNull
  private final Map<String, Boolean> myTemplatesState = ContainerUtil.newHashMap();
  @NotNull
  private final JPanel myPanelWithTableView;

  public PostfixTemplatesListPanel(@NotNull List<TemplateProviderInfo> templates) {
    ColumnInfo[] columns = generateColumns(templates);
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
  private ColumnInfo[] generateColumns(@NotNull List<TemplateProviderInfo> templates) {
    String longestTemplateName = "";
    String longestDescription = "";
    String longestExample = "";
    for (TemplateProviderInfo template : templates) {
      longestTemplateName = longestString(longestTemplateName, GET_SHORTCUT_FUNCTION.fun(template));
      longestDescription = longestString(longestDescription, GET_DESCRIPTION_FUNCTION.fun(template));
      longestExample = longestString(longestExample, GET_EXAMPLE_FUNCTION.fun(template));
    }
    return new ColumnInfo[]{
      new BooleanColumnInfo(),
      new StringColumnInfo("Shortcut", GET_SHORTCUT_FUNCTION, longestTemplateName),
      new StringColumnInfo("Description", GET_DESCRIPTION_FUNCTION, longestDescription),
      new StringColumnInfo("Example", GET_EXAMPLE_FUNCTION, longestExample),
    };
  }

  @NotNull
  private static String longestString(@NotNull String firstString, @NotNull String secondString) {
    return secondString.length() > firstString.length() ? secondString : firstString;
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

  private static class StringColumnInfo extends ColumnInfo<TemplateProviderInfo, String> {
    @NotNull private final Function<TemplateProviderInfo, String> myValueOfFunction;
    @Nullable private final String myPreferredStringValue;

    public StringColumnInfo(@NotNull String name,
                            @NotNull Function<TemplateProviderInfo, String> valueOfFunction,
                            @Nullable String preferredStringValue) {
      super(name);
      myValueOfFunction = valueOfFunction;
      myPreferredStringValue = preferredStringValue != null && !preferredStringValue.isEmpty() ? preferredStringValue : null;
    }

    @Override
    public int getAdditionalWidth() {
      return UIUtil.DEFAULT_HGAP;
    }

    @Nullable
    @Override
    public String getPreferredStringValue() {
      return myPreferredStringValue;
    }

    public String valueOf(final TemplateProviderInfo template) {
      return myValueOfFunction.fun(template);
    }
  }
}
