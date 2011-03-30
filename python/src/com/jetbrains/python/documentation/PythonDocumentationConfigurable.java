package com.jetbrains.python.documentation;

import com.google.common.collect.Sets;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.AddEditRemovePanel;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * @author yole
 */
public class PythonDocumentationConfigurable implements Configurable {
  private PythonDocumentationPanel myPanel = new PythonDocumentationPanel();

  @Nls
  @Override
  public String getDisplayName() {
    return "External Documentation";
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public String getHelpTopic() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public void reset() {
    myPanel.getData().clear();
    myPanel.getData().addAll(PythonDocumentationMap.getInstance().getEntries());
  }

  @Override
  public boolean isModified() {
    HashSet<PythonDocumentationMap.Entry> originalEntries = Sets.newHashSet(PythonDocumentationMap.getInstance().getEntries());
    HashSet<PythonDocumentationMap.Entry> editedEntries = Sets.newHashSet(myPanel.getData());
    return !editedEntries.equals(originalEntries);
  }

  @Override
  public void apply() throws ConfigurationException {
    PythonDocumentationMap.getInstance().setEntries(myPanel.getData());
  }

  @Override
  public void disposeUIResources() {
  }

  private static class PythonDocumentationTableModel extends AddEditRemovePanel.TableModel<PythonDocumentationMap.Entry> {
    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public String getColumnName(int columnIndex) {
      return columnIndex == 0 ? "Module Name" : "URL Pattern";
    }

    @Override
    public Object getField(PythonDocumentationMap.Entry o, int columnIndex) {
      return columnIndex == 0 ? o.getPrefix() : o.getUrlPattern();
    }
  }

  private static final PythonDocumentationTableModel ourModel = new PythonDocumentationTableModel();

  private static class PythonDocumentationPanel extends AddEditRemovePanel<PythonDocumentationMap.Entry> {
    public PythonDocumentationPanel() {
      super(ourModel, new ArrayList<PythonDocumentationMap.Entry>());
      setRenderer(1, new ColoredTableCellRenderer() {
        @Override
        protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus, int row, int column) {
          String text = (String) value;
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
            append(text.substring(openBrace, closeBrace), new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.blue.darker()));
            pos = closeBrace;
          }
        }
      });
    }

    @Override
    protected PythonDocumentationMap.Entry addItem() {
      return showEditor(null);
    }

    @Nullable
    private PythonDocumentationMap.Entry showEditor(PythonDocumentationMap.Entry entry) {
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
    protected boolean removeItem(PythonDocumentationMap.Entry o) {
      return true;
    }

    @Override
    protected PythonDocumentationMap.Entry editItem(PythonDocumentationMap.Entry o) {
      return showEditor(o);
    }
  }

}
