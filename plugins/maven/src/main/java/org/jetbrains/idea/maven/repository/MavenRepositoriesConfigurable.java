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

public class MavenRepositoriesConfigurable extends BaseConfigurable {
  private Project myProject;
  private MavenRepositoryManager myManager;

  private JPanel myMainPanel;
  private JTable myTable;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myRemoveButton;
  private JButton myUpdateButton;
  private JButton myUpdateAllButton;

  public MavenRepositoriesConfigurable(Project project, MavenRepositoryManager m) {
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
    EditMavenRepositoryDialog d = new EditMavenRepositoryDialog(myProject);
    d.show();
    if (!d.isOK()) return;

    try {
      myManager.add(new MavenRepositoryInfo(d.getId(), d.getUrl(), true));
      reset();
      int lastIndex = myTable.getRowCount() - 1;
      myTable.getSelectionModel().setSelectionInterval(lastIndex, lastIndex);
    }
    catch (MavenRepositoryException e) {
      Messages.showErrorDialog(e.getMessage(), getDisplayName());
    }
    setModified(true);
  }

  private void doEditRepository() {
    if (!canEdit()) return;

    MavenRepositoryInfo i = getSelectedIndexInfo();
    EditMavenRepositoryDialog d = new EditMavenRepositoryDialog(
        myProject, i.getId(), i.getRepositoryUrl());

    d.show();
    if (!d.isOK()) return;

    try {
      myManager.change(i, d.getId(), d.getUrl(), true);
    }
    catch (MavenRepositoryException e) {
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
    catch (MavenRepositoryException e) {
      Messages.showErrorDialog(e.getMessage(), getDisplayName());
    }
    setModified(true);
  }

  private void doUpdateRepository() {
    MavenRepositoryInfo i = getSelectedIndexInfo();
    myManager.startUpdate(i);
  }

  private void doUpdateAllRepositories() {
    myManager.startUpdateAll();
  }

  private boolean canEdit() {
    return myTable.getSelectedRow() > 0;
  }

  private MavenRepositoryInfo getSelectedIndexInfo() {
    int sel = myTable.getSelectedRow();
    if (sel < 0) return null;
    return ((MyTableModel)myTable.getModel()).getIndexInfo(sel);
  }

  public String getDisplayName() {
    return "Maven Repositories";
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
    myTable.setModel(new MyTableModel(myManager.getLocalRepository(),
                                      myManager.getUserRepositories()));
    myTable.getColumnModel().getColumn(0).setPreferredWidth(100);
    myTable.getColumnModel().getColumn(1).setPreferredWidth(400);
  }

  public void disposeUIResources() {
  }

  private static class MyTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = new String[] { "ID", "URL" };

    private MavenRepositoryInfo myLocal;
    private List<MavenRepositoryInfo> myUserInfos;

    public MyTableModel(MavenRepositoryInfo local, List<MavenRepositoryInfo> user) {
      myLocal = local;
      myUserInfos = user;
    }

    public int getColumnCount() {
      return COLUMNS.length;
    }

    @Override
    public String getColumnName(int index) {
      return COLUMNS[index];
    }

    public int getRowCount() {
      return myUserInfos.size() + 1;
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      MavenRepositoryInfo i = getIndexInfo(rowIndex);
      switch (columnIndex) {
        case 0: return i.getId();
        case 1: return i.getRepositoryPathOrUrl();
      }
      throw new RuntimeException();
    }

    public MavenRepositoryInfo getIndexInfo(int rowIndex) {
      if (rowIndex == 0) return myLocal;
      return myUserInfos.get(rowIndex - 1);
    }
  }
}
