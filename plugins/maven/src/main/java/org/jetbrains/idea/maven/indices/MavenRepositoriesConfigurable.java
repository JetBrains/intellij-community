// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.AnimatedIcon;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TimerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenRepositoryInfo;
import org.jetbrains.idea.maven.project.MavenConfigurableBundle;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class MavenRepositoriesConfigurable implements SearchableConfigurable, Configurable.NoScroll {


  private final Project myProject;
  private JPanel myMainPanel;
  private JBTable myRepositoriesTable;
  private JButton myUpdateButton;
  private JPanel myBorderPanel;

  private AnimatedIcon myUpdatingIcon;
  private Timer myRepaintTimer;
  private ActionListener myTimerListener;

  public MavenRepositoriesConfigurable(Project project) {
    myProject = project;
    configControls();

    myBorderPanel.setBorder(
      IdeBorderFactory.createTitledBorder(MavenConfigurableBundle.message("maven.settings.repositories.title"), false, JBUI.insetsTop(8))
        .setShowLine(false));
  }

  @Override
  public boolean isModified() {
    return false;
  }

  private void configControls() {
    myUpdateButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doUpdateIndex();
      }
    });

    myRepositoriesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        updateButtonsState();
      }
    });

    myRepositoriesTable.setDefaultRenderer(Object.class, new MyCellRenderer());
    myRepositoriesTable.setDefaultRenderer(IndexUpdatingState.class, new MyIconCellRenderer());

    myRepositoriesTable.getEmptyText().setText(MavenConfigurableBundle.message("maven.settings.repositories.no"));

    updateButtonsState();
  }

  private void updateButtonsState() {
    boolean hasSelection = !myRepositoriesTable.getSelectionModel().isSelectionEmpty();
    myUpdateButton.setEnabled(hasSelection);
  }

  public void updateIndexHint(int row) {
  }

  private void doUpdateIndex() {
    MavenRepositoryInfo repositoryInfo = getSelectedIndices().stream().findFirst().orElse(null);
    MavenSystemIndicesManager.getInstance().updateIndexContentFromEDT(repositoryInfo);
  }

  private List<MavenRepositoryInfo> getSelectedIndices() {
    List<MavenRepositoryInfo> result = new ArrayList<>();
    for (int i : myRepositoriesTable.getSelectedRows()) {
      result.add(getIndexAt(i));
    }
    return result;
  }

  private MavenRepositoryInfo getIndexAt(int i) {
    MyTableModel model = (MyTableModel)myRepositoriesTable.getModel();
    return model.getIndex(i);
  }

  @Override
  public String getDisplayName() {
    return IndicesBundle.message("maven.repositories.title");
  }

  @Override
  public String getHelpTopic() {
    return "reference.settings.project.maven.repository.indices";
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }

  @Override
  public JComponent createComponent() {
    return myMainPanel;
  }

  @Override
  public void apply() throws ConfigurationException {
  }

  @Override
  public void reset() {
    myRepositoriesTable.setModel(new MyTableModel(MavenIndexUtils.getAllRepositories(myProject)));
    myRepositoriesTable.getColumnModel().getColumn(0).setPreferredWidth(400);
    myRepositoriesTable.getColumnModel().getColumn(1).setPreferredWidth(50);
    myRepositoriesTable.getColumnModel().getColumn(2).setPreferredWidth(50);
    myRepositoriesTable.getColumnModel().getColumn(3).setPreferredWidth(20);

    myUpdatingIcon = new AsyncProcessIcon(IndicesBundle.message("maven.indices.updating"));
    myUpdatingIcon.resume();

    myTimerListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myRepositoriesTable.repaint();
      }
    };
    myRepaintTimer = TimerUtil.createNamedTimer("Maven repaint", AsyncProcessIcon.CYCLE_LENGTH / AsyncProcessIcon.COUNT, myTimerListener);
    myRepaintTimer.start();
  }

  @Override
  public void disposeUIResources() {
    if (myRepaintTimer == null) return; // has not yet been initialized and reset

    myRepaintTimer.removeActionListener(myTimerListener);
    myRepaintTimer.stop();
    myUpdatingIcon.dispose();
  }

  private class MyTableModel extends AbstractTableModel {
    private final String[] COLUMNS =
      new String[]{
        IndicesBundle.message("maven.index.url"),
        IndicesBundle.message("maven.index.type"),
        IndicesBundle.message("maven.index.updated"),
        ""};

    private final List<MavenRepositoryInfo> myIndices;

    MyTableModel(List<MavenRepositoryInfo> indices) {
      myIndices = indices;
    }

    @Override
    public int getColumnCount() {
      return COLUMNS.length;
    }

    @Override
    public String getColumnName(int index) {
      return COLUMNS[index];
    }

    @Override
    public int getRowCount() {
      return myIndices.size();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      if (columnIndex == 3) return IndexUpdatingState.class;
      return super.getColumnClass(columnIndex);
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      MavenRepositoryInfo i = getIndex(rowIndex);
      return switch (columnIndex) {
        case 0 -> i.getUrl();
        case 1 -> switch (i.getKind()) {
          case LOCAL -> "Local";
          case REMOTE -> "Remote";
          case ONLINE -> "Online";
        };
        case 2 -> "";
        case 3 -> {
          MavenRepositoryInfo repository = i;
          if (repository == null) yield IndexUpdatingState.IDLE;
          yield MavenSystemIndicesManager.getInstance().getUpdatingStateSync(myProject, repository);
        }
        default -> throw new RuntimeException();
      };
    }

    public MavenRepositoryInfo getIndex(int rowIndex) {
      return myIndices.get(rowIndex);
    }
  }

  private static class MyCellRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      // reset custom colors and let DefaultTableCellRenderer to set ones
      setForeground(null);
      setBackground(null);

      return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
    }
  }

  private class MyIconCellRenderer extends MyCellRenderer {
    IndexUpdatingState myState;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myState = (IndexUpdatingState)value;
      return super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Dimension size = getSize();
      switch (myState) {
        case UPDATING -> {
          myUpdatingIcon.setBackground(getBackground());
          myUpdatingIcon.setSize(size.width, size.height);
          myUpdatingIcon.paint(g);
        }
        case WAITING -> {
          int x = (size.width - AllIcons.Process.Step_passive.getIconWidth()) / 2;
          int y = (size.height - AllIcons.Process.Step_passive.getIconHeight()) / 2;
          AllIcons.Process.Step_passive.paintIcon(this, g, x, y);
        }
      }
    }
  }
}
