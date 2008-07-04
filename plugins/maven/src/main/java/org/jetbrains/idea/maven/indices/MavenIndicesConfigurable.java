package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.AnimatedIcon;
import com.intellij.util.ui.AsyncProcessIcon;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class MavenIndicesConfigurable extends BaseConfigurable {
  private Project myProject;
  private MavenIndicesManager myManager;

  private JPanel myMainPanel;
  private JTable myTable;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myRemoveButton;
  private JButton myUpdateButton;
  private JButton myUpdateAllButton;

  private Timer myRepaintTimer;

  private AnimatedIcon myUpdatingIcon;
  private Icon myWaitingIcon = IconLoader.getIcon("/process/step_passive.png");

  public MavenIndicesConfigurable(Project project, MavenIndicesManager m) {
    myProject = project;
    myManager = m;

    myUpdatingIcon = new AsyncProcessIcon(RepositoryBundle.message("maven.indices.updating"));

    myRepaintTimer = new Timer(
        AsyncProcessIcon.CYCLE_LENGTH / AsyncProcessIcon.COUNT,
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            int width = myTable.getColumnModel().getColumn(2).getWidth();
            myTable.repaint(myTable.getWidth() - width, 0, width, myTable.getHeight());
          }
        });

    configControls();
  }

  private void configControls() {
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doAddRepository();
      }
    });

    myEditButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doEditRepository();
      }
    });

    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doRemoveRepository();
      }
    });

    myUpdateButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doUpdateRepository();
      }
    });

    myUpdateAllButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        doUpdateAllRepositories();
      }
    });

    myTable.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          doEditRepository();
        }
      }
    });

    myTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        updateButtonsState();
      }
    });
    updateButtonsState();
  }

  private void updateButtonsState() {
    boolean canEdit = canEdit();
    myEditButton.setEnabled(canEdit);
    myRemoveButton.setEnabled(canEdit);

    boolean hasSelection = !myTable.getSelectionModel().isSelectionEmpty();
    myUpdateButton.setEnabled(hasSelection);
  }

  private void doAddRepository() {
    EditMavenRepositoryDialog d = new EditMavenRepositoryDialog();
    d.show();
    if (!d.isOK()) return;

    try {
      myManager.add(d.getUrl(), MavenIndex.Kind.REMOTE);
      reset();
      int lastIndex = myTable.getRowCount() - 1;
      myTable.getSelectionModel().setSelectionInterval(lastIndex, lastIndex);
    }
    catch (MavenIndexException e) {
      Messages.showErrorDialog(e.getMessage(), getDisplayName());
    }
  }

  private void doEditRepository() {
    if (!canEdit()) return;

    MavenIndex i = getSelectedIndexInfo();
    EditMavenRepositoryDialog d = new EditMavenRepositoryDialog(i.getRepositoryPathOrUrl());

    d.show();
    if (!d.isOK()) return;

    try {
      myManager.change(i, d.getUrl());
      myTable.repaint();
    }
    catch (MavenIndexException e) {
      Messages.showErrorDialog(e.getMessage(), getDisplayName());
    }
  }

  private void doRemoveRepository() {
    if (!canEdit()) return;
    try {
      myManager.remove(getSelectedIndexInfo());
      reset();
    }
    catch (MavenIndexException e) {
      Messages.showErrorDialog(e.getMessage(), getDisplayName());
    }
  }

  private void doUpdateRepository() {
    MavenIndex i = getSelectedIndexInfo();
    myManager.scheduleUpdate(myProject, i);
  }

  private void doUpdateAllRepositories() {
    myManager.scheduleUpdateAll(myProject);
  }

  private boolean canEdit() {
    MavenIndex sel = getSelectedIndexInfo();
    return sel != null && sel.getKind() == MavenIndex.Kind.REMOTE;
  }

  private MavenIndex getSelectedIndexInfo() {
    int sel = myTable.getSelectedRow();
    if (sel < 0) return null;
    return ((MyTableModel)myTable.getModel()).getIndex(sel);
  }

  public String getDisplayName() {
    return RepositoryBundle.message("maven.indices");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
    myTable.setModel(new MyTableModel(myManager.getIndices()));
    myTable.getColumnModel().getColumn(0).setPreferredWidth(400);
    myTable.getColumnModel().getColumn(1).setPreferredWidth(50);
    myTable.getColumnModel().getColumn(2).setPreferredWidth(20);

    myTable.setDefaultRenderer(MavenIndicesManager.IndexUpdatingState.class,
                               new MyIconCellRenderer());

    myRepaintTimer.start();
    myUpdatingIcon.resume();
  }

  public void disposeUIResources() {
    myUpdatingIcon.dispose();
    myRepaintTimer.stop();
  }

  private class MyTableModel extends AbstractTableModel {
    private final String[] COLUMNS =
        new String[]{
            RepositoryBundle.message("maven.index.url"),
            RepositoryBundle.message("maven.index.type"),
            ""};

    private List<MavenIndex> myIndices;

    public MyTableModel(List<MavenIndex> indices) {
      myIndices = indices;
    }

    public int getColumnCount() {
      return COLUMNS.length;
    }

    @Override
    public String getColumnName(int index) {
      return COLUMNS[index];
    }

    public int getRowCount() {
      return myIndices.size();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      if (columnIndex == 2) return MavenIndicesManager.IndexUpdatingState.class;
      return super.getColumnClass(columnIndex);
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      MavenIndex i = getIndex(rowIndex);
      switch (columnIndex) {
        case 0:
          return i.getRepositoryPathOrUrl();
        case 1:
          if (i.getKind() == MavenIndex.Kind.LOCAL) return "Local";
          return "Remote";
        case 2:
          return myManager.getUpdatingState(i);
      }
      throw new RuntimeException();
    }

    public MavenIndex getIndex(int rowIndex) {
      return myIndices.get(rowIndex);
    }
  }

  private class MyIconCellRenderer extends DefaultTableCellRenderer {
    MavenIndicesManager.IndexUpdatingState myState;

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      myState = (MavenIndicesManager.IndexUpdatingState)value;
      return super.getTableCellRendererComponent(table, "", isSelected, hasFocus, row, column);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Dimension size = getSize();
      switch (myState) {
        case UPDATING:
          myUpdatingIcon.setBackground(getBackground());
          myUpdatingIcon.setSize(size.width, size.height);
          myUpdatingIcon.paint(g);
          break;
        case WAITING:
          int x = (size.width - myWaitingIcon.getIconWidth()) / 2;
          int y = (size.height - myWaitingIcon.getIconHeight()) / 2;
          myWaitingIcon.paintIcon(this, g, x, y);
          break;
      }
    }
  }
}
