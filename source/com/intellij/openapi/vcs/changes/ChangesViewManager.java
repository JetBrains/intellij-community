/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 11.07.2006
 * Time: 15:29:25
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.actions.IgnoredSettingsAction;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;
import com.intellij.util.Icons;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Collection;

public class ChangesViewManager implements ProjectComponent, JDOMExternalizable {
  private boolean SHOW_FLATTEN_MODE = true;
  private boolean SHOW_IGNORED_MODE = false;

  private ChangesListView myView;
  private JLabel myProgressLabel;
  private final Project myProject;

  private Alarm myRepaintAlarm;

  private boolean myDisposed = false;

  private ChangeListListener myListener = new MyChangeListListener();
  private WolfTheProblemSolver.ProblemListener myProblemListener = new MyProblemListener();
  private ChangesViewContentManager myContentManager;

  @NonNls private static final String ATT_FLATTENED_VIEW = "flattened_view";
  @NonNls private static final String ATT_SHOW_IGNORED = "show_ignored";

  public static ChangesViewManager getInstance(Project project) {
    return project.getComponent(ChangesViewManager.class);
  }

  public ChangesViewManager(Project project, ChangesViewContentManager contentManager) {
    myProject = project;
    myContentManager = contentManager;
    myView = new ChangesListView(project);
    Disposer.register(project, myView);
    myRepaintAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
  }

  public void projectOpened() {
    ChangeListManager.getInstance(myProject).addChangeListListener(myListener);
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    final Content content = PeerFactory.getInstance().getContentFactory().createContent(createChangeViewComponent(), "Local", false);
    content.setCloseable(false);
    myContentManager.addContent(content);

    WolfTheProblemSolver.getInstance(myProject).addProblemListener(myProblemListener);
  }

  public void projectClosed() {
    ChangeListManager.getInstance(myProject).removeChangeListListener(myListener);
    WolfTheProblemSolver.getInstance(myProject).removeProblemListener(myProblemListener);

    myDisposed = true;
    myRepaintAlarm.cancelAllRequests();
  }

  @NonNls @NotNull
  public String getComponentName() {
    return "ChangesViewManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private JComponent createChangeViewComponent() {
    JPanel panel = new JPanel(new BorderLayout());

    DefaultActionGroup group = (DefaultActionGroup) ActionManager.getInstance().getAction("ChangesViewToolbar");

    ActionManager.getInstance().getAction("ChangesView.Refresh").registerCustomShortcutSet(CommonShortcuts.getRerun(), panel);
    ActionManager.getInstance().getAction("ChangesView.NewChangeList").registerCustomShortcutSet(CommonShortcuts.getNew(), panel);
    ActionManager.getInstance().getAction("ChangesView.RemoveChangeList").registerCustomShortcutSet(CommonShortcuts.DELETE, panel);
    ActionManager.getInstance().getAction("ChangesView.Move").registerCustomShortcutSet(CommonShortcuts.getMove(), panel);
    ActionManager.getInstance().getAction("ChangesView.Rename").registerCustomShortcutSet(CommonShortcuts.getRename(), panel);

    final CustomShortcutSet diffShortcut =
      new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, SystemInfo.isMac ? KeyEvent.META_DOWN_MASK : KeyEvent.CTRL_DOWN_MASK));
    ActionManager.getInstance().getAction("ChangesView.Diff").registerCustomShortcutSet(diffShortcut, panel);

    JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.add(createToolbarComponent(group), BorderLayout.WEST);

    DefaultActionGroup visualActionsGroup = new DefaultActionGroup();
    final Expander expander = new Expander();
    visualActionsGroup.add(CommonActionsManager.getInstance().createExpandAllAction(expander, panel));
    visualActionsGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(expander, panel));

    ToggleShowFlattenAction showFlattenAction = new ToggleShowFlattenAction();
    showFlattenAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P,
                                                                                             SystemInfo.isMac
                                                                                             ? KeyEvent.META_DOWN_MASK
                                                                                             : KeyEvent.CTRL_DOWN_MASK)),
                                                panel);
    visualActionsGroup.add(showFlattenAction);
    visualActionsGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY));                                              
    visualActionsGroup.add(new ToggleShowIgnoredAction());
    visualActionsGroup.add(new IgnoredSettingsAction());
    visualActionsGroup.add(new ContextHelpAction(ChangesListView.ourHelpId));
    toolbarPanel.add(createToolbarComponent(visualActionsGroup), BorderLayout.CENTER);


    DefaultActionGroup menuGroup = (DefaultActionGroup) ActionManager.getInstance().getAction("ChangesViewPopupMenu");
    myView.setMenuActions(menuGroup);

    myView.setShowFlatten(SHOW_FLATTEN_MODE);

    myProgressLabel = new JLabel();

    panel.add(toolbarPanel, BorderLayout.WEST);
    panel.add(new JScrollPane(myView), BorderLayout.CENTER);
    panel.add(myProgressLabel, BorderLayout.SOUTH);

    myView.installDndSupport(ChangeListManagerImpl.getInstanceImpl(myProject));
    return panel;
  }

  private static JComponent createToolbarComponent(final DefaultActionGroup group) {
    final ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.CHANGES_VIEW_TOOLBAR, group, false);
    return actionToolbar.getComponent();
  }

  void updateProgressText(final String text, final boolean isError) {
    if (myProgressLabel != null) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myProgressLabel.setText(text);
          myProgressLabel.setForeground(isError ? Color.red : UIUtil.getLabelForeground());
        }
      });
    }
  }

  public void scheduleRefresh() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    myRepaintAlarm.cancelAllRequests();
    myRepaintAlarm.addRequest(new Runnable() {
      public void run() {
        refreshView();
      }
    }, 100, ModalityState.NON_MODAL);
  }

  void refreshView() {
    if (myDisposed || ApplicationManager.getApplication().isUnitTestMode()) return;
    ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
    myView.updateModel(changeListManager.getChangeListsCopy(),
                       changeListManager.getUnversionedFiles(),
                       changeListManager.getDeletedFiles(),
                       changeListManager.getModifiedWithoutEditing(),
                       changeListManager.getSwitchedFilesMap(),
                       SHOW_IGNORED_MODE ? changeListManager.getIgnoredFiles() : null);
  }

  public void readExternal(Element element) throws InvalidDataException {
    SHOW_FLATTEN_MODE = Boolean.valueOf(element.getAttributeValue(ATT_FLATTENED_VIEW)).booleanValue();
    SHOW_IGNORED_MODE = Boolean.valueOf(element.getAttributeValue(ATT_SHOW_IGNORED)).booleanValue();
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(ATT_FLATTENED_VIEW, String.valueOf(SHOW_FLATTEN_MODE));
    element.setAttribute(ATT_SHOW_IGNORED, String.valueOf(SHOW_IGNORED_MODE));
  }

  public void selectFile(final VirtualFile vFile) {
    if (vFile == null) return;
    Object objectToFind;
    Change change = ChangeListManager.getInstance(myProject).getChange(vFile);
    if (change != null) {
      objectToFind = change;
    }
    else {
      objectToFind = vFile;
    }

    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myView.getModel().getRoot();
    DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, objectToFind);
    if (node != null) {
      TreeUtil.selectNode(myView, node);
    }
  }

  private class MyChangeListListener extends ChangeListAdapter {

    public void changeListAdded(ChangeList list) {
      scheduleRefresh();
    }

    public void changeListRemoved(ChangeList list) {
      scheduleRefresh();
    }

    public void changeListRenamed(ChangeList list, String oldName) {
      scheduleRefresh();
    }

    public void changesMoved(Collection<Change> changes, ChangeList fromList, ChangeList toList) {
      scheduleRefresh();
    }

    public void defaultListChanged(ChangeList newDefaultList) {
      scheduleRefresh();
    }

    public void changeListUpdateDone() {
      scheduleRefresh();
      ChangeListManagerImpl changeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
      VcsException updateException = changeListManager.getUpdateException();
      if (updateException == null) {
        updateProgressText("", false);
      }
      else {
        updateProgressText(VcsBundle.message("error.updating.changes", updateException.getMessage()), true);
      }
    }
  }

  private class Expander implements TreeExpander {
    public void expandAll() {
      TreeUtil.expandAll(myView);
    }

    public boolean canExpand() {
      return true;
    }

    public void collapseAll() {
      TreeUtil.collapseAll(myView, 2);
      TreeUtil.expand(myView, 1);
    }

    public boolean canCollapse() {
      return true;
    }
  }

  public class ToggleShowFlattenAction extends ToggleAction {
    public ToggleShowFlattenAction() {
      super(VcsBundle.message("changes.action.show.directories.text"),
            VcsBundle.message("changes.action.show.directories.description"),
            Icons.DIRECTORY_CLOSED_ICON);
    }

    public boolean isSelected(AnActionEvent e) {
      return !SHOW_FLATTEN_MODE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      SHOW_FLATTEN_MODE = !state;
      myView.setShowFlatten(SHOW_FLATTEN_MODE);
      refreshView();
    }
  }

  public class ToggleShowIgnoredAction extends ToggleAction {
    public ToggleShowIgnoredAction() {
      super(VcsBundle.message("changes.action.show.ignored.text"),
            VcsBundle.message("changes.action.show.ignored.description"),
            IconLoader.getIcon("/actions/showHiddens.png"));
    }

    public boolean isSelected(AnActionEvent e) {
      return SHOW_IGNORED_MODE;
    }

    public void setSelected(AnActionEvent e, boolean state) {
      SHOW_IGNORED_MODE = state;
      refreshView();
    }
  }

  private class MyProblemListener extends WolfTheProblemSolver.ProblemListener {
    @Override
    public void problemsAppeared(final VirtualFile file) {
      refreshChangesViewNodeAsync(file);
    }

    @Override
    public void problemsDisappeared(VirtualFile file) {
      refreshChangesViewNodeAsync(file);
    }

    private void refreshChangesViewNodeAsync(final VirtualFile file) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          refreshChangesViewNode(file);
        }
      });
    }

    private void refreshChangesViewNode(final VirtualFile file) {
      DefaultMutableTreeNode root = (DefaultMutableTreeNode)myView.getModel().getRoot();
      Object userObject;
      final ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
      if (changeListManager.isUnversioned(file)) {
        userObject = file;
      }
      else {
        userObject = changeListManager.getChange(file);
      }
      if (userObject != null) {
        final DefaultMutableTreeNode node = TreeUtil.findNodeWithObject(root, userObject);
        if (node != null) {
          myView.getModel().nodeChanged(node);
        }
      }
    }

  }
}