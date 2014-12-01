package org.jetbrains.idea.svn.difftool.properties;

import com.intellij.openapi.util.diff.util.Side;
import com.intellij.openapi.util.diff.util.TextDiffType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.ListTableModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.difftool.properties.SvnPropertiesDiffViewer.PropertyDiffRecord;
import org.jetbrains.idea.svn.difftool.properties.SvnPropertiesDiffViewer.PropertyDiffRecord.ColoredChunk;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// FIXME: colors on selection
public class PropertiesTableModel extends ListTableModel<PropertyDiffRecord> {
  @NotNull private final MultiLineTableRenderer myRenderer = new MultiLineTableRenderer();

  public static int NAME_COL = 0;

  public PropertiesTableModel(boolean showLeft, boolean showRight, @Nullable String title1, @Nullable String title2) {
    if (showLeft && showRight) {
      setColumnInfos(new ColumnInfo[]{
        new NameColumnInfo(myRenderer),
        new ValueColumnInfo(Side.LEFT, title1, myRenderer),
        new ValueColumnInfo(Side.RIGHT, title2, myRenderer)});
    }
    else if (showLeft) {
      setColumnInfos(new ColumnInfo[]{
        new NameColumnInfo(myRenderer),
        new ValueColumnInfo(Side.LEFT, title1, myRenderer)});
    }
    else if (showRight) {
      setColumnInfos(new ColumnInfo[]{
        new NameColumnInfo(myRenderer),
        new ValueColumnInfo(Side.RIGHT, title2, myRenderer)});
    }
    else {
      throw new IllegalStateException();
    }
  }

  @Override
  public RowSorter.SortKey getDefaultSortKey() {
    return new RowSorter.SortKey(NAME_COL, SortOrder.DESCENDING);
  }

  private static TextDiffType getDiffType(PropertyDiffRecord record) {
    if (record.isChanged()) {
      if (record.getBefore() == null) {
        return TextDiffType.INSERTED;
      }
      else if (record.getAfter() == null) {
        return TextDiffType.DELETED;
      }
      else {
        return TextDiffType.MODIFIED;
      }
    }
    return null;
  }

  private static String getValueTitle(@NotNull Side side, @Nullable String title) {
    if (title != null) return "Value in " + title;
    return side.isLeft() ? "Value Before" : "Value After";
  }

  private static class NameColumnInfo extends ColumnInfo<PropertyDiffRecord, String> {
    @NotNull private final MultiLineTableRenderer myRenderer;

    public NameColumnInfo(@NotNull MultiLineTableRenderer renderer) {
      super("SVN Property Name");
      myRenderer = renderer;
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(final PropertyDiffRecord record) {
      TextDiffType type = getDiffType(record);
      final Color bgColor = type != null ? type.getIgnoredColor(null) : null;
      myRenderer.setCustomizer(new MultiLineTableRenderer.Customizer() {
        @Override
        public void customize(JTable table, JLabel label, boolean isSelected, Object value) {
          label.setText(record.getName());
          if (bgColor != null) {
            label.setForeground(table.getForeground());
            label.setBackground(bgColor);
          }
        }
      });
      return myRenderer;
    }

    @Nullable
    @Override
    public String valueOf(PropertyDiffRecord data) {
      return data.getName();
    }

    @Nullable
    @Override
    public String getPreferredStringValue() {
      return "svn:some_property_name";
    }


    @Nullable
    @Override
    public String getMaxStringValue() {
      return "svn:some_really_big_cool_property_name";
    }

    @Nullable
    @Override
    public Comparator<PropertyDiffRecord> getComparator() {
      return new Comparator<PropertyDiffRecord>() {
        @Override
        public int compare(PropertyDiffRecord o1, PropertyDiffRecord o2) {
          return StringUtil.naturalCompare(o1.getName(), o2.getName());
        }
      };
    }
  }

  private static class ValueColumnInfo extends ColumnInfo<PropertyDiffRecord, String> {
    @NotNull private final MultiLineTableRenderer myRenderer;
    @NotNull private final Side mySide;

    public ValueColumnInfo(@NotNull Side side, @Nullable String title, @NotNull MultiLineTableRenderer renderer) {
      super(getValueTitle(side, title));
      mySide = side;
      myRenderer = renderer;
    }

    @Nullable
    private List<ColoredChunk> getChunks(@NotNull PropertyDiffRecord record) {
      return mySide.isLeft() ? record.getBefore() : record.getAfter();
    }

    @Nullable
    @Override
    public TableCellRenderer getRenderer(final PropertyDiffRecord record) {
      TextDiffType type = getDiffType(record);
      final Color bgColor = type != null ? type.getIgnoredColor(null) : null;
      myRenderer.setCustomizer(new MultiLineTableRenderer.Customizer() {
        @Override
        public void customize(JTable table, JLabel label, boolean isSelected, Object value) {
          List<ColoredChunk> chunks = getChunks(record);
          if (chunks == null) {
            label.setText("");
            label.setBackground(JBColor.border()); // TODO: better color
          }
          else {
            StringBuilder text = new StringBuilder();
            text.append("<html><body style=\"white-space:nowrap\">");
            for (ColoredChunk chunk : chunks) {
              text.append("<span");
              if (chunk.getType() != null) {
                text.append(" style=\"");
                text.append("background-color:#").append(Integer.toString(chunk.getType().getColor(null).getRGB() & 0xFFFFFF, 16));
                text.append("\"");
              }
              text.append('>');
              text.append(StringUtil.escapeXml(chunk.getText()).replaceAll("\n", "<br>"));
              text.append("</span>");
            }
            text.append("</body></html>");
            label.setText(text.toString());
            if (bgColor != null) {
              label.setForeground(table.getForeground());
              label.setBackground(bgColor);
            }
          }
        }
      });
      return myRenderer;
    }

    @Nullable
    @Override
    public String valueOf(@NotNull PropertyDiffRecord record) {
      List<ColoredChunk> chunks = getChunks(record);
      if (chunks == null) return null;

      StringBuilder builder = new StringBuilder();
      for (ColoredChunk chunk : chunks) {
        builder.append(chunk.getText());
      }
      return builder.toString();
    }
  }

  private static class MultiLineTableRenderer extends DefaultTableCellRenderer {
    @NotNull private final List<List<Integer>> rowColHeight = new ArrayList<List<Integer>>();
    @Nullable private Customizer myCustomizer;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
      setBackground(null);
      final JLabel label = (JLabel)super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      if (myCustomizer != null) myCustomizer.customize(table, label, isSelected, value);

      adjustRowHeight(table, row, column);

      return label;
    }

    /**
     * Calculate the new preferred height for a given row, and sets the height on the table.
     */
    private void adjustRowHeight(JTable table, int row, int column) {
      //The trick to get this to work properly is to set the width of the column to the
      //textarea. The reason for this is that getPreferredSize(), without a width tries
      //to place all the text in one line. By setting the size with the with of the column,
      //getPreferredSize() returnes the proper height which the row should have in
      //order to make room for the text.
      int prefH = getPreferredSize().height;
      while (rowColHeight.size() <= row) {
        rowColHeight.add(new ArrayList<Integer>(column));
      }
      List<Integer> colHeights = rowColHeight.get(row);
      while (colHeights.size() <= column) {
        colHeights.add(0);
      }
      colHeights.set(column, prefH);
      int maxH = prefH;
      for (Integer colHeight : colHeights) {
        if (colHeight > maxH) {
          maxH = colHeight;
        }
      }
      if (table.getRowHeight(row) != maxH) {
        table.setRowHeight(row, maxH);
      }
    }

    public void setCustomizer(@Nullable Customizer customizer) {
      myCustomizer = customizer;
    }

    public interface Customizer {
      void customize(JTable table, JLabel textArea, boolean isSelected, Object value);
    }
  }
}
