package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import gnu.trove.TIntArrayList;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Comparator;

public class JdkChooserPanel extends JPanel {
  private JList myList = null;
  private DefaultListModel myListModel = null;
  private ProjectJdk myCurrentJdk;

  public JdkChooserPanel() {
    super(new BorderLayout());
    myListModel = new DefaultListModel();
    myList = new JList(myListModel);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new ProjectJdkListRenderer());
    //noinspection HardCodedStringLiteral
    myList.setPrototypeCellValue("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
    fillList();

    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myCurrentJdk = (ProjectJdk)myList.getSelectedValue();
      }
    });
    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          editJdkTable();
        }
      }
    });

    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JScrollPane(myList), BorderLayout.CENTER);
    add(panel, BorderLayout.CENTER);
    if (myListModel.getSize() > 0) {
      myList.setSelectedIndex(0);
    }
  }

  public ProjectJdk getChosenJdk() {
    return myCurrentJdk;
  }

  public void editJdkTable() {
    ProjectJdksEditor editor = new ProjectJdksEditor((ProjectJdk)myList.getSelectedValue(), myList);
    editor.show();
    if (editor.isOK()) {
      ProjectJdk selectedJdk = editor.getSelectedJdk();
      Object[] selectedValues = selectedJdk != null ? new Object[]{selectedJdk} : myList.getSelectedValues();
      fillList();
      // restore selection
      TIntArrayList list = new TIntArrayList();
      for (int i = 0; i < selectedValues.length; i++) {
        int idx = myListModel.indexOf(selectedValues[i]);
        if (idx >= 0) {
          list.add(idx);
        }
      }
      final int[] indicesToSelect = list.toNativeArray();
      if (indicesToSelect.length > 0) {
        myList.setSelectedIndices(indicesToSelect);
      }
      else if (myList.getModel().getSize() > 0) {
        myList.setSelectedIndex(0);
      }

      myCurrentJdk = (ProjectJdk)myList.getSelectedValue();
    }
  }

  public JList getPreferredFocusedComponent() {
    return myList;
  }

  private void fillList() {
    myListModel.clear();
    final ProjectJdk[] jdks = ProjectJdkTable.getInstance().getAllJdks();
    Arrays.sort(jdks, new Comparator<ProjectJdk>() {
      public int compare(final ProjectJdk o1, final ProjectJdk o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
    for (int i = 0; i < jdks.length; i++) {
      myListModel.addElement(jdks[i]);
    }
  }

  public JComponent getDefaultFocusedComponent() {
    return myList;
  }

  public void selectJdk(ProjectJdk defaultJdk) {
    final int index = myListModel.indexOf(defaultJdk);
    if (index >= 0) {
      myList.setSelectedIndex(index);
    }
  }

  private static ProjectJdk showDialog(String title, final Component parent, ProjectJdk jdkToSelect) {
    final JdkChooserPanel jdkChooserPanel = new JdkChooserPanel();
    final MyDialog dialog = jdkChooserPanel.new MyDialog(parent);
    if (title != null) {
      dialog.setTitle(title);
    }
    if (jdkToSelect != null) {
      jdkChooserPanel.selectJdk(jdkToSelect);
    }
    dialog.show();
    return dialog.isOK() ? jdkChooserPanel.getChosenJdk() : null;
  }

  public static ProjectJdk chooseAndSetJDK(final Project project) {
    final ProjectJdk projectJdk = ProjectRootManager.getInstance(project).getProjectJdk();
    final ProjectJdk jdk = showDialog(ProjectBundle.message("module.libraries.target.jdk.select.title"), WindowManagerEx.getInstanceEx().getFrame(project), projectJdk);
    if (jdk == null) {
      return null;
    }
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ProjectRootManager.getInstance(project).setProjectJdk(jdk);
      }
    });
    return jdk;
  }

  public class MyDialog extends DialogWrapper implements ListSelectionListener {

    public MyDialog(Component parent) {
      super(parent, true);
      setTitle(IdeBundle.message("title.select.jdk"));
      init();
      myList.addListSelectionListener(this);
      updateOkButton();
    }

    protected String getDimensionServiceKey() {
      return "#com.intellij.ide.util.projectWizard.JdkChooserPanel.MyDialog";
    }

    public void valueChanged(ListSelectionEvent e) {
      updateOkButton();
    }

    private void updateOkButton() {
      setOKActionEnabled(myList.getSelectedValue() != null);
    }

    public void dispose() {
      myList.removeListSelectionListener(this);
      super.dispose();
    }

    protected JComponent createCenterPanel() {
      return JdkChooserPanel.this;
    }

    protected Action[] createActions() {
      return new Action[]{new ConfigureAction(), getOKAction(), getCancelAction()};
    }

    public JComponent getPreferredFocusedComponent() {
      return myList;
    }

    private final class ConfigureAction extends AbstractAction {
      public ConfigureAction() {
        super(IdeBundle.message("button.configure.e"));
        putValue(Action.MNEMONIC_KEY, new Integer('E'));
      }

      public void actionPerformed(ActionEvent e) {
        editJdkTable();
      }
    }
  }


}