// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.properties.PropertyConsumer;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.jetbrains.idea.svn.properties.PropertyValue;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static java.util.Collections.emptyList;
import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnBundle.messagePointer;

public class PropertiesComponent extends JPanel {
  @NonNls public static final @NotNull String ID = "SVN Properties";

  private final @NotNull PropertiesTableView myTable = new PropertiesTableView();
  private JTextArea myTextArea;
  private boolean myIsFollowSelection;
  private File myFile;
  private SvnVcs myVcs;
  private JSplitPane mySplitPane;
  private final CloseAction myCloseAction = new CloseAction();
  private final RefreshAction myRefreshAction = new RefreshAction();

  public PropertiesComponent() {
    // register toolwindow and add listener to the selection.
    myIsFollowSelection = true;
    init();
  }

  private void init() {
    setLayout(new BorderLayout());

    myTextArea = new JTextArea(0, 0);
    myTextArea.setEditable(false);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
    mySplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, scrollPane, ScrollPaneFactory.createScrollPane(myTextArea));
    add(mySplitPane, BorderLayout.CENTER);
    add(createToolbar(), BorderLayout.WEST);
    myTable.setShowVerticalLines(true);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getSelectionModel().addListSelectionListener(e -> {
      PropertyData property = myTable.getSelectedObject();
      myTextArea.setText(property != null ? property.getValue().toString() : "");
    });
    ActionGroup popupActionGroup = createPopup();
    PopupHandler.installPopupHandler(myTable, popupActionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
    PopupHandler.installPopupHandler(scrollPane, popupActionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
    myCloseAction.registerCustomShortcutSet(getActiveKeymapShortcuts(IdeActions.ACTION_CLOSE_ACTIVE_TAB), this);
    myRefreshAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), this);
  }

  public void setFile(SvnVcs vcs, File file) {
    boolean firstTime = myFile == null;

    if (file != null) {
      myFile = file;
      myVcs = vcs;
    }
    myTable.setProperties(file != null ? collectProperties(vcs, file) : emptyList());

    if (firstTime) {
      mySplitPane.setDividerLocation(.5);
    }
    if (myTable.getRowCount() > 0) {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  private static @NotNull List<PropertyData> collectProperties(@NotNull SvnVcs vcs, @NotNull File file) {
    try {
      List<PropertyData> properties = new ArrayList<>();
      PropertyConsumer handler = new PropertyConsumer() {
        @Override
        public void handleProperty(File path, PropertyData property) {
          properties.add(property);
        }
      };

      vcs.getFactory(file).createPropertyClient().list(Target.on(file, Revision.UNDEFINED), Revision.WORKING, Depth.EMPTY, handler);

      return properties;
    }
    catch (VcsException e) {
      return emptyList();
    }
  }

  private JComponent createToolbar() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddPropertyAction());
    group.add(new EditPropertyAction());
    group.add(new DeletePropertyAction());
    group.addSeparator();
    group.add(new SetKeywordsAction());
    group.addSeparator();
    group.add(new FollowSelectionAction());
    group.add(myRefreshAction);
    group.add(myCloseAction);
    return ActionManager.getInstance().createActionToolbar("SvnProperties", group, false).getComponent();
  }

  private DefaultActionGroup createPopup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new AddPropertyAction());
    group.add(new EditPropertyAction());
    group.add(new DeletePropertyAction());
    group.addSeparator();
    group.add(new SetKeywordsAction());
    group.addSeparator();
    group.add(myRefreshAction);
    return group;
  }

  @Nullable
  private String getSelectedPropertyName() {
    PropertyData property = myTable.getSelectedObject();
    return property != null ? property.getName() : null;
  }

  private void updateFileStatus(boolean recursive) {
    if (myFile != null && myVcs != null) {
      String url = "file://" + myFile.getPath().replace(File.separatorChar, '/');
      VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
      if (file != null) {
        VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(myVcs.getProject());

        if (recursive && file.isDirectory()) {
          dirtyScopeManager.dirDirtyRecursively(file);
        } else {
          dirtyScopeManager.fileDirty(file);
        }
      }
    }
  }

  private static final class CloseAction extends DumbAwareAction {
    private CloseAction() {
      super(
        messagePointer("action.Subversion.PropertiesView.Close.text"),
        messagePointer("action.Subversion.PropertiesView.Close.description"),
        AllIcons.Actions.Cancel
      );
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getRequiredData(CommonDataKeys.PROJECT);
      ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID);

      if (toolWindow != null) toolWindow.remove();
    }
  }

  private final class RefreshAction extends DumbAwareAction {
    private RefreshAction() {
      super(
        messagePointer("action.Subversion.PropertiesView.Refresh.text"),
        messagePointer("action.Subversion.PropertiesView.Refresh.description"),
        AllIcons.Actions.Refresh
      );
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myFile != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      setFile(myVcs, myFile);
      updateFileStatus(false);
    }
  }

  private abstract class BasePropertyAction extends DumbAwareAction {
    protected BasePropertyAction(@NotNull Supplier<String> dynamicText, @NotNull Supplier<String> dynamicDescription, @Nullable Icon icon) {
      super(dynamicText, dynamicDescription, icon);
    }

    protected void setProperty(@Nullable String property, @Nullable String value, boolean recursive, boolean force) {
      if (!StringUtil.isEmpty(property)) {
        try {
          myVcs.getFactory(myFile).createPropertyClient()
            .setProperty(myFile, property, PropertyValue.create(value), Depth.allOrEmpty(recursive), force);
        }
        catch (VcsException error) {
          VcsBalloonProblemNotifier
            .showOverChangesView(myVcs.getProject(), message("error.can.not.set.property", error.getMessage()), MessageType.ERROR);
        }
      }
    }

    protected void updateFileView(boolean recursive) {
      setFile(myVcs, myFile);
      updateFileStatus(recursive);
    }
  }

  private final class SetKeywordsAction extends BasePropertyAction {
    private SetKeywordsAction() {
      super(
        messagePointer("action.Subversion.PropertiesView.EditKeywords.text"),
        messagePointer("action.Subversion.PropertiesView.EditKeywords.description"),
        AllIcons.Actions.Properties
      );
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myFile != null && myFile.isFile());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      PropertyValue propValue = null;
      try {
        propValue = myVcs.getFactory(myFile).createPropertyClient()
          .getProperty(Target.on(myFile), SvnPropertyKeys.SVN_KEYWORDS, false, Revision.WORKING);
      }
      catch (VcsException ignored) {
      }

      SetKeywordsDialog dialog = new SetKeywordsDialog(project, propValue);
      if (dialog.showAndGet()) {
        setProperty(SvnPropertyKeys.SVN_KEYWORDS, dialog.getKeywords(), false, false);
      }
      updateFileView(false);
    }
  }

  private final class DeletePropertyAction extends BasePropertyAction {
    private DeletePropertyAction() {
      super(
        messagePointer("action.Subversion.PropertiesView.DeleteProperty.text"),
        messagePointer("action.Subversion.PropertiesView.DeleteProperty.description"),
        AllIcons.General.Remove
      );
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myFile != null && getSelectedPropertyName() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      setProperty(getSelectedPropertyName(), null, false, true);
      updateFileView(false);
    }
  }

  private final class AddPropertyAction extends BasePropertyAction {
    private AddPropertyAction() {
      super(
        messagePointer("action.Subversion.PropertiesView.AddProperty.text"),
        messagePointer("action.Subversion.PropertiesView.AddProperty.description"),
        IconUtil.getAddIcon()
      );
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myFile != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      SetPropertyDialog dialog = new SetPropertyDialog(project, new File[]{myFile}, null,
                                                       myFile.isDirectory());
      boolean recursive = false;
      if (dialog.showAndGet()) {
        recursive = dialog.isRecursive();
        setProperty(dialog.getPropertyName(), dialog.getPropertyValue(), recursive, false);
      }
      updateFileView(recursive);
    }
  }

  private final class EditPropertyAction extends BasePropertyAction {
    private EditPropertyAction() {
      super(
        messagePointer("action.Subversion.PropertiesView.EditProperty.text"),
        messagePointer("action.Subversion.PropertiesView.EditProperty.description"),
        AllIcons.Actions.EditSource
      );
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(myFile != null && getSelectedPropertyName() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      SetPropertyDialog dialog = new SetPropertyDialog(project, new File[]{myFile}, getSelectedPropertyName(), myFile.isDirectory());
      boolean recursive = false;
      if (dialog.showAndGet()) {
        recursive = dialog.isRecursive();
        setProperty(dialog.getPropertyName(), dialog.getPropertyValue(), recursive, false);
      }
      updateFileView(recursive);
    }
  }

  private final class FollowSelectionAction extends DumbAwareToggleAction {
    private FollowSelectionAction() {
      super(
        messagePointer("action.Subversion.PropertiesView.FollowSelection.text"),
        messagePointer("action.Subversion.PropertiesView.FollowSelection.description"),
        AllIcons.General.AutoscrollFromSource
      );
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myIsFollowSelection;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      if (state && !myIsFollowSelection) {
        updateSelection(e);
      }
      myIsFollowSelection = state;
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      super.update(e);
      // change file
      if (myIsFollowSelection) {
        updateSelection(e);
      }
    }

    private void updateSelection(AnActionEvent e) {
      if (myVcs == null) return;

      VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
      if (vf == null) return;

      File f = virtualToIoFile(vf);
      if (!filesEqual(f, myFile)) {
        setFile(myVcs, f);

        ToolWindow toolWindow = ToolWindowManager.getInstance(myVcs.getProject()).getToolWindow(ID);
        if (toolWindow != null) toolWindow.setTitle(f.getName());
      }
    }
  }
}
