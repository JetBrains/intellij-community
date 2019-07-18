package org.jetbrains.plugins.textmate.configuration;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBComboBoxLabel;
import com.intellij.ui.components.editors.JBComboBoxTableCellEditorComponent;
import com.intellij.ui.table.TableView;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.AbstractTableCellEditor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.TextMateService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TextMateThemeMappingPanel implements Disposable {
  private String[] myTmThemeNames;
  private final String[] myIdeaThemeNames;
  private final TableView<Map.Entry<String, String>> myThemeMappingTable;
  private final ListTableModel<Map.Entry<String, String>> myThemeMappingModel;
  private final DynamicThemeColumn myTmThemeColumn;
  private final TextMateService.TextMateBundleListener myListener;

  public TextMateThemeMappingPanel(@NotNull String[] tmThemeNames, @NotNull String[] ideaThemeNames) {
    myTmThemeNames = tmThemeNames;
    myIdeaThemeNames = ideaThemeNames;

    myTmThemeColumn = new DynamicThemeColumn(findLongestString(myTmThemeNames));
    ColumnInfo[] columns = {new StaticThemeColumn(findLongestString(myIdeaThemeNames)), myTmThemeColumn};
    myThemeMappingModel = new ListTableModel<>(columns, new ArrayList<>(), 0);
    myThemeMappingTable = new TableView<>(myThemeMappingModel);
    myThemeMappingTable.setBorder(null);
    myThemeMappingTable.setShowGrid(false);
    myThemeMappingTable.updateColumnSizes();
    myThemeMappingTable.setIntercellSpacing(JBUI.emptySize());

    myListener = new TextMateService.TextMateBundleListener() {
      @Override
      public void colorSchemeChanged() {
        ApplicationManager.getApplication().invokeLater(() -> {
          updateTextMateThemeNamesColumn();
          myThemeMappingTable.updateUI();
        });
      }
    };
    TextMateService.getInstance().addListener(myListener);
  }

  @Override
  public void dispose() {
    TextMateService.getInstance().removeListener(myListener);
  }

  @Nullable
  private static String findLongestString(@NotNull String[] names) {
    String maxString = null;
    for (String name : names) {
      if (maxString == null || maxString.length() < name.length()) {
        maxString = name;
      }
    }
    return maxString;
  }

  public void reset(TextMateSettings settings) {
    updateTextMateThemeNamesColumn();
    final Map<String, String> mapping = getDefaultThemesMapping(settings);
    final TextMateService textMateService = TextMateService.getInstance();
    for (String name : myIdeaThemeNames) {
      mapping.put(name, settings.getTextMateThemeName(name, textMateService));
    }
    myThemeMappingModel.setItems(new ArrayList<>(mapping.entrySet()));
  }

  private void updateTextMateThemeNamesColumn() {
    myTmThemeNames = TextMateService.getInstance().getThemeNames();
    myTmThemeColumn.setMaxStringValue(findLongestString(myTmThemeNames));
  }

  public Map<String, String> getDefaultThemesMapping(@NotNull TextMateSettings settings) {
    HashMap<String, String> result = new HashMap<>();
    final TextMateService textMateService = TextMateService.getInstance();
    for (String name : myIdeaThemeNames) {
      result.put(name, settings.getTextMateThemeName(name, textMateService));
    }
    return result;
  }

  public void apply(TextMateSettings.TextMateSettingsState state) {
    List<Map.Entry<String, String>> tableItems = myThemeMappingTable.getItems();
    Map<String, String> mapping = new HashMap<>(tableItems.size());
    for (Map.Entry<String, String> entry : tableItems) {
      mapping.put(entry.getKey(), entry.getValue());
    }
    state.setThemesMapping(mapping);
  }

  public JPanel getMainComponent() {
    return ToolbarDecorator.createDecorator(myThemeMappingTable)
      .setPreferredSize(new Dimension((int)myThemeMappingTable.getPreferredSize().getWidth(),
                                      (int)myThemeMappingTable.getPreferredScrollableViewportSize().getWidth()))
      .setToolbarBorder(null)
      .disableAddAction()
      .disableRemoveAction()
      .disableUpDownActions()
      .createPanel();
  }

  public boolean isModified(Map<String, String> themesMapping) {
    for (Map.Entry<String, String> entry : myThemeMappingTable.getItems()) {
      String oldValue = themesMapping.get(entry.getKey());
      if (oldValue == null || !oldValue.equals(entry.getValue())) {
        return true;
      }
    }
    return false;
  }

  private class DynamicThemeColumn extends ColumnInfo<Map.Entry<String, String>, String> {
    private String myMaxStringValue;
    private final DefaultTableCellRenderer myCellRenderer = new DefaultTableCellRenderer() {
      private final JBComboBoxLabel myLabel = new JBComboBoxLabel();

      @Override
      public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
        myLabel.setTextFont(table.getFont());
        final String themeName = String.valueOf(value);
        myLabel.setText(themeName);
        if (isSelected) {
          myLabel.setSelectionIcon();
        }
        else {
          myLabel.setRegularIcon();
        }
        myLabel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        myLabel.setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());

        if (!isSelected && !ArrayUtil.contains(themeName, myTmThemeNames)) {
          myLabel.setForeground(JBColor.RED);
        }
        return myLabel;
      }
    };

    DynamicThemeColumn(@Nullable String maxStringValue) {
      super("TextMate Color Scheme");
      setMaxStringValue(maxStringValue);
    }

    public void setMaxStringValue(@Nullable String maxStringValue) {
      myMaxStringValue = maxStringValue == null || getName().length() > maxStringValue.length() ? getName() : maxStringValue;
    }

    @Override
    public TableCellRenderer getRenderer(final Map.Entry<String, String> mapping) {
      return myCellRenderer;
    }

    @Override
    public TableCellEditor getEditor(final Map.Entry<String, String> mapping) {
      final JBComboBoxTableCellEditorComponent themeChooser = new JBComboBoxTableCellEditorComponent();
      themeChooser.setWide(true);
      themeChooser.setText(mapping.getValue());
      themeChooser.setFont(myThemeMappingTable.getFont());
      themeChooser.setOptions((Object[])myTmThemeNames);
      return new AbstractTableCellEditor() {
        @Override
        public Object getCellEditorValue() {
          return themeChooser.getEditorValue();
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
          themeChooser.setCell(table, row, column);
          final int defaultValueIndex = ArrayUtil.find(myTmThemeNames, mapping.getValue());
          themeChooser.setDefaultValue(defaultValueIndex > -1 ? myTmThemeNames[defaultValueIndex] : null);
          themeChooser.setToString(o -> (String)o);
          return themeChooser;
        }
      };
    }

    @Override
    public void setValue(final Map.Entry<String, String> mapping, final String colorScheme) {
      if (colorScheme != null) {
        mapping.setValue(colorScheme.isEmpty() ? TextMateSettings.DEFAULT_THEME_NAME : colorScheme);
      }
    }

    @Override
    public boolean isCellEditable(Map.Entry<String, String> entry) {
      return true;
    }

    @Override
    public TableCellRenderer getCustomizedRenderer(Map.Entry<String, String> o, TableCellRenderer renderer) {
      return super.getCustomizedRenderer(o, renderer);
    }

    @Nullable
    @Override
    public String getMaxStringValue() {
      return myMaxStringValue;
    }

    @Override
    public int getAdditionalWidth() {
      return 10;
    }

    @Nullable
    @Override
    public String valueOf(Map.Entry<String, String> mapping) {
      return mapping.getValue();
    }
  }

  private static class StaticThemeColumn extends ColumnInfo<Map.Entry<String, String>, String> {
    private final String myMaxStringValue;

    StaticThemeColumn(String maxStringValue) {
      super("IDE Color Scheme");
      myMaxStringValue = maxStringValue == null || getName().length() > maxStringValue.length() ? getName() : maxStringValue;
    }

    @Nullable
    @Override
    public String getMaxStringValue() {
      return myMaxStringValue;
    }

    @Override
    public int getAdditionalWidth() {
      return 10;
    }

    @Nullable
    @Override
    public String valueOf(Map.Entry<String, String> mapping) {
      return mapping.getKey();
    }
  }
}
