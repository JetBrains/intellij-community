package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
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

  public MavenIndicesConfigurable(Project project, MavenIndicesManager m) {
    myProject = project;
    myManager = m;

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
      myManager.add(new RemoteMavenIndex(d.getId(), d.getUrl()));
      reset();
      int lastIndex = myTable.getRowCount() - 1;
      myTable.getSelectionModel().setSelectionInterval(lastIndex, lastIndex);
    }
    catch (MavenIndexException e) {
      Messages.showErrorDialog(e.getMessage(), getDisplayName());
    }
    setModified(true);
  }

  private void doEditRepository() {
    if (!canEdit()) return;

    MavenIndex i = getSelectedIndexInfo();
    EditMavenRepositoryDialog d = new EditMavenRepositoryDialog(i.getId(), i.getRepositoryUrl());

    d.show();
    if (!d.isOK()) return;

    try {
      myManager.change(i, d.getId(), d.getUrl());
    }
    catch (MavenIndexException e) {
      Messages.showErrorDialog(e.getMessage(), getDisplayName());
    }

    setModified(true);
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
    setModified(true);
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
    return sel instanceof RemoteMavenIndex;
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
    myManager.save();
    setModified(false);
  }

  public void reset() {
    myTable.setModel(new MyTableModel(myManager.getIndices()));
    myTable.getColumnModel().getColumn(0).setPreferredWidth(100);
    myTable.getColumnModel().getColumn(1).setPreferredWidth(400);
  }

  public void disposeUIResources() {
  }

  private static class MyTableModel extends AbstractTableModel {
    private static final String[] COLUMNS =
        new String[]{RepositoryBundle.message("maven.index.id"), RepositoryBundle.message("maven.index.url")};

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

    public Object getValueAt(int rowIndex, int columnIndex) {
      MavenIndex i = getIndex(rowIndex);
      switch (columnIndex) {
        case 0:
          if (i instanceof ProjectMavenIndex) return "Project Index";
          if (i instanceof LocalMavenIndex) return "Local Index";
          return i.getId();
        case 1:
          return i.getRepositoryPathOrUrl();
      }
      throw new RuntimeException();
    }

    public MavenIndex getIndex(int rowIndex) {
      return myIndices.get(rowIndex);
    }
  }
}
