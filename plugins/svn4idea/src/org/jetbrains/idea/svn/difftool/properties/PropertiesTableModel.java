package org.jetbrains.idea.svn.difftool.properties;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.Disposer;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.DiffDrawUtil;
import com.intellij.diff.util.DiffUtil;
import com.intellij.diff.util.Side;
import com.intellij.diff.util.TextDiffType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.EditorSettingsProvider;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.difftool.properties.SvnPropertiesDiffViewer.PropertyDiffRecord;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.EventObject;
import java.util.List;

public class PropertiesTableModel extends ListTableModel<PropertyDiffRecord> {
  @NotNull private final MyNameTableCellRenderer myNameRenderer;
  @NotNull private final MyValueTableCellRenderer myValueRenderer;
  @NotNull private final MyValueTableCellEditor myValueEditor;

  public PropertiesTableModel(@Nullable String title1,
                              @Nullable String title2,
                              @NotNull Disposable disposable) {
    myNameRenderer = new MyNameTableCellRenderer();
    myValueRenderer = new MyValueTableCellRenderer(disposable);
    myValueEditor = new MyValueTableCellEditor();

    ValueColumnInfo left = new ValueColumnInfo(Side.LEFT, title1, myValueRenderer, myValueEditor);
    ValueColumnInfo right = new ValueColumnInfo(Side.RIGHT, title2, myValueRenderer, myValueEditor);
    NameColumnInfo middle = new NameColumnInfo(myNameRenderer);

    setColumnInfos(new ColumnInfo[]{left, middle, right});
  }

  private static String getValueTitle(@NotNull Side side, @Nullable String title) {
    if (title != null) return "Value in " + title;
    return side.isLeft() ? "Value Before" : "Value After";
  }

  //
  // Columns
  //

  private static class NameColumnInfo extends ColumnInfo<PropertyDiffRecord, String> {
    @NotNull private final MyNameTableCellRenderer myRenderer;

    public NameColumnInfo(@NotNull MyNameTableCellRenderer renderer) {
      super("SVN Property Name");
      myRenderer = renderer;
    }

    @Nullable
    @Override
    public String valueOf(PropertyDiffRecord data) {
      return data.getName();
    }

    @Nullable
    @Override
    public String getPreferredStringValue() {
      return "svn:some_property";
    }


    @Nullable
    @Override
    public String getMaxStringValue() {
      return "svn:some_long_property";
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(PropertyDiffRecord record) {
      myRenderer.setRecord(record);
      return myRenderer;
    }
  }

  private static class ValueColumnInfo extends ColumnInfo<PropertyDiffRecord, String> {
    @NotNull private final Side mySide;
    @NotNull private final MyValueTableCellRenderer myRenderer;
    @NotNull private final MyValueTableCellEditor myEditor;

    public ValueColumnInfo(@NotNull Side side,
                           @Nullable String title,
                           @NotNull MyValueTableCellRenderer renderer,
                           @NotNull MyValueTableCellEditor editor) {
      super(getValueTitle(side, title));
      mySide = side;
      myRenderer = renderer;
      myEditor = editor;
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(final PropertyDiffRecord record) {
      myRenderer.setRecord(record);
      myRenderer.setSide(mySide);
      return myRenderer;
    }

    @Nullable
    @Override
    public String valueOf(@NotNull PropertyDiffRecord record) {
      return StringUtil.notNullize(mySide.select(record.getBefore(), record.getAfter()));
    }

    @Override
    public boolean isCellEditable(PropertyDiffRecord record) {
      return true;
    }

    @Nullable
    @Override
    public TableCellEditor getEditor(PropertyDiffRecord record) {
      myEditor.setRecord(record);
      myEditor.setSide(mySide);
      return myEditor;
    }
  }

  //
  // Renderers
  //

  private class MyNameTableCellRenderer implements TableCellRenderer {
    private PropertyDiffRecord myRecord;

    @NotNull private final JPanel myPanel;
    @NotNull private final JBLabel myLabel;

    public MyNameTableCellRenderer() {
      myLabel = new JBLabel();
      myLabel.setFont(UIUtil.getLabelFont());
      myLabel.setHorizontalAlignment(SwingConstants.CENTER);
      myLabel.setVerticalAlignment(SwingConstants.TOP);

      myPanel = new JPanel(new BorderLayout()) {
        @Override
        protected void paintComponent(Graphics g) {
          drawBackground(g);
        }
      };
      myPanel.add(myLabel, BorderLayout.CENTER);
      myPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
    }

    public void setRecord(PropertyDiffRecord record) {
      myRecord = record;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myLabel.setText(myRecord.getName());
      myLabel.setForeground(getRecordColor(myRecord));

      return myPanel;
    }

    private void drawBackground(@NotNull Graphics g) {
      g.setColor(UIUtil.getSeparatorBackground());
      g.fillRect(0, 0, myPanel.getWidth(), myPanel.getHeight());

      GraphicsUtil.setupAAPainting(g);

      int lineHeight = myValueRenderer.getEditorLineHeight();
      int topOffset = myValueRenderer.getTopOffset();

      for (LineFragment fragment : myRecord.getFragments()) {
        TextDiffType type = DiffUtil.getLineDiffType(fragment);

        Color color = type.getColor(myValueRenderer.myEditor);

        int start1 = fragment.getStartLine1() * lineHeight + topOffset;
        int end1 = fragment.getEndLine1() * lineHeight + topOffset;
        int start2 = fragment.getStartLine2() * lineHeight + topOffset;
        int end2 = fragment.getEndLine2() * lineHeight + topOffset;

        DiffDrawUtil.drawCurveTrapezium((Graphics2D)g, 0, myPanel.getWidth(), start1, end1, start2, end2, color, null);
      }
    }
  }


  private class MyValueTableCellRenderer implements TableCellRenderer {
    @NotNull private final EditorEx myEditor;

    private PropertyDiffRecord myRecord;
    private Side mySide;

    public MyValueTableCellRenderer(@NotNull Disposable disposable) {
      EditorTextField field = new EditorTextField(new DocumentImpl("", true), null, FileTypes.PLAIN_TEXT, true, false);
      field.setSupplementary(true);
      field.addNotify(); // creates editor

      myEditor = (EditorEx)ObjectUtils.assertNotNull(field.getEditor());
      myEditor.setRendererMode(true);
      myEditor.getColorsScheme().setColor(EditorColors.CARET_ROW_COLOR, null);
      myEditor.getScrollPane().setBorder(null);

      Disposer.register(disposable, new Disposable() {
        @Override
        public void dispose() {
          EditorFactory.getInstance().releaseEditor(myEditor);
        }
      });
    }

    public void setRecord(PropertyDiffRecord record) {
      myRecord = record;
    }

    public void setSide(Side side) {
      mySide = side;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myEditor.getMarkupModel().removeAllHighlighters();

      setEditorContent(myEditor, myRecord, mySide);

      return myEditor.getComponent();
    }

    public int getEditorLineHeight() {
      return myEditor.getLineHeight();
    }

    public int getTopOffset() {
      return 0;
    }
  }

  //
  // Editor
  //

  private class MyValueTableCellEditor extends AbstractTableCellEditor {
    private PropertyDiffRecord myRecord;
    private Side mySide;

    public MyValueTableCellEditor() {
    }

    public void setRecord(PropertyDiffRecord record) {
      myRecord = record;
    }

    public void setSide(Side side) {
      mySide = side;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
      EditorTextField field = new EditorTextField(new DocumentImpl("", true), null, FileTypes.PLAIN_TEXT, false, false) {
        @Override
        protected boolean shouldHaveBorder() {
          return false;
        }
      };

      field.addSettingsProvider(new EditorSettingsProvider() {
        @Override
        public void customizeSettings(EditorEx editor) {
          setEditorContent(editor, myRecord, mySide);
        }
      });

      return field;
    }

    @Override
    public boolean isCellEditable(EventObject e) {
      return false; // TODO: is property editable
    }

    @Override
    public Object getCellEditorValue() {
      return myRecord;
    }

    @Override
    public boolean stopCellEditing() {
      // TODO: on-the-fly rediff
      // TODO: apply change, rediff, update row height
      return super.stopCellEditing();
    }
  }

  //
  // Helpers
  //

  public void updateRowHeights(@NotNull JTable table) {
    for (int i = 0; i < getRowCount(); i++) {
      int height = getPreferredHeight(getRowValue(i));
      table.setRowHeight(i, height);
    }
  }

  @Nullable
  private static Color getRecordColor(@NotNull PropertyDiffRecord record) {
    TextDiffType type = getRecordDiffType(record);
    if (type == null) return JBColor.black;
    if (type == TextDiffType.DELETED) return FileStatus.COLOR_MISSING;
    if (type == TextDiffType.INSERTED) return FileStatus.COLOR_ADDED;
    if (type == TextDiffType.MODIFIED) return FileStatus.COLOR_MODIFIED;
    throw new IllegalArgumentException();
  }

  @Nullable
  private static TextDiffType getRecordDiffType(@NotNull PropertyDiffRecord record) {
    if (record.getBefore() == null) return TextDiffType.INSERTED;
    if (record.getAfter() == null) return TextDiffType.DELETED;
    if (!record.getFragments().isEmpty()) return TextDiffType.MODIFIED;
    return null; // unchanged
  }

  @NotNull
  private static String getRecordText(@NotNull PropertyDiffRecord record, @NotNull Side side) {
    return StringUtil.notNullize(side.select(record.getBefore(), record.getAfter()));
  }

  private static void setEditorContent(@NotNull EditorEx editor, @NotNull PropertyDiffRecord record, @NotNull Side side) {
    String text = getRecordText(record, side);
    editor.getDocument().setText(text);

    for (LineFragment fragment : record.getFragments()) {
      List<DiffFragment> innerFragments = fragment.getInnerFragments();

      int start = side.getStartOffset(fragment);
      int end = side.getEndOffset(fragment);
      TextDiffType type = DiffUtil.getLineDiffType(fragment);

      DiffDrawUtil.createHighlighter(editor, start, end, type, innerFragments != null);

      if (innerFragments != null) {
        for (DiffFragment innerFragment : innerFragments) {
          int innerStart = side.getStartOffset(innerFragment);
          int innerEnd = side.getEndOffset(innerFragment);
          TextDiffType innerType = DiffUtil.getDiffType(innerFragment);

          innerStart += start;
          innerEnd += start;

          DiffDrawUtil.createInlineHighlighter(editor, innerStart, innerEnd, innerType);
        }
      }
    }
  }

  private int getPreferredHeight(@NotNull PropertyDiffRecord record) {
    String before = StringUtil.notNullize(record.getBefore());
    String after = StringUtil.notNullize(record.getAfter());

    int lines = Math.max(StringUtil.countNewLines(before) + 1, StringUtil.countNewLines(after) + 1);

    int valueHeight = lines * myValueRenderer.myEditor.getLineHeight();
    int nameHeight = myNameRenderer.myPanel.getMinimumSize().height;

    return Math.max(valueHeight, nameHeight);
  }
}
