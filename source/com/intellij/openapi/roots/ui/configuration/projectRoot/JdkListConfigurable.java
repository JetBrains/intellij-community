package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModel;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.TreeMap;

@State(
  name = "JdkListConfigurable.UI",
  storages = {
    @Storage(
      id ="other",
      file = "$WORKSPACE_FILE$"
    )}
)
public class JdkListConfigurable extends BaseStructureConfigurable {

  private final ProjectJdksModel myJdksTreeModel;


  SdkModel.Listener myListener = new SdkModel.Listener() {
    public void sdkAdded(Sdk sdk) {
    }

    public void beforeSdkRemove(Sdk sdk) {
    }

    public void sdkChanged(Sdk sdk, String previousName) {
      updateName();
    }

    public void sdkHomeSelected(Sdk sdk, String newSdkHome) {
      updateName();
    }

    private void updateName() {
      final TreePath path = myTree.getSelectionPath();
      if (path != null) {
        final NamedConfigurable configurable = ((MyNode)path.getLastPathComponent()).getConfigurable();
        if (configurable != null && configurable instanceof JdkConfigurable) {
          configurable.updateName();
        }
      }
    }
  };

  public JdkListConfigurable(final Project project, ProjectStructureConfigurable root) {
    super(project);
    myJdksTreeModel = root.getProjectJdksModel();
    myJdksTreeModel.addListener(myListener);
  }
  protected void processRemovedItems() {
  }

  protected boolean wasObjectStored(final Object editableObject) {
    return false;
  }

  @Nls
  public String getDisplayName() {
    return "JDKs";
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return "reference.settingsdialog.project.structure.jdk ";
  }

  @NonNls
  public String getId() {
    return "jdk.list";
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(final String option) {
    return null;
  }

  protected void loadTree() {
    final TreeMap<ProjectJdk, ProjectJdk> sdks = myJdksTreeModel.getProjectJdks();
    for (ProjectJdk sdk : sdks.keySet()) {
      final JdkConfigurable configurable = new JdkConfigurable((ProjectJdkImpl)sdks.get(sdk), myJdksTreeModel, TREE_UPDATER, myHistory);
      addNode(new MyNode(configurable), myRoot);
    }
  }

  public boolean addJdkNode(final ProjectJdk jdk, final boolean selectInTree) {
    if (!myUiDisposed) {
      addNode(new MyNode(new JdkConfigurable((ProjectJdkImpl)jdk, myJdksTreeModel, TREE_UPDATER, myHistory)), myRoot);
      if (selectInTree) {
        selectNodeInTree(MasterDetailsComponent.findNodeByObject(myRoot, jdk));
      }
      return true;
    }
    return false;
  }

  public void dispose() {
    myJdksTreeModel.removeListener(myListener);
    myJdksTreeModel.disposeUIResources();
  }

  public ProjectJdksModel getJdksTreeModel() {
    return myJdksTreeModel;
  }

  public void reset() {
    super.reset();
    myTree.setRootVisible(false);
  }

  public void apply() throws ConfigurationException {
    boolean modifiedJdks = false;
    for (int i = 0; i < myRoot.getChildCount(); i++) {
      final NamedConfigurable configurable = ((MyNode)myRoot.getChildAt(i)).getConfigurable();
      if (configurable.isModified()) {
        configurable.apply();
        modifiedJdks = true;
      }
    }

    if (myJdksTreeModel.isModified() || modifiedJdks) myJdksTreeModel.apply(this);
    myJdksTreeModel.setProjectJdk(ProjectRootManager.getInstance(myProject).getProjectJdk());
  }

  public boolean isModified() {
    return super.isModified() || myJdksTreeModel.isModified();
  }

  public static JdkListConfigurable getInstance(Project project) {
    return ShowSettingsUtil.getInstance().findProjectConfigurable(project, JdkListConfigurable.class);
  }

  public AbstractAddGroup createAddAction() {
    return new AbstractAddGroup(ProjectBundle.message("add.new.jdk.text")) {
      public AnAction[] getChildren(@Nullable final AnActionEvent e) {
        DefaultActionGroup group = new DefaultActionGroup(ProjectBundle.message("add.new.jdk.text"), true);
        myJdksTreeModel.createAddActions(group, myTree, new Consumer<ProjectJdk>() {
          public void consume(final ProjectJdk projectJdk) {
            addJdkNode(projectJdk, true);
          }
        });
        return group.getChildren(null);
      }
    };
  }

  protected void removeJdk(final ProjectJdk jdk) {
    myJdksTreeModel.removeJdk(jdk);
    myContext.myJdkDependencyCache.remove(jdk);
    myContext.myValidityCache.clear();
  }

  protected
  @Nullable
  String getEmptySelectionString() {
    return "Select a JDK to view or edit its details here";
  }
}
