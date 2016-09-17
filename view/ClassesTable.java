package org.jetbrains.debugger.memory.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.SpeedSearchUtil;
import com.intellij.ui.table.JBTable;
import com.intellij.util.containers.FList;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.memory.component.InstancesTracker;
import org.jetbrains.debugger.memory.tracking.TrackerForNewInstances;
import org.jetbrains.debugger.memory.tracking.TrackingType;
import org.jetbrains.debugger.memory.utils.AbstractTableColumnDescriptor;
import org.jetbrains.debugger.memory.utils.AbstractTableModelWithColumns;
import org.jetbrains.debugger.memory.utils.InstancesProvider;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ClassesTable extends JBTable implements DataProvider, Disposable {
  public static final DataKey<ReferenceType> SELECTED_CLASS_KEY = DataKey.create("ClassesTable.SelectedClass");
  public static final DataKey<XDebugSession> DEBUG_SESSION_KEY = DataKey.create("ClassesTable.DebugSession");
  public static final DataKey<InstancesProvider> NEW_INSTANCES_PROVIDER_KEY =
      DataKey.create("ClassesTable.NewInstances");

  private static final int CLASSES_COLUMN_PREFERRED_WIDTH = 250;
  private static final int COUNT_COLUMN_MIN_WIDTH = 80;
  private static final int COUNT_COLUMN_MAX_WIDTH = 100;
  private static final int DIFF_COLUMN_MIN_WIDTH = 80;
  private static final int DIFF_COLUMN_MAX_WIDTH = 100;

  private final DiffViewTableModel myModel = new DiffViewTableModel();
  private final UnknownDiffValue myUnknownValue = new UnknownDiffValue();
  private final XDebugSession myDebugSession;
  private final Map<ReferenceType, DiffValue> myCounts = new ConcurrentHashMap<>();
  private final InstancesTracker myInstancesTracker;
  private final ClassesFilteredView myParent;

  private boolean myOnlyWithDiff;

  private boolean myOnlyWithInstances;
  private MinusculeMatcher myMatcher = NameUtil.buildMatcher("*").build();
  private String myFilteringPattern = "";

  private volatile List<ReferenceType> myElems = Collections.unmodifiableList(new ArrayList<>());

  // TODO: parent must be unknown in this context
  ClassesTable(@NotNull XDebugSession session, boolean onlyWithDiff, boolean onlyWithInstances,
               @NotNull ClassesFilteredView parent) {
    setModel(myModel);

    myDebugSession = session;
    myOnlyWithDiff = onlyWithDiff;
    myOnlyWithInstances = onlyWithInstances;
    myInstancesTracker = InstancesTracker.getInstance(myDebugSession.getProject());
    myParent = parent;

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

    setShowGrid(false);
    setIntercellSpacing(new JBDimension(0, 0));

    setDefaultRenderer(ReferenceType.class, new MyClassColumnRenderer());
    setDefaultRenderer(Long.class, new MyCountColumnRenderer());
    setDefaultRenderer(DiffValue.class, new MyDiffColumnRenderer());

    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (columnAtPoint(e.getPoint()) != DiffViewTableModel.DIFF_COLUMN_INDEX) {
          return;
        }

        ReferenceType ref = myElems.get(convertRowIndexToModel(rowAtPoint(e.getPoint())));
        TrackerForNewInstances strategy = myParent.getStrategy(ref);
        if (strategy != null) {
          new InstancesWindow(myDebugSession, limit -> strategy.getNewInstances(), ref.name()).show();
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
        return !(myOnlyWithDiff && diff.diff() == 0 || myOnlyWithInstances && !diff.hasInstance())
            && myMatcher.matches(ref.name());

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

  @Nullable
  ReferenceType getClassByName(@NotNull String name) {
    for (ReferenceType ref : myElems) {
      if (name.equals(ref.name())) {
        return ref;
      }
    }

    return null;
  }

  void setBusy(boolean value) {
    setPaintBusy(value);
  }

  void setFilterPattern(String pattern) {
    if (!myFilteringPattern.equals(pattern)) {
      myFilteringPattern = pattern;
      myMatcher = NameUtil.buildMatcher("*" + pattern).build();
      getRowSorter().allRowsChanged();
      if (getSelectedClass() == null && getRowCount() > 0) {
        getSelectionModel().setSelectionInterval(0, 0);
      }
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

  void setClassesAndUpdateCounts(@NotNull List<ReferenceType> classes, @NotNull long[] counts) {
    assert classes.size() == counts.length;
    ReferenceType selectedClass = myModel.getSelectedClassBeforeHided();
    int newSelectedIndex = classes.indexOf(selectedClass);
    boolean isInitialized = !myElems.isEmpty();
    myElems = Collections.unmodifiableList(new ArrayList<>(classes));

    for (int i = 0, size = classes.size(); i < size; i++) {
      ReferenceType ref = classes.get(i);
      DiffValue oldValue = isInitialized && !myCounts.containsKey(ref)
          ? new DiffValue(0, 0)
          : myCounts.getOrDefault(ref, myUnknownValue);
      myCounts.put(ref, oldValue.update(counts[i]));
    }

    showContent();

    if (newSelectedIndex != -1 && !myModel.isHided()) {
      int ix = convertRowIndexToView(newSelectedIndex);
      changeSelection(ix,
          DiffViewTableModel.CLASSNAME_COLUMN_INDEX, false, false);
    }
  }

  void hideContent() {
    myModel.hide();
  }

  private void showContent() {
    myModel.show();
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (SELECTED_CLASS_KEY.is(dataId)) {
      return getSelectedClass();
    }
    if (DEBUG_SESSION_KEY.is(dataId)) {
      return myDebugSession;
    }
    if (NEW_INSTANCES_PROVIDER_KEY.is(dataId)) {
      ReferenceType selectedClass = getSelectedClass();
      if (selectedClass != null) {
        TrackerForNewInstances strategy = myParent.getStrategy(selectedClass);
        if (strategy != null && strategy.isReady()) {
          List<ObjectReference> newInstances = strategy.getNewInstances();
          return (InstancesProvider) limit -> newInstances;
        }
      }
    }

    return null;
  }

  @Override
  public void dispose() {
  }

  @Nullable
  private TrackingType getTrackingType(int row) {
    ReferenceType ref = (ReferenceType) getValueAt(row, DiffViewTableModel.CLASSNAME_COLUMN_INDEX);
    return myInstancesTracker.getTrackingType(ref.name());
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
          new AbstractTableColumnDescriptor("Diff", DiffValue.class) {
            @Override
            public Object getValue(int ix) {
              return myCounts.getOrDefault(myElems.get(ix), myUnknownValue);
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
        fireTableDataChanged();
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

  /**
   * State transmissions for DiffValue and UnknownDiffValue
   * unknown -> diff
   * diff -> diff
   * <p>
   * State descriptions:
   * Unknown - instances count never executed
   * Diff - actual value
   */
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
      return new DiffValue(count);
    }
  }

  private static class DiffValue implements Comparable<DiffValue> {
    private long myOldCount;

    private long myCurrentCount;

    DiffValue(long count) {
      this(count, count);
    }

    DiffValue(long old, long current) {
      myCurrentCount = current;
      myOldCount = old;
    }

    DiffValue update(long count) {
      myOldCount = myCurrentCount;
      myCurrentCount = count;
      return this;
    }

    boolean hasInstance() {
      return myCurrentCount > 0;
    }

    long diff() {
      return myCurrentCount - myOldCount;
    }

    @Override
    public int compareTo(@NotNull DiffValue o) {
      return Long.compare(diff(), o.diff());
    }
  }

  private abstract class MyTableCellRenderer extends ColoredTableCellRenderer {
    @Override
    protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean isSelected,
                                         boolean hasFocus, int row, int column) {
      TrackingType trackingType = getTrackingType(row);
      if (trackingType != null && !isSelected) {
        JBColor color = myParent.isTrackingActive(myElems.get(convertRowIndexToModel(row)))
            ? trackingType.color()
            : TrackingType.inactiveColor();
        setBackground(UIUtil.toAlpha(color, 20));
      }

      if (value != null) {
        addText(table, value, isSelected, hasFocus, row, column);
      }
    }

    protected abstract void addText(JTable table, @NotNull Object value, boolean isSelected,
                                    boolean hasFocus, int row, int column);
  }

  private class MyClassColumnRenderer extends MyTableCellRenderer {
    @Override
    protected void addText(JTable table, @NotNull Object value, boolean isSelected,
                           boolean hasFocus, int row, int column) {
      String presentation = ((ReferenceType) value).name();
      append(" ");
      if (isSelected) {
        FList<TextRange> textRanges = myMatcher.matchingFragments(presentation);
        if (textRanges != null) {
          SimpleTextAttributes attributes = new SimpleTextAttributes(getBackground(), getForeground(), null,
              SimpleTextAttributes.STYLE_SEARCH_MATCH);
          SpeedSearchUtil.appendColoredFragments(this, presentation, textRanges,
              SimpleTextAttributes.REGULAR_ATTRIBUTES, attributes);
        }
      } else {
        append(String.format("%s", presentation), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
  }

  private class MyCountColumnRenderer extends MyTableCellRenderer {
    @Override
    protected void addText(JTable table, @NotNull Object value, boolean isSelected,
                           boolean hasFocus, int row, int column) {
      setTextAlign(SwingConstants.RIGHT);
      append(value.toString());
    }
  }

  private class MyDiffColumnRenderer extends MyTableCellRenderer {
    private final SimpleTextAttributes myClickableCellAttributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE,
        JBColor.BLUE);

    @Override
    protected void addText(JTable table, @NotNull Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      long diff = ((DiffValue) value).diff();
      setTextAlign(SwingConstants.RIGHT);

      ReferenceType ref = myElems.get(convertRowIndexToModel(row));
      TrackerForNewInstances strategy = myParent.getStrategy(ref);
      SimpleTextAttributes attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
      if (strategy != null && strategy.isReady()) {
        attributes = myClickableCellAttributes;
      }

      String text = String.format("%s%d", diff > 0 ? "+" : "", diff);
      append(text, attributes);
    }
  }
}
