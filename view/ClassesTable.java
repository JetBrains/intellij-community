package org.jetbrains.debugger.memory.view;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.utils.AbstractTableColumnDescriptor;
import org.jetbrains.debugger.memory.utils.AbstractTableModelWithColumns;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

class ClassesTable extends JBTable {
  private static final int CLASSES_COLUMN_PREFERRED_WIDTH = 250;
  private static final int COUNT_COLUMN_MIN_WIDTH = 80;
  private static final int COUNT_COLUMN_MAX_WIDTH = 100;
  private static final int DIFF_COLUMN_MIN_WIDTH = 80;
  private static final int DIFF_COLUMN_MAX_WIDTH = 100;

  private static final String EMPTY_TABLE_CONTENT = "The application is running";

  private final DiffViewTableModel myModel = new DiffViewTableModel();
  private final UnknownDiffValue myUnknownValue = new UnknownDiffValue();

  private boolean myOnlyWithDiff;
  private boolean myOnlyWithInstances;

  private String myFilteringPattern = "";
  private ConcurrentHashMap<ReferenceType, DiffValue> myCounts = new ConcurrentHashMap<>();

  private volatile List<ReferenceType> myElems = Collections.unmodifiableList(new ArrayList<>());

  ClassesTable(boolean onlyWithDiff, boolean onlyWithInstances) {
    setModel(myModel);

    myOnlyWithDiff = onlyWithDiff;
    myOnlyWithInstances = onlyWithInstances;

    getEmptyText().setText(EMPTY_TABLE_CONTENT);

    TableColumn classesColumn = getColumnModel().getColumn(DiffViewTableModel.CLASSNAME_COLUMN_INDEX);
    TableColumn countColumn = getColumnModel().getColumn(DiffViewTableModel.COUNT_COLUMN_INDEX);
    TableColumn diffColumn = getColumnModel().getColumn(DiffViewTableModel.DIFF_COLUMN_INDEX);

    setAutoResizeMode(AUTO_RESIZE_ALL_COLUMNS);
    classesColumn.setPreferredWidth(JBUI.scale(CLASSES_COLUMN_PREFERRED_WIDTH));
    classesColumn.setResizable(false);

    countColumn.setMinWidth(JBUI.scale(COUNT_COLUMN_MIN_WIDTH));
    countColumn.setMaxWidth(JBUI.scale(COUNT_COLUMN_MAX_WIDTH));
    countColumn.setResizable(false);

    diffColumn.setMinWidth(JBUI.scale(DIFF_COLUMN_MIN_WIDTH));
    diffColumn.setMaxWidth(JBUI.scale(DIFF_COLUMN_MAX_WIDTH));
    diffColumn.setResizable(false);

    setDefaultRenderer(ReferenceType.class, new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, @Nullable Object value,
                                           boolean isSelected, boolean hasFocus, int row, int column) {
        String presentation = value == null ? "null" : ((ReferenceType) value).name();
        append(presentation);
        setTransparentIconBackground(true);
        setIcon(AllIcons.Nodes.Class);
      }
    });

    setDefaultRenderer(Integer.class, new ColoredTableCellRenderer() {
      @Override
      protected void customizeCellRenderer(JTable table, @Nullable Object value,
                                           boolean selected, boolean hasFocus, int row, int column) {
        if (value != null) {
          int val = (Integer) value;
          append(String.format("%s%d", val > 0 ? "+" : "", val));
          setTextAlign(SwingConstants.RIGHT);
        }
      }
    });

    TableRowSorter<DiffViewTableModel> sorter = new TableRowSorter<>(myModel);
    sorter.setRowFilter(new RowFilter<DiffViewTableModel, Integer>() {
      @Override
      public boolean include(Entry<? extends DiffViewTableModel, ? extends Integer> entry) {
        int ix = entry.getIdentifier();
        ReferenceType ref = myElems.get(ix);
        DiffValue diff = myCounts.getOrDefault(ref, myUnknownValue);
        if (myOnlyWithDiff && diff.diff() == 0 ||
            myOnlyWithInstances && !diff.hasInstance()) {
          return false;
        }

        String name = ref.name().toLowerCase();
        String pattern = myFilteringPattern;
        return name.contains(pattern) || match(pattern.toLowerCase(), name.substring(name.lastIndexOf('.') + 1));
      }
    });

    List<RowSorter.SortKey> myDefaultSortingKeys = Arrays.asList(
        new RowSorter.SortKey(DiffViewTableModel.DIFF_COLUMN_INDEX, SortOrder.DESCENDING),
        new RowSorter.SortKey(DiffViewTableModel.COUNT_COLUMN_INDEX, SortOrder.DESCENDING),
        new RowSorter.SortKey(DiffViewTableModel.CLASSNAME_COLUMN_INDEX, SortOrder.ASCENDING)
    );
    sorter.setSortKeys(myDefaultSortingKeys);
    setRowSorter(sorter);
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }

  @Nullable
  ReferenceType getSelectedClass() {
    int selectedRow = getSelectedRow();
    if (selectedRow != -1) {
      int ix = convertRowIndexToModel(selectedRow);
      return myElems.get(ix);
    }

    return null;
  }

  void setBusy(boolean value) {
    setPaintBusy(value);
  }

  void setFilterPattern(String pattern) {
    if (!myFilteringPattern.equals(pattern)) {
      myFilteringPattern = pattern;
      getRowSorter().allRowsChanged();
    }
  }

  void setFilteringByInstanceExists(boolean value) {
    if (value != myOnlyWithInstances) {
      myOnlyWithInstances = value;
      getRowSorter().allRowsChanged();
    }
  }

  void setFilteringByDiffNonZero(boolean value) {
    if (myOnlyWithDiff != value) {
      myOnlyWithDiff = value;
      getRowSorter().allRowsChanged();
    }
  }

  void setClassesAndCounts(List<ReferenceType> classes, long[] counts) {
    ReferenceType selectedClass = myModel.getSelectedClassBeforeHided();
    final int newSelectedIndex = classes.indexOf(selectedClass);
    myElems = Collections.unmodifiableList(new ArrayList<>(classes));
    for (int i = 0, size = myElems.size(); i < size; i++) {
      ReferenceType ref = myElems.get(i);
      myCounts.put(ref, myCounts.getOrDefault(ref, myUnknownValue).update(counts[i]));
    }

    showContent();

    if (newSelectedIndex != -1 && !myModel.isHided()) {
      changeSelection(convertRowIndexToView(newSelectedIndex),
          DiffViewTableModel.CLASSNAME_COLUMN_INDEX, false, false);
    }

    setBusy(false);
  }

  void hideContent() {
    myModel.hide();
  }

  private void showContent() {
    myModel.show();
  }

  // TODO: use jb filter
  private boolean match(String pattern, String value) {
    int i = 0;
    int j = 0;
    while (j < value.length()) {
      if (i < pattern.length() && value.charAt(j) == pattern.charAt(i)) {
        i++;
      }
      j++;
    }

    return i == pattern.length();
  }

  private class DiffViewTableModel extends AbstractTableModelWithColumns {
    private final static int CLASSNAME_COLUMN_INDEX = 0;
    private final static int COUNT_COLUMN_INDEX = 1;
    private final static int DIFF_COLUMN_INDEX = 2;

    // Workaround: save selection after content of classes table has been hided
    private ReferenceType mySelectedClassWhenHided = null;
    private boolean myIsWithContent = false;

    DiffViewTableModel() {
      super(new AbstractTableColumnDescriptor[]{
          new AbstractTableColumnDescriptor("Class", ReferenceType.class) {
            @Override
            public Object getValue(int ix) {
              return myElems.get(ix);
            }
          },
          new AbstractTableColumnDescriptor("Count", Long.class) {
            @Override
            public Object getValue(int ix) {
              return myCounts.getOrDefault(myElems.get(ix), myUnknownValue).myCurrentCount;
            }
          },
          new AbstractTableColumnDescriptor("Diff", Integer.class) {
            @Override
            public Object getValue(int ix) {
              return myCounts.getOrDefault(myElems.get(ix), myUnknownValue).diff();
            }
          }
      });
    }

    ReferenceType getSelectedClassBeforeHided() {
      return mySelectedClassWhenHided;
    }

    void hide() {
      if (myIsWithContent) {
        mySelectedClassWhenHided = getSelectedClass();
        myIsWithContent = false;
        clearSelection();
        getRowSorter().allRowsChanged();
      }
    }

    void show() {
      if (!myIsWithContent) {
        myIsWithContent = true;
        getRowSorter().allRowsChanged();
      }
    }

    boolean isHided() {
      return !myIsWithContent;
    }

    @Override
    public int getRowCount() {
      return myIsWithContent ? myElems.size() : 0;
    }
  }

  private static class UnknownDiffValue extends DiffValue {
    UnknownDiffValue() {
      super(0);
    }

    @Override
    boolean hasInstance() {
      return true;
    }

    @Override
    DiffValue update(long count) {
      return new DiffValue(count, count);
    }
  }

  private static class DiffValue {
    private final long myOldCount;
    private final long myCurrentCount;

    DiffValue(long count) {
      this(count, count);
    }

    DiffValue(long old, long current) {
      myCurrentCount = current;
      myOldCount = old;
    }

    DiffValue update(long count) {
      return new DiffValue(myCurrentCount, count);
    }

    boolean hasInstance() {
      return myCurrentCount > 0;
    }

    int diff() {
      return (int) (myCurrentCount - myOldCount);
    }
  }
}
