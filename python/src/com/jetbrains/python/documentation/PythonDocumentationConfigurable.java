// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.AddEditRemovePanel;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformUtils;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Map;


public final class PythonDocumentationConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private final PythonDocumentationPanel myPanel = new PythonDocumentationPanel();

  @NotNull
  @Override
  public String getId() {
    return PythonDocumentationProvider.DOCUMENTATION_CONFIGURABLE_ID;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return PlatformUtils.isPyCharm() ? PyBundle.message("external.documentation.pycharm")
                                     : PyBundle.message("external.documentation.python.plugin");
  }

  @Override
  public String getHelpTopic() {
    return "preferences.ExternalDocumentation";
  }

  @Override
  public JComponent createComponent() {
    SwingUtilities.updateComponentTreeUI(myPanel); // TODO: create Swing components in this method (see javadoc)
    myPanel.getTable().setShowGrid(false);
    return myPanel;
  }

  @Override
  public void reset() {
    myPanel.getData().clear();
    myPanel.getData().addAll(PythonDocumentationMap.getInstance().getEntries().entrySet());
  }

  @Override
  public boolean isModified() {
    Map<String, String> originalEntries = Map.copyOf(PythonDocumentationMap.getInstance().getEntries());
    Map<String, String> editedEntries = ImmutableMap.copyOf(myPanel.getData());
    return !editedEntries.equals(originalEntries);
  }

  @Override
  public void apply() throws ConfigurationException {
    PythonDocumentationMap.getInstance().setEntries(ImmutableMap.copyOf(myPanel.getData()));
  }

  private static class PythonDocumentationTableModel extends AddEditRemovePanel.TableModel<Map.Entry<String, String>> {
    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public String getColumnName(int columnIndex) {
      return columnIndex == 0
             ? PyBundle.message("external.documentation.column.name.module")
             : PyBundle.message("external.documentation.column.name.url.path.pattern");
    }

    @Override
    public Object getField(Map.Entry<String, String> o, int columnIndex) {
      return columnIndex == 0 ? o.getKey() : o.getValue();
    }
  }

  private static final PythonDocumentationTableModel ourModel = new PythonDocumentationTableModel();

  private static class PythonDocumentationPanel extends AddEditRemovePanel<Map.Entry<String, String>> {
    PythonDocumentationPanel() {
      super(ourModel, new ArrayList<>());
      setRenderer(1, new ColoredTableCellRenderer() {
        @Override
        protected void customizeCellRenderer(@NotNull JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          String text = value == null ? "" : (String) value;
          int pos = 0;
          while(pos < text.length()) {
            int openBrace = text.indexOf('{', pos);
            if (openBrace == -1) openBrace = text.length();
            append(text.substring(pos, openBrace));
            int closeBrace = text.indexOf('}', openBrace);
            if (closeBrace == -1)
              closeBrace = text.length();
            else
              closeBrace++;
            append(text.substring(openBrace, closeBrace), new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.BLUE.darker()));
            pos = closeBrace;
          }
        }
      });
    }

    @Override
    protected Map.Entry<String, String> addItem() {
      return showEditor(null);
    }

    @Nullable
    private Map.Entry<String, String> showEditor(Map.Entry<String, String> entry) {
      PythonDocumentationEntryEditor editor = new PythonDocumentationEntryEditor(this);
      if (entry != null) {
        editor.setEntry(entry);
      }
      editor.show();
      if (editor.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
        return null;
      }
      return editor.getEntry();
    }

    @Override
    protected boolean removeItem(Map.Entry<String, String> o) {
      return true;
    }

    @Override
    protected Map.Entry<String, String> editItem(Map.Entry<String, String> o) {
      return showEditor(o);
    }
  }
}
