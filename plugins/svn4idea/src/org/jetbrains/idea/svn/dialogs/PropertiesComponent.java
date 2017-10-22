// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.IconUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnPropertyKeys;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.properties.PropertyConsumer;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.jetbrains.idea.svn.properties.PropertyValue;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.Map;
import java.util.TreeMap;

import static com.intellij.openapi.keymap.KeymapUtil.getActiveKeymapShortcuts;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class PropertiesComponent extends JPanel {
  public static final String ID = "SVN Properties";
  private JTable myTable;
  private JTextArea myTextArea;
  private boolean myIsFollowSelection;
  private File myFile;
  private SvnVcs myVcs;
  private JSplitPane mySplitPane;
  private final CloseAction myCloseAction = new CloseAction();
  private final RefreshAction myRefreshAction = new RefreshAction();
  private ActionGroup myPopupActionGroup;

  public PropertiesComponent() {
    // register toolwindow and add listener to the selection.
    myIsFollowSelection = true;
    init();
  }

  public void init() {
    setLayout(new BorderLayout());
    myTable = new JBTable();
    myTextArea = new JTextArea(0, 0);
    myTextArea.setEditable(false);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTable);
    mySplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, scrollPane, ScrollPaneFactory.createScrollPane(myTextArea));
    add(mySplitPane, BorderLayout.CENTER);
    add(createToolbar(), BorderLayout.WEST);
    final DefaultTableModel model = new DefaultTableModel(createTableModel(new HashMap<>()), new Object[]{"Name", "Value"}) {
      public boolean isCellEditable(final int row, final int column) {
        return false;
      }
    };
    myTable.setModel(model);
    myTable.setShowVerticalLines(true);
    myTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    myTable.getSelectionModel().addListSelectionListener(e -> {
      int index = myTable.getSelectedRow();
      if (index >= 0) {
        Object value = myTable.getValueAt(index, 1);
        if (value instanceof String) {
          myTextArea.setText(((String) value));
        } else {
          myTextArea.setText("");
        }
      } else {
        myTextArea.setText("");
      }
    });
    myPopupActionGroup = createPopup();
    PopupHandler.installPopupHandler(myTable, myPopupActionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
    PopupHandler.installPopupHandler(scrollPane, myPopupActionGroup, ActionPlaces.UNKNOWN, ActionManager.getInstance());
    myCloseAction.registerCustomShortcutSet(getActiveKeymapShortcuts(IdeActions.ACTION_CLOSE_ACTIVE_TAB), this);
    myRefreshAction.registerCustomShortcutSet(CommonShortcuts.getRerun(), this);
  }

  public void setFile(SvnVcs vcs, File file) {
    final Map<String, String> props = new TreeMap<>();
    boolean firstTime = myFile == null;
    if (file != null) {
      myFile = file;
      myVcs = vcs;
      collectProperties(vcs, file, props);
    }
    DefaultTableModel model = (DefaultTableModel) myTable.getModel();
    model.setDataVector(createTableModel(props), new Object[] {"Name", "Value"});

    myTable.getColumnModel().setColumnSelectionAllowed(false);
    myTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
      protected void setValue(Object value) {
        if (value != null) {
          if (value.toString().indexOf('\r') >= 0) {
            value = value.toString().substring(0, value.toString().indexOf('\r')) + " [...]";
          }
          if (value.toString().indexOf('\n') >= 0) {
            value = value.toString().substring(0, value.toString().indexOf('\n')) + " [...]";
          }
        }
        super.setValue(value);
      }
    });
    if (firstTime) {
      mySplitPane.setDividerLocation(.5);
    }
    if (myTable.getRowCount() > 0) {
      myTable.getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  private static void collectProperties(@NotNull SvnVcs vcs, @NotNull File file, @NotNull final Map<String, String> props) {
    try {
      PropertyConsumer handler = new PropertyConsumer() {
        public void handleProperty(File path, PropertyData property) {
          final PropertyValue value = property.getValue();
          if (value != null) {
            props.put(property.getName(), PropertyValue.toString(property.getValue()));
          }
        }

        public void handleProperty(Url url, PropertyData property) {
        }

        public void handleProperty(long revision, PropertyData property) {
        }
      };
      vcs.getFactory(file).createPropertyClient().list(Target.on(file, Revision.UNDEFINED), Revision.WORKING, Depth.EMPTY,
                                                       handler);
    }
    catch (VcsException e) {
      props.clear();
    }
  }

  private static Object[][] createTableModel(Map<String, String> model) {
    Object[][] result = new Object[model.size()][2];
    int index = 0;
    for (final String name : model.keySet()) {
      String value = model.get(name);
      if (value == null) {
        value = "";
      }
      result[index][0] = name;
      result[index][1] = value;
      index++;
    }
    return result;
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

  private String getSelectedPropertyName() {
    int row = myTable.getSelectedRow();
    if (row < 0) {
      return null;
    }
    return (String) myTable.getValueAt(row, 0);
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

  private static class CloseAction extends AnAction {

    public void update(AnActionEvent e) {
      e.getPresentation().setText("Close");
      e.getPresentation().setDescription("Close this tool window");
      e.getPresentation().setIcon(AllIcons.Actions.Cancel);
    }

    public void actionPerformed(AnActionEvent e) {
      Project p = e.getData(CommonDataKeys.PROJECT);
      ToolWindowManager.getInstance(p).unregisterToolWindow(ID);
    }
  }

  private class RefreshAction extends AnAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText("Refresh");
      e.getPresentation().setDescription("Reload properties");
      e.getPresentation().setIcon(AllIcons.Actions.Refresh);
      e.getPresentation().setEnabled(myFile != null);
    }

    public void actionPerformed(AnActionEvent e) {
      setFile(myVcs, myFile);
      updateFileStatus(false);
    }
  }

  private abstract class BasePropertyAction extends AnAction {

    protected void setProperty(@Nullable String property, @Nullable String value, boolean recursive, boolean force) {
      if (!StringUtil.isEmpty(property)) {
        try {
          myVcs.getFactory(myFile).createPropertyClient()
            .setProperty(myFile, property, PropertyValue.create(value), Depth.allOrEmpty(recursive), force);
        }
        catch (VcsException error) {
          VcsBalloonProblemNotifier
            .showOverChangesView(myVcs.getProject(), "Can not set property: " + error.getMessage(), MessageType.ERROR);
          // show error message.
        }
      }
    }

    protected void updateFileView(boolean recursive) {
      setFile(myVcs, myFile);
      updateFileStatus(recursive);
    }
  }

  private class SetKeywordsAction extends BasePropertyAction {

    public void update(AnActionEvent e) {
      e.getPresentation().setText("Edit Keywords");
      e.getPresentation().setDescription("Manage svn:keywords property");
      e.getPresentation().setIcon(AllIcons.Actions.Properties);
      e.getPresentation().setEnabled(myFile != null && myFile.isFile());
    }

    public void actionPerformed(AnActionEvent e) {
      Project project = e.getProject();
      PropertyValue propValue = null;
      try {
        propValue = myVcs.getFactory(myFile).createPropertyClient()
          .getProperty(Target.on(myFile), SvnPropertyKeys.SVN_KEYWORDS, false, Revision.WORKING);
      }
      catch (VcsException e1) {
        // show erorr message
      }

      SetKeywordsDialog dialog = new SetKeywordsDialog(project, propValue);
      if (dialog.showAndGet()) {
        setProperty(SvnPropertyKeys.SVN_KEYWORDS, dialog.getKeywords(), false, false);
      }
      updateFileView(false);
    }
  }

  private class DeletePropertyAction extends BasePropertyAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText("Delete Property");
      e.getPresentation().setDescription("Delete selected property");
      e.getPresentation().setIcon(AllIcons.General.Remove);
      e.getPresentation().setEnabled(myFile != null && getSelectedPropertyName() != null);
    }

    public void actionPerformed(AnActionEvent e) {
      setProperty(getSelectedPropertyName(), null, false, true);
      updateFileView(false);
    }
  }

  private class AddPropertyAction extends BasePropertyAction {

    public void update(AnActionEvent e) {
      e.getPresentation().setText("Add Property");
      e.getPresentation().setDescription("Add new property");
      e.getPresentation().setIcon(IconUtil.getAddIcon());
      e.getPresentation().setEnabled(myFile != null);
    }

    public void actionPerformed(AnActionEvent e) {
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

  private class EditPropertyAction extends BasePropertyAction {
    public void update(AnActionEvent e) {
      e.getPresentation().setText("Edit Property");
      e.getPresentation().setDescription("Edit selected property value");
      e.getPresentation().setIcon(AllIcons.Actions.EditSource);
      e.getPresentation().setEnabled(myFile != null && getSelectedPropertyName() != null);
    }

    public void actionPerformed(AnActionEvent e) {
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

  private class FollowSelectionAction extends ToggleAction {

    public boolean isSelected(AnActionEvent e) {
      return myIsFollowSelection;
    }
    public void setSelected(AnActionEvent e, boolean state) {
      if (state && !myIsFollowSelection) {
        updateSelection(e);        
      }
      myIsFollowSelection = state;
    }

    public void update(final AnActionEvent e) {
      super.update(e);
      e.getPresentation().setIcon(AllIcons.General.AutoscrollFromSource);
      e.getPresentation().setText("Follow Selection");
      e.getPresentation().setDescription("Follow Selection");
      // change file
      if (myIsFollowSelection) {
        updateSelection(e);
      }
    }

    private void updateSelection(AnActionEvent e) {
      if (myVcs == null) {
        return;
      }
      VirtualFile vf = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
      if (vf != null) {
        File f = virtualToIoFile(vf);
        if (!f.equals(myFile)) {
          setFile(myVcs, f);
          Project p = e.getProject();
          ToolWindowManager.getInstance(p).getToolWindow(ID).setTitle(f.getName());
        }

      }
    }
  }
}
