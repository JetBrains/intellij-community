package com.intellij.openapi.fileChooser.ex;

import com.intellij.ide.actions.SynchronizeAction;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.actions.FileDeleteAction;
import com.intellij.openapi.fileChooser.actions.GotoHomeAction;
import com.intellij.openapi.fileChooser.actions.GotoProjectDirectory;
import com.intellij.openapi.fileChooser.actions.NewFolderAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class FileChooserDialogImpl extends DialogWrapper implements FileChooserDialog{
  private final FileChooserDescriptor myChooserDescriptor;
  /** @fabrique **/
  protected FileSystemTreeImpl myFileSystemTree;

  private static VirtualFile ourLastFile;
  /** @fabrique **/
  protected Project myProject;
  /** @fabrique **/
  private VirtualFile[] myChosenFiles;

  public FileChooserDialogImpl(FileChooserDescriptor chooserDescriptor, Project project) {
    super(project, true);
    myProject = project;
    myChooserDescriptor = chooserDescriptor;
    setTitle("Select Path");
  }

  public FileChooserDialogImpl(FileChooserDescriptor chooserDescriptor, Component parent) {
    super(parent, true);
    myChooserDescriptor = chooserDescriptor;
    setTitle("Select Path");
  }

  public VirtualFile[] choose(VirtualFile toSelect, Project project) {
    init();

    VirtualFile selectFile = null;

    if (toSelect == null && ourLastFile == null) {
      if (project != null && project.getProjectFile() != null) {
        selectFile = project.getProjectFile().getParent();
      }
    } else {
      selectFile = (toSelect == null) ? ourLastFile : toSelect;
    }

    final VirtualFile file = selectFile;

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (file == null || !file.isValid()) return;
        if (select(file)) return;
        VirtualFile parent = file.getParent();
        if (parent != null) {
          select(parent);
        }
      }
    }, ModalityState.stateForComponent(getContentPane()));
    show();

    return myChosenFiles;
  }

  protected DefaultActionGroup createActionGroup() {
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new GotoHomeAction(myFileSystemTree));
    group.add(new GotoProjectDirectory(myFileSystemTree));
    group.addSeparator();
    group.add(new NewFolderAction(myFileSystemTree));
    group.add(new FileDeleteAction(myFileSystemTree));
    group.addSeparator();
    SynchronizeAction action1 = new SynchronizeAction(false);
    AnAction original = ActionManager.getInstance().getAction(IdeActions.ACTION_SYNCHRONIZE);
    action1.copyFrom(original);
    action1.registerCustomShortcutSet(original.getShortcutSet(), myFileSystemTree.getTree());
    group.add(action1);
    group.addSeparator();
    group.add(new MyShowHiddensAction());
    return group;
  }

  protected final JComponent createTitlePane() {
    return new TitlePanel(myChooserDescriptor.getTitle(), myChooserDescriptor.getDescription());
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new MyPanel();

    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

    createTree();

    final DefaultActionGroup group = createActionGroup();
    ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);
    panel.add(toolBar.getComponent(), BorderLayout.NORTH);


    registerMouseListener(group);

    JScrollPane scrollPane = new JScrollPane(myFileSystemTree.getTree());
    scrollPane.setBorder(BorderFactory.createLineBorder(new Color(148, 154, 156)));
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.setPreferredSize(new Dimension(400, 400));

    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myFileSystemTree.getTree();
  }

  protected final void dispose() {
    myFileSystemTree.dispose();
    super.dispose();
  }

  protected void doOKAction() {
    if (!isOKActionEnabled()) {
      return;
    }

    final VirtualFile[] selectedFiles = getSelectedFiles();
    try {
      myChooserDescriptor.validateSelectedFiles(selectedFiles);
    }
    catch (Exception e) {
      Messages.showErrorDialog(getContentPane(), e.getMessage(), myChooserDescriptor.getTitle());
      return;
    }

    myChosenFiles = selectedFiles;
    if (selectedFiles.length == 0) {
      close(CANCEL_EXIT_CODE);
      return;
    }
    ourLastFile = selectedFiles[selectedFiles.length - 1];

    super.doOKAction();
  }

  public final void doCancelAction() {
    myChosenFiles = VirtualFile.EMPTY_ARRAY;
    super.doCancelAction();
  }

  public final boolean select(final VirtualFile file) {
    return myFileSystemTree.select(file);
  }

  protected JTree createTree() {
    myFileSystemTree = new FileSystemTreeImpl(myProject, myChooserDescriptor);
    myFileSystemTree.addOkAction(new Runnable() {
      public void run() {doOKAction(); }
    });
    JTree tree = myFileSystemTree.getTree();
    tree.setCellRenderer(new NodeRenderer());
    tree.addTreeSelectionListener(new FileTreeSelectionListener());
    setOKActionEnabled(false);
    return tree;
  }

  protected final void registerMouseListener(final ActionGroup group) {
    myFileSystemTree.registerMouseListener(group);
  }

  protected VirtualFile[] getSelectedFiles() {
    return myFileSystemTree.getChoosenFiles();
  }

  private final class FileTreeSelectionListener implements TreeSelectionListener {
    public void valueChanged(TreeSelectionEvent e) {
      TreePath[] paths = e.getPaths();

      boolean enabled = true;
      for (int i = 0; i < paths.length; i++) {
        TreePath treePath = paths[i];
        if (!e.isAddedPath(treePath)) continue;
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        Object userObject = node.getUserObject();
        if (!(userObject instanceof FileNodeDescriptor)) {
          enabled = false;
          break;
        }
        FileElement descriptor = (FileElement) ((FileNodeDescriptor) userObject).getElement();
        VirtualFile file = descriptor.getFile();
        enabled = myChooserDescriptor.isFileSelectable(file);
      }
      setOKActionEnabled(enabled);
    }
  }

  private static final class TitlePanel extends JPanel {
    public TitlePanel(String title, String description) {
      super(new BorderLayout());
      JLabel label = new JLabel(title);
      add(label, BorderLayout.NORTH);
      label.setOpaque(false);
      Font font = label.getFont();
      label.setFont(font.deriveFont(Font.BOLD, font.getSize() + 2));
      if (description != null) {
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        add(descriptionLabel, BorderLayout.CENTER);
      }
      else {
        label.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
      }
    }

    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g;
      int width = getSize().width;
      int height = getSize().height;
      Object oldAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setPaint(new Color(247, 247, 247));
      RoundRectangle2D rect = new RoundRectangle2D.Double(0, 0, width - 1, height - 1, 0, 0);
      g2.fill(rect);
      g2.setPaint(Color.GRAY);
      g2.drawLine(0, height - 1, width - 1, height - 1);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);
    }
  }

  public static abstract class FileChooserAction extends AnAction {
    private final FileSystemTree myFileSystemTree;
    public FileChooserAction(String text, String description, Icon icon, FileSystemTree fileSystemTree) {
      super(text, description, icon);
      myFileSystemTree = fileSystemTree;
    }

    final public void actionPerformed(AnActionEvent e) {
      actionPerformed(myFileSystemTree, e);
    }

    final public void update(AnActionEvent e) {
      update(myFileSystemTree, e);
    }

    protected abstract void update(FileSystemTree fileChooser, AnActionEvent e);

    protected abstract void actionPerformed(FileSystemTree fileChooser, AnActionEvent e);
  }

  /**
   * @author Vladimir Kondratyev
   */
  private final class MyShowHiddensAction extends ToggleAction{
    public MyShowHiddensAction() {
      super("Show Hidden Files and Directories", "Show hidden files and directories", IconLoader.getIcon("/actions/showHiddens.png"));
    }

    public boolean isSelected(AnActionEvent e) {
      return myFileSystemTree.areHiddensShown();
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myFileSystemTree.showHiddens(state);
    }
  }

  private final class MyPanel extends JPanel implements DataContext{
    public MyPanel() {
      super(new BorderLayout());
    }

    public Object getData(String dataId) {
      if (DataConstants.VIRTUAL_FILE_ARRAY.equals(dataId)) {
        return getSelectedFiles();
      }
      return null;
    }
  }

}
