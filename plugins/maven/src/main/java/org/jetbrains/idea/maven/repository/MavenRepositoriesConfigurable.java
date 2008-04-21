package org.jetbrains.idea.maven.repository;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

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
  private MavenRepositoryIndex myIndex;

  private JPanel myMainPanel;
  private JTable myTable;
  private JButton myAddButton;
  private JButton myEditButton;
  private JButton myRemoveButton;
  private JButton myUpdateAllButton;

  public MavenRepositoriesConfigurable(Project project, MavenRepositoryIndex index) {
    myProject = project;
    myIndex = index;

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
      myIndex.add(new MavenRepositoryIndex.IndexInfo(d.getId(), d.getUrl(), true));
      reset();
      int lastIndex = myTable.getRowCount() - 1;
      myTable.getSelectionModel().setSelectionInterval(lastIndex, lastIndex);
    }
    catch (MavenRepositoryIndexException e) {
      Messages.showErrorDialog(e.getMessage(), getDisplayName());
    }
    setModified(true);
  }

  private void doEditRepository() {
    MavenRepositoryIndex.IndexInfo i = getSelectedIndexInfo();
    if (i == null) return;

    EditMavenRepositoryDialog d = new EditMavenRepositoryDialog(
        myProject, i.getId(), i.getRepositoryUrl());

    d.show();
    if (!d.isOK()) return;

    try {
      myIndex.change(i, d.getId(), d.getUrl(), true);
    }
    catch (MavenRepositoryIndexException e) {
      Messages.showErrorDialog(e.getMessage(), getDisplayName());
    }

    setModified(true);
  }

  private void doRemoveRepository() {
    try {
      MavenRepositoryIndex.IndexInfo i = getSelectedIndexInfo();
      if (i == null) return;
      myIndex.remove(i);
      reset();
    }
    catch (MavenRepositoryIndexException e) {
      Messages.showErrorDialog(e.getMessage(), getDisplayName());
    }
    setModified(true);
  }

  private void doUpdateAllRepositories() {
    new Task.Backgroundable(myProject, "Updating Maven Repositories...", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          for (MavenRepositoryIndex.IndexInfo i : myIndex.getIndexInfos()) {
            myIndex.update(i, indicator);
          }
        }
        catch (final MavenRepositoryIndexException e) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              Messages.showErrorDialog(e.getMessage(), getDisplayName());
            }
          });
        }
      }
    }.queue();
  }

  private MavenRepositoryIndex.IndexInfo getSelectedIndexInfo() {
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
    myIndex.save();
    setModified(false);
  }

  public void reset() {
    List<MavenRepositoryIndex.IndexInfo> infos = new ArrayList<MavenRepositoryIndex.IndexInfo>();
    for (MavenRepositoryIndex.IndexInfo i : myIndex.getIndexInfos()) {
      if (i.isRemote()) infos.add(i);
    }
    myTable.setModel(new MyTableModel(infos));
  }

  public void disposeUIResources() {
  }

  private static class MyTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = new String[] { "ID", "URL" };

    private List<MavenRepositoryIndex.IndexInfo> myInfos;

    public MyTableModel(List<MavenRepositoryIndex.IndexInfo> infos) {
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
      MavenRepositoryIndex.IndexInfo i = getIndexInfo(rowIndex);
      switch (columnIndex) {
        case 0: return i.getId();
        case 1: return i.getRepositoryUrl();
      }
      throw new RuntimeException();
    }

    public MavenRepositoryIndex.IndexInfo getIndexInfo(int rowIndex) {
      return myInfos.get(rowIndex);
    }
  }
}
