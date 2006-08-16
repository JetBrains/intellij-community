package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.projectRoots.ui.ProjectJdksEditor;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectJdksModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootConfigurable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import gnu.trove.TIntArrayList;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;

public class JdkChooserPanel extends JPanel {
  private JList myList = null;
  private DefaultListModel myListModel = null;
  private ProjectJdk myCurrentJdk;
  private Project myProject;

  public JdkChooserPanel(Project project, final SdkType type) {
    super(new BorderLayout());
    myProject = project;
    myListModel = new DefaultListModel();
    myList = new JList(myListModel);
    myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myList.setCellRenderer(new ProjectJdkListRenderer());
    //noinspection HardCodedStringLiteral
    myList.setPrototypeCellValue("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
    fillList(type);

    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myCurrentJdk = (ProjectJdk)myList.getSelectedValue();
      }
    });
    myList.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2 && myProject == null) {
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
    ProjectJdksEditor editor = new ProjectJdksEditor((ProjectJdk)myList.getSelectedValue(),
                                                     myProject != null ? myProject : ProjectManager.getInstance().getDefaultProject(),
                                                     myList);
    editor.show();
    if (editor.isOK()) {
      ProjectJdk selectedJdk = editor.getSelectedJdk();
      updateList(selectedJdk, null);
    }
  }

  public void updateList(final ProjectJdk selectedJdk, final SdkType type) {
    final int[] selectedIndices = myList.getSelectedIndices();
    fillList(type);
    // restore selection
    if (selectedJdk != null) {
      TIntArrayList list = new TIntArrayList();
      for (int i = 0; i < myListModel.size(); i++) {
        final ProjectJdk jdk = (ProjectJdk)myListModel.getElementAt(i);
        if (Comparing.strEqual(jdk.getName(), selectedJdk.getName())){
          list.add(i);
        }
      }
      final int[] indicesToSelect = list.toNativeArray();
      if (indicesToSelect.length > 0) {
        myList.setSelectedIndices(indicesToSelect);
      }
      else if (myList.getModel().getSize() > 0) {
        myList.setSelectedIndex(0);
      }
    } else {
      myList.setSelectedIndices(selectedIndices);
    }

    myCurrentJdk = (ProjectJdk)myList.getSelectedValue();
  }

  public JList getPreferredFocusedComponent() {
    return myList;
  }

  private void fillList(final SdkType type) {
    myListModel.clear();
    final ProjectJdk[] jdks;
    if (myProject == null) {
      jdks = ProjectJdkTable.getInstance().getAllJdks();
    }
    else {
      final ProjectJdksModel projectJdksModel = ProjectRootConfigurable.getInstance(myProject).getProjectJdksModel();
      if (!projectJdksModel.isInitialized()){ //should be initialized
        projectJdksModel.reset(myProject);
      }
      final Set<ProjectJdk> compatibleJdks = new HashSet<ProjectJdk>();
      final Collection<ProjectJdk> collection = projectJdksModel.getProjectJdks().values();
      for (ProjectJdk projectJdk : collection) {
        if (type == null || projectJdk.getSdkType() == type) {
          compatibleJdks.add(projectJdk);
        }
      }
      jdks = compatibleJdks.toArray(new ProjectJdk[compatibleJdks.size()]);
    }
    Arrays.sort(jdks, new Comparator<ProjectJdk>() {
      public int compare(final ProjectJdk o1, final ProjectJdk o2) {
        return o1.getName().compareToIgnoreCase(o2.getName());
      }
    });
    for (ProjectJdk jdk : jdks) {
      myListModel.addElement(jdk);
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

  private static ProjectJdk showDialog(final Project project, String title, final Component parent, ProjectJdk jdkToSelect) {
    final JdkChooserPanel jdkChooserPanel = new JdkChooserPanel(project, null);
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
    final ProjectJdk jdk = showDialog(project, ProjectBundle.message("module.libraries.target.jdk.select.title"), WindowManagerEx.getInstanceEx().getFrame(project), projectJdk);
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