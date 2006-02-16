package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diff.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.ui.ChangeListChooser;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author max
 */
public class ChangeListManagerImpl extends ChangeListManager implements ProjectComponent, ChangeListOwner, JDOMExternalizable {
  private Project myProject;
  private final ProjectLevelVcsManager myVcsManager;
  private static final String TOOLWINDOW_ID = "Changes";

  private Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD);
  private Alarm myRepaintAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private boolean myInitilized = false;
  private boolean myDisposed = false;

  private final List<ChangeList> myChangeLists = new ArrayList<ChangeList>();
  private ChangeList myDefaultChangelist;
  private ChangesListView myView;

  public ChangeListManagerImpl(final Project project, ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myVcsManager = vcsManager;
    myView = new ChangesListView(project);
  }

  public void projectOpened() {
    if (myChangeLists.isEmpty()) {
      final ChangeList list = ChangeList.createEmptyChangeList("Default");
      myChangeLists.add(list);
      setDefaultChangeList(list);
    }

    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          ToolWindowManager.getInstance(myProject).registerToolWindow(TOOLWINDOW_ID, createChangeViewComponent(), ToolWindowAnchor.BOTTOM);
          myInitilized = true;
        }
      });
    }
  }

  public void projectClosed() {
    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      myDisposed = true;
      ToolWindowManager.getInstance(myProject).unregisterToolWindow(TOOLWINDOW_ID);
      myView.dispose();
    }
  }

  @NonNls
  public String getComponentName() {
    return "ChangeListManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  private JComponent createChangeViewComponent() {
    JPanel panel = new JPanel(new BorderLayout());
    DefaultActionGroup group = new DefaultActionGroup();

    RefreshAction refreshAction = new RefreshAction();
    refreshAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), panel);

    AddChangeListAction newChangeListAction = new AddChangeListAction();
    newChangeListAction.registerCustomShortcutSet(CommonShortcuts.getNew(), panel);

    final RemoveChangeListAction removeChangeListAction = new RemoveChangeListAction();
    removeChangeListAction.registerCustomShortcutSet(CommonShortcuts.DELETE, panel);

    final ShowDiffAction diffAction = new ShowDiffAction();
    diffAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D,
                                                                                      SystemInfo.isMac
                                                                                      ? KeyEvent.META_DOWN_MASK
                                                                                      : KeyEvent.CTRL_DOWN_MASK)),
                                         panel);

    final MoveChangesToAnotherListAction toAnotherListAction = new MoveChangesToAnotherListAction();
    toAnotherListAction.registerCustomShortcutSet(CommonShortcuts.getMove(), panel);

    group.add(refreshAction);
    group.add(newChangeListAction);
    group.add(removeChangeListAction);
    group.add(new SetDefaultChangeListAction());
    group.add(toAnotherListAction);
    group.add(diffAction);

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ChangeView", group, false);
    panel.add(toolbar.getComponent(), BorderLayout.WEST);
    panel.add(new JScrollPane(myView), BorderLayout.CENTER);

    myView.installDndSupport(this);
    return panel;
  }

  public void scheduleUpdate() {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(new Runnable() {
      public void run() {
        if (myDisposed) return;
        if (!myInitilized) {
          scheduleUpdate();
          return;
        }

        final List<VcsDirtyScope> scopes = ((VcsDirtyScopeManagerImpl)VcsDirtyScopeManager.getInstance(myProject)).retreiveScopes();
        for (final VcsDirtyScope scope : scopes) {
          final AbstractVcs vcs = myVcsManager.getVcsFor(scope.getScopeRoot());
          if (vcs != null) {
            final ChangeProvider changeProvider = vcs.getChangeProvider();
            if (changeProvider != null) {
              final List<Change> filteredChanges = new ArrayList<Change>();
              changeProvider.getChanges(scope, new ChangelistBuilder() {
                public void processChange(Change change) {
                  if (isUnder(change, scope)) {
                    filteredChanges.add(change);
                  }
                }

                public void processUnversionedFile(VirtualFile file) {
                  // TODO: process unknown files
                }
              }, null); // TODO: make real indicator

              udpateUI(scope, filteredChanges);
            }
          }
        }
      }

      private boolean isUnder(final Change change, final VcsDirtyScope scope) {
        final ContentRevision before = change.getBeforeRevision();
        final ContentRevision after = change.getAfterRevision();
        return before != null && scope.belongsTo(before.getFile()) || after != null && scope.belongsTo(after.getFile());
      }
    }, 300);
  }

  private void udpateUI(VcsDirtyScope scope, final Collection<Change> changes) {
    synchronized (myChangeLists) {
      Set<Change> allChanges = new HashSet<Change>(changes);
      for (ChangeList list : myChangeLists) {
        if (list == myDefaultChangelist) continue;
        list.updateChangesIn(scope, allChanges);
        allChanges.removeAll(list.getChanges());
      }
      myDefaultChangelist.updateChangesIn(scope, allChanges);
      scheduleRefresh();
    }
  }

  private void scheduleRefresh() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myRepaintAlarm.cancelAllRequests();
        myRepaintAlarm.addRequest(new Runnable() {
          public void run() {
            if (myDisposed) return;
            myView.updateModel(getChangeLists());
          }
        }, 100);
      }
    });
  }

  @NotNull
  public List<ChangeList> getChangeLists() {
    synchronized (myChangeLists) {
      return myChangeLists;
    }
  }

  public ChangeList addChangeList(String name) {
    synchronized (myChangeLists) {
      final ChangeList list = ChangeList.createEmptyChangeList(name);
      myChangeLists.add(list);
      scheduleRefresh();
      return list;
    }
  }

  public void removeChangeList(ChangeList list) {
    synchronized (myChangeLists) {
      if (list.isDefault()) throw new RuntimeException(new IncorrectOperationException("Cannot remove default changelist"));

      final Collection<Change> changes = list.getChanges();
      for (Change change : changes) {
        myDefaultChangelist.addChange(change);
      }
      myChangeLists.remove(list);

      scheduleRefresh();
    }
  }

  public void setDefaultChangeList(ChangeList list) {
    synchronized (myChangeLists) {
      if (myDefaultChangelist != null) myDefaultChangelist.setDefault(false);
      list.setDefault(true);
      myDefaultChangelist = list;
      scheduleRefresh();
    }
  }

  public class RefreshAction extends AnAction {
    public RefreshAction() {
      super("Refresh", "Refresh VCS changes", IconLoader.getIcon("/actions/sync.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    }
  }

  public class AddChangeListAction extends AnAction {
    public AddChangeListAction() {
      super("New ChangeList", "Create new changelist", IconLoader.getIcon("/actions/include.png"));
    }

    public void actionPerformed(AnActionEvent e) {
      String rc = Messages.showInputDialog(myProject, "Enter new changelist name", "New ChangeList", Messages.getQuestionIcon());
      if (rc != null) {
        if (rc.length() == 0) {
          rc = getUniqueName();
        }

        addChangeList(rc);
      }
    }

    private String getUniqueName() {
      int unnamedcount = 0;
      for (ChangeList list : getChangeLists()) {
        if (list.getDescription().startsWith("Unnamed")) {
          unnamedcount++;
        }
      }

      return unnamedcount == 0 ? "Unnamed" : "Unnamed (" + unnamedcount + ")";
    }
  }

  public class SetDefaultChangeListAction extends AnAction {
    public SetDefaultChangeListAction() {
      super("Set Default", "Set default changelist", IconLoader.getIcon("/actions/submit1.png"));
    }


    public void update(AnActionEvent e) {
      ChangeList[] lists = (ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS);
      e.getPresentation().setEnabled(lists != null && lists.length == 1 && !lists[0].isDefault());
    }

    public void actionPerformed(AnActionEvent e) {
      setDefaultChangeList(((ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS))[0]);
    }
  }

  public class RemoveChangeListAction extends AnAction {
    public RemoveChangeListAction() {
      super("Remove Changelist", "Remove changelist and move all changes to default", IconLoader.getIcon("/actions/exclude.png"));
    }


    public void update(AnActionEvent e) {
      ChangeList[] lists = (ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS);
      e.getPresentation().setEnabled(lists != null && lists.length == 1 && !lists[0].isDefault());
    }

    public void actionPerformed(AnActionEvent e) {
      final ChangeList list = ((ChangeList[])e.getDataContext().getData(DataConstants.CHANGE_LISTS))[0];
      int rc = list.getChanges().size() == 0 ? DialogWrapper.OK_EXIT_CODE :
               Messages.showYesNoDialog(myProject,
                                        "Are you sure want to remove changelist '" + list.getDescription() + "'?\n" +
                                        "All changes will be moved to default changelist.",
                                        "Remove Change List",
                                        Messages.getQuestionIcon());

      if (rc == DialogWrapper.OK_EXIT_CODE) {
        removeChangeList(list);
      }
    }
  }

  public class MoveChangesToAnotherListAction extends AnAction {
    public MoveChangesToAnotherListAction() {
      super("Move to another list", "Move selected changes to another changelist", IconLoader.getIcon("/actions/fileStatus.png"));
    }

    public void update(AnActionEvent e) {
      Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
      e.getPresentation().setEnabled(changes != null && changes.length > 0);
    }

    public void actionPerformed(AnActionEvent e) {
      Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
      if (changes == null) return;

      ChangeListChooser chooser = new ChangeListChooser(myProject, getChangeLists(), null);
      chooser.show();
      ChangeList resultList = chooser.getSelectedList();
      if (resultList != null) {
        moveChangesTo(resultList, changes);
      }
    }
  }

  public class ShowDiffAction extends AnAction {
    public ShowDiffAction() {
      super("Show Diff", "Show diff for selected change", IconLoader.getIcon("/actions/diff.png"));
    }

    public void update(AnActionEvent e) {
      Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
      e.getPresentation().setEnabled(changes != null && changes.length == 1);
    }

    public void actionPerformed(AnActionEvent e) {
      Change[] changes = (Change[])e.getDataContext().getData(DataConstants.CHANGES);
      if (changes == null) return;

      Change change = changes[0];

      final DiffTool tool = DiffManager.getInstance().getDiffTool();

      final ContentRevision bRev = change.getBeforeRevision();
      final ContentRevision aRev = change.getAfterRevision();

      if (bRev != null && bRev.getFile().getFileType().isBinary() || aRev != null && aRev.getFile().getFileType().isBinary()) {
        return;
      }

      String title = bRev != null ? bRev.getFile().getPath() : aRev != null ? aRev.getFile().getPath() : "Unknown diff";
      final SimpleDiffRequest diffReq = new SimpleDiffRequest(myProject, title);

      diffReq.setContents(createContent(bRev), createContent(aRev));
      diffReq.setContentTitles("Base version", "Your version");
      tool.show(diffReq);
    }

    private DiffContent createContent(ContentRevision revision) {
      if (revision == null) return new SimpleContent("");
      if (revision instanceof CurrentContentRevision) {
        final CurrentContentRevision current = (CurrentContentRevision)revision;
        final VirtualFile vFile = current.getVirtualFile();
        return vFile != null ? new FileContent(myProject, vFile) : new SimpleContent("");
      }

      SimpleContent content = new SimpleContent(revision.getContent(), revision.getFile().getFileType());
      content.setReadOnly(true);
      return content;
    }
  }

  public void moveChangesTo(final ChangeList list, final Change[] changes) {
    for (ChangeList existingList : getChangeLists()) {
      for (Change change : changes) {
        existingList.removeChange(change);
      }
    }

    for (Change change : changes) {
      list.addChange(change);
    }

    scheduleRefresh();
  }


  @SuppressWarnings({"unchecked"})
  public void readExternal(Element element) throws InvalidDataException {
    final List<Element> listNodes = (List<Element>)element.getChildren("list");
    for (Element listNode : listNodes) {
      ChangeList list = addChangeList(listNode.getAttributeValue("name"));
      final List<Element> changeNodes = (List<Element>)listNode.getChildren("change");
      for (Element changeNode : changeNodes) {
        list.addChange(readChange(changeNode));
      }

      if ("true".equals(listNode.getAttributeValue("default"))) {
        setDefaultChangeList(list);
      }
    }

    if (myChangeLists.size() > 0 && myDefaultChangelist == null) {
      setDefaultChangeList(myChangeLists.get(0));
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (ChangeList list : getChangeLists()) {
      Element listNode = new Element("list");
      element.addContent(listNode);
      if (list.isDefault()) {
        listNode.setAttribute("default", "true");
      }

      listNode.setAttribute("name", list.getDescription());
      for (Change change : list.getChanges()) {
        writeChange(listNode, change);
      }
    }
  }

  private void writeChange(final Element listNode, final Change change) {
    Element changeNode = new Element("change");
    listNode.addContent(changeNode);
    changeNode.setAttribute("type", change.getType().name());

    final ContentRevision bRev = change.getBeforeRevision();
    final ContentRevision aRev = change.getAfterRevision();

    changeNode.setAttribute("beforePath", bRev != null ? bRev.getFile().getPath() : "");
    changeNode.setAttribute("afterPath", aRev != null ? aRev.getFile().getPath() : "");
  }

  private Change readChange(Element changeNode) {
    String bRev = changeNode.getAttributeValue("beforePath");
    String aRev = changeNode.getAttributeValue("afterPath");
    return new Change(StringUtil.isEmpty(bRev) ? null : new FakeRevision(bRev), StringUtil.isEmpty(aRev) ? null : new FakeRevision(aRev));
  }

  private static class FakeRevision implements ContentRevision {
    private FilePath myFile;

    public FakeRevision(String path) {
      myFile = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(new File(path));
    }

    @Nullable
    public String getContent() { return null; }

    @NotNull
    public FilePath getFile() {
      return myFile;
    }
  }
}
