/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.xml.ui;

import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.ui.JBColor;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.TableView;
import com.intellij.util.EventDispatcher;
import com.intellij.util.PlatformIcons;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;

/**
 * @author peter
 */
public abstract class AbstractTableView<T> extends JPanel implements TypeSafeDataProvider {
  private final MyTableView myTable = new MyTableView();
  private final String myHelpID;
  private final String myEmptyPaneText;
  private final JPanel myInnerPanel;
  private final Project myProject;
  private TableCellRenderer[][] myCachedRenderers;
  private EmptyPane myEmptyPane;
  @NonNls private static final String TREE = "Tree";
  @NonNls private static final String EMPTY_PANE = "EmptyPane";
  private final EventDispatcher<ChangeListener> myDispatcher = EventDispatcher.create(ChangeListener.class);
  private final MyListTableModel myTableModel = new MyListTableModel();

  public AbstractTableView(final Project project) {
    this(project, null, null);
  }

  public AbstractTableView(final Project project, final String emptyPaneText, final String helpID) {
    super(new BorderLayout());
    myProject = project;
    myTableModel.setSortable(false);

    myEmptyPaneText = emptyPaneText;
    myHelpID = helpID;

    //ToolTipHandlerProvider.getToolTipHandlerProvider().install(myTable);

    final JTableHeader header = myTable.getTableHeader();
    header.addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        updateTooltip(e);
      }
    });
    header.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        updateTooltip(e);
      }
    });
    header.setReorderingAllowed(false);

    myTable.setRowHeight(PlatformIcons.CLASS_ICON.getIconHeight());
    myTable.setPreferredScrollableViewportSize(JBUI.size(-1, 150));
    myTable.setSelectionMode(allowMultipleRowsSelection() ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);

    myInnerPanel = new JPanel(new CardLayout());
    myInnerPanel.add(ScrollPaneFactory.createScrollPane(myTable), TREE);
    if (getEmptyPaneText() != null) {
      //noinspection HardCodedStringLiteral
      myEmptyPane = new EmptyPane(XmlStringUtil.wrapInHtml(getEmptyPaneText()));
      final JComponent emptyPanel = myEmptyPane.getComponent();
      myInnerPanel.add(emptyPanel, EMPTY_PANE);
    }

    add(myInnerPanel, BorderLayout.CENTER);

    ToolTipManager.sharedInstance().registerComponent(myTable);
  }
  protected TableCellRenderer getTableCellRenderer(final int row, final int column, final TableCellRenderer superRenderer, final Object value) {
    return getTableModel().getColumnInfos()[column].getCustomizedRenderer(value, new StripeTableCellRenderer(superRenderer));
  }

  protected final void installPopup(final String place, final DefaultActionGroup group) {
    PopupHandler.installPopupHandler(myTable, group, place, ActionManager.getInstance());
  }

  public final void setToolbarActions(final AnAction... actions) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    for (final AnAction action : actions) {
      actionGroup.add(action);
    }
    if (getHelpId() != null) {
      actionGroup.add(Separator.getInstance());
      actionGroup.add(new ContextHelpAction(getHelpId()));
    }

    final ActionManager actionManager = ActionManager.getInstance();
    final ToolbarPosition position = getToolbarPosition();
    final ActionToolbar myActionToolbar = actionManager.createActionToolbar(ActionPlaces.PROJECT_VIEW_TOOLBAR, actionGroup, position == ToolbarPosition.TOP || position == ToolbarPosition.BOTTOM);
    myActionToolbar.setTargetComponent(myInnerPanel);
    final JComponent toolbarComponent = myActionToolbar.getComponent();
    final MatteBorder matteBorder = BorderFactory.createMatteBorder(0, 0, position == ToolbarPosition.TOP ? 1 : 0, 0, JBColor.DARK_GRAY);
    toolbarComponent.setBorder(BorderFactory.createCompoundBorder(matteBorder, toolbarComponent.getBorder()));

    getTable().getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        myActionToolbar.updateActionsImmediately();
      }
    });

    add(toolbarComponent, position.getPosition());
  }

  protected final void setErrorMessages(String[] messages) {
    final boolean empty = messages.length == 0;
    final String tooltipText = TooltipUtils.getTooltipText(messages);
    if (myEmptyPane != null) {
      myEmptyPane.getComponent().setBackground(empty ? UIUtil.getTreeTextBackground() : BaseControl.ERROR_BACKGROUND);
      myEmptyPane.getComponent().setToolTipText(tooltipText);
    }
    final JViewport viewport = (JViewport)myTable.getParent();
    final Color tableBackground = empty ? UIUtil.getTableBackground() : BaseControl.ERROR_BACKGROUND;
    viewport.setBackground(tableBackground);
    viewport.setToolTipText(tooltipText);
    myTable.setBackground(tableBackground);
    myTable.setToolTipText(tooltipText);
    if (tooltipText == null) ToolTipManager.sharedInstance().registerComponent(myTable);
  }

  protected final void initializeTable() {
    myTable.setModelAndUpdateColumns(myTableModel);
    if (getEmptyPaneText() != null) {
      final CardLayout cardLayout = ((CardLayout)myInnerPanel.getLayout());
      myTable.getModel().addTableModelListener(new TableModelListener() {
        @Override
        public void tableChanged(TableModelEvent e) {
          cardLayout.show(myInnerPanel, myTable.getRowCount() == 0 ? EMPTY_PANE : TREE);
        }
      });
    }
    tuneTable(myTable);
  }

  protected void adjustColumnWidths() {
    final ColumnInfo[] columnInfos = myTableModel.getColumnInfos();
    for (int i = 0; i < columnInfos.length; i++) {
      final int width = getColumnPreferredWidth(i);
      if (width > 0) {
        myTable.getColumnModel().getColumn(i).setPreferredWidth(width);
      }
    }
  }

  protected int getColumnPreferredWidth(final int i) {
    final ColumnInfo columnInfo = myTableModel.getColumnInfos()[i];
    final java.util.List items = myTableModel.getItems();
    int width = -1;
    for (int j = 0; j < items.size(); j++) {
      final TableCellRenderer renderer = myTable.getCellRenderer(j, i);
      final Component component = renderer.getTableCellRendererComponent(myTable, columnInfo.valueOf(items.get(j)), false, false, j, i);
      width = Math.max(width, component.getPreferredSize().width);
    }
    return width;
  }

  protected String getEmptyPaneText() {
    return myEmptyPaneText;
  }

  protected final void updateTooltip(final MouseEvent e) {
    final int i = myTable.columnAtPoint(e.getPoint());
    if (i >= 0) {
      myTable.getTableHeader().setToolTipText(myTableModel.getColumnInfos()[i].getTooltipText());
    }
  }

  protected void tuneTable(JTable table) {
  }

  protected boolean allowMultipleRowsSelection() {
    return true;
  }

  public final JTable getTable() {
    return myTable;
  }

  public final ListTableModel getTableModel() {
    return myTableModel;
  }

  @Override
  public void calcData(DataKey key, DataSink sink) {
    if (PlatformDataKeys.HELP_ID.equals(key)) {
      sink.put(PlatformDataKeys.HELP_ID, getHelpId());
    }
  }

  private String getHelpId() {
    return myHelpID;
  }

  public final void addChangeListener(ChangeListener listener) {
    myDispatcher.addListener(listener);
  }

  public final void reset(ColumnInfo[] columnInfos, List<? extends T> data) {
    final boolean columnsChanged = myTableModel.setColumnInfos(columnInfos);
    final boolean dataChanged = !data.equals(myTableModel.getItems());
    final int oldRowCount = myTableModel.getRowCount();
    if ((dataChanged || columnsChanged) && myTable.isEditing()) {
      myTable.getCellEditor().cancelCellEditing();
    }

    if (dataChanged) {
      final int selectedRow = myTable.getSelectedRow();
      myTableModel.setItems(new ArrayList<>(data));
      if (selectedRow >= 0 && selectedRow < myTableModel.getRowCount()) {
        myTable.getSelectionModel().setSelectionInterval(selectedRow, selectedRow);
      }
    }

    myTableModel.cacheValues();
    final int rowCount = myTableModel.getRowCount();
    final int columnCount = myTableModel.getColumnCount();
    myCachedRenderers = new TableCellRenderer[rowCount][columnCount];
    for (int row = 0; row < rowCount; row++) {
      for (int column = 0; column < columnCount; column++) {
        final TableCellRenderer superRenderer = myTable.getSuperCellRenderer(row, column);
        myCachedRenderers[row][column] = getTableCellRenderer(row, column, superRenderer, myTableModel.getItems().get(row));
      }
    }
    if (columnsChanged || oldRowCount == 0 && rowCount != 0) {
      adjustColumnWidths();
    }

    myTable.revalidate();
    myTable.repaint();
  }

  protected abstract void wrapValueSetting(@NotNull T t, Runnable valueSetter);

  protected final void fireChanged() {
    myDispatcher.getMulticaster().changed();
  }

  protected void dispose() {
  }

  public final Project getProject() {
    return myProject;
  }

  protected ToolbarPosition getToolbarPosition() {
    return ToolbarPosition.TOP;
  }

  private class MyListTableModel extends ListTableModel<T> {

    private Object[][] myTableData;

    public MyListTableModel() {
      super(ColumnInfo.EMPTY_ARRAY);
      setSortable(false);
    }

    @Override
    public Object getValueAt(final int rowIndex, final int columnIndex) {
      return myTableData[rowIndex][columnIndex];
    }

    void cacheValues() {
      final int rowCount = getRowCount();
      final int columnCount = getColumnCount();
      final Object[][] objects = new Object[rowCount][columnCount];
      for (int i = 0; i < rowCount; i++) {
        for (int j = 0; j < columnCount; j++) {
          objects[i][j] = super.getValueAt(i, j);
        }
      }
      myTableData = objects;
    }

    @Override
    public void setValueAt(final Object aValue, final int rowIndex, final int columnIndex) {
      final Object oldValue = getValueAt(rowIndex, columnIndex);
      if (!Comparing.equal(oldValue, aValue)) {
        wrapValueSetting(getItems().get(rowIndex),
                         () -> super.setValueAt("".equals(aValue) ? null : aValue, rowIndex, columnIndex));
      }
    }

  }

  protected static enum ToolbarPosition {
    TOP(BorderLayout.NORTH),
    LEFT(BorderLayout.WEST),
    RIGHT(BorderLayout.EAST),
    BOTTOM(BorderLayout.SOUTH);

    private final String myPosition;

    private ToolbarPosition(final String position) {
      myPosition = position;
    }

    public String getPosition() {
      return myPosition;
    }
  }

  public interface ChangeListener extends EventListener {
    void changed();
  }

  protected class MyTableView extends TableView {

    public final TableCellRenderer getSuperCellRenderer(int row, int column) {
      return super.getCellRenderer(row, column);
    }

    @Override
    public final TableCellRenderer getCellRenderer(int row, int column) {
      return myCachedRenderers[row][column];
    }
  }
}
