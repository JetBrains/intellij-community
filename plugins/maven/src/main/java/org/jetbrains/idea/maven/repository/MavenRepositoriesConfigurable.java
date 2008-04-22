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
import java.util.ArrayList;
import java.util.List;

public class MavenRepositoriesConfigurable extends BaseConfigurable {
  private Project myProject;
  private MavenRepositoryManager myManager;

  private JPanel myMainPanel;
  private JTable myTable;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myRemoveButton;
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
        boolean empty = myTable.getSelectionModel().isSelectionEmpty();
        myEditButton.setEnabled(empty);
        myRemoveButton.setEnabled(empty);
      }
    });
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
    MavenRepositoryInfo i = getSelectedIndexInfo();
    if (i == null) return;

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
    try {
      MavenRepositoryInfo i = getSelectedIndexInfo();
      if (i == null) return;
      myManager.remove(i);
      reset();
    }
    catch (MavenRepositoryException e) {
      Messages.showErrorDialog(e.getMessage(), getDisplayName());
    }
    setModified(true);
  }

  private void doUpdateAllRepositories() {
    myManager.startUpdateAll();
  }

  private MavenRepositoryInfo getSelectedIndexInfo() {
    int sel = myTable.getSelectedRow();
    if (sel == -1) return null;
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
    List<MavenRepositoryInfo> infos = new ArrayList<MavenRepositoryInfo>();
    for (MavenRepositoryInfo i : myManager.getInfos()) {
      if (i.isRemote()) infos.add(i);
    }
    myTable.setModel(new MyTableModel(infos));
  }

  public void disposeUIResources() {
  }

  private static class MyTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = new String[] { "ID", "URL" };

    private List<MavenRepositoryInfo> myInfos;

    public MyTableModel(List<MavenRepositoryInfo> infos) {
      myInfos = infos;
    }

    public int getColumnCount() {
      return COLUMNS.length;
    }

    @Override
    public String getColumnName(int index) {
      return COLUMNS[index];
    }

    public int getRowCount() {
      return myInfos.size();
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
      MavenRepositoryInfo i = getIndexInfo(rowIndex);
      switch (columnIndex) {
        case 0: return i.getId();
        case 1: return i.getRepositoryUrl();
      }
      throw new RuntimeException();
    }

    public MavenRepositoryInfo getIndexInfo(int rowIndex) {
      return myInfos.get(rowIndex);
    }
  }
}
