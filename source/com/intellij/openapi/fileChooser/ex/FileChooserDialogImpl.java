package com.intellij.openapi.fileChooser.ex;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.SynchronizeAction;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDialog;
import com.intellij.openapi.fileChooser.FileElement;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.actions.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TitlePanel;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.*;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.concurrency.WorkerThread;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.text.BadLocationException;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.*;
import java.util.List;

public class FileChooserDialogImpl extends DialogWrapper implements FileChooserDialog{
  private final FileChooserDescriptor myChooserDescriptor;
  protected FileSystemTreeImpl myFileSystemTree;

  private static VirtualFile ourLastFile;
  private Project myProject;
  private VirtualFile[] myChosenFiles = VirtualFile.EMPTY_ARRAY;

  private final List<Disposable> myDisposables = new ArrayList<Disposable>();
  private JPanel myNorthPanel;

  private static boolean ourTextFieldShown = false;
  private FileChooserDialogImpl.TextFieldAction myTextFieldAction;

  private JTextField myPathTextField;
  private JComponent myPathTextFieldWrapper;

  private MergingUpdateQueue myTextUpdate;

  private WorkerThread myFileLocator = new WorkerThread("fileChooserFileLocator", 200);

  private boolean myPathIsUpdating;
  private boolean myTreeIsUpdating;

  public static DataKey<FileChooserDialogImpl> KEY = DataKey.create("FileChooserDialog");

  private List<File> myCurrentCompletion;
  private JBPopup myCurrentPopup;
  private JList myList;

  public FileChooserDialogImpl(FileChooserDescriptor chooserDescriptor, Project project) {
    super(project, true);
    myProject = project;
    myChooserDescriptor = chooserDescriptor;
    setTitle(UIBundle.message("file.chooser.default.title"));
  }

  public FileChooserDialogImpl(FileChooserDescriptor chooserDescriptor, Component parent) {
    super(parent, true);
    myChooserDescriptor = chooserDescriptor;
    setTitle(UIBundle.message("file.chooser.default.title"));
  }

  @NotNull
  public VirtualFile[] choose(VirtualFile toSelect, Project project) {
    init();

    VirtualFile selectFile = null;

    if (toSelect == null && ourLastFile == null) {
      if (project != null && project.getBaseDir() != null) {
        selectFile = project.getBaseDir();
      }
    } else {
      selectFile = (toSelect == null) ? ourLastFile : toSelect;
    }

    final VirtualFile file = selectFile;

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        if (file == null || !file.isValid()) {
          return;
        }
        if (select(file)) {
          return;
        }
        VirtualFile parent = file.getParent();
        if (parent != null) {
          select(parent);
        }
      }
    });
    show();

    return myChosenFiles;
  }

  protected DefaultActionGroup createActionGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();

    addToGroup(group, new GotoHomeAction(myFileSystemTree));
    addToGroup(group, new GotoProjectDirectory(myFileSystemTree));
    addToGroup(group, new GotoModuleDirectory(myFileSystemTree, myChooserDescriptor.getContextModule()));

    group.addSeparator();
    if (myChooserDescriptor.getNewFileType() != null) {
      addToGroup(group, new NewFileAction(myFileSystemTree, myChooserDescriptor.getNewFileType(), myChooserDescriptor.getNewFileTemplateText()));
    }
    addToGroup(group, new NewFolderAction(myFileSystemTree));
    addToGroup(group, new FileDeleteAction(myFileSystemTree));
    group.addSeparator();

    final SynchronizeAction syncAction = new SynchronizeAction();
    AnAction original = ActionManager.getInstance().getAction(IdeActions.ACTION_SYNCHRONIZE);
    syncAction.copyFrom(original);
    final JTree tree = myFileSystemTree.getTree();
    syncAction.registerCustomShortcutSet(original.getShortcutSet(), tree);
    group.add(syncAction);
    myDisposables.add(new Disposable() {
      public void dispose() {
        syncAction.unregisterCustomShortcutSet(tree);
      }
    });

    group.addSeparator();
    addToGroup(group, new MyShowHiddensAction());

    return group;
  }

  private void addToGroup(DefaultActionGroup group, AnAction action) {
    group.add(action);
    if (action instanceof Disposable) {
      myDisposables.add(((Disposable)action));
    }
  }

  protected final JComponent createTitlePane() {
    return new TitlePanel(myChooserDescriptor.getTitle(), myChooserDescriptor.getDescription());
  }

  protected JComponent createCenterPanel() {
    JPanel panel = new MyPanel();

    myTextUpdate = new MergingUpdateQueue("FileChooserUpdater", 200, false, panel);
    Disposer.register(myDisposable, myTextUpdate);
    new UiNotifyConnector(panel, myTextUpdate);

    myFileLocator.start();
    Disposer.register(myDisposable, new Disposable() {
      public void dispose() {
        myFileLocator.dispose(true);
      }
    });

    panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

    createTree();

    final DefaultActionGroup group = createActionGroup();
    ActionToolbar toolBar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, true);

    final JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.add(toolBar.getComponent(), BorderLayout.CENTER);

    myTextFieldAction = new TextFieldAction();
    toolbarPanel.add(myTextFieldAction, BorderLayout.EAST);

    myPathTextFieldWrapper = new JPanel(new BorderLayout());
    myPathTextFieldWrapper.setBorder(new EmptyBorder(0, 0, 2, 0));
    myPathTextField = new JTextField();
    myPathTextFieldWrapper.add(myPathTextField, BorderLayout.CENTER);

    myPathTextField.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(final DocumentEvent e) {
        updateTreeFromPath();
      }

      public void removeUpdate(final DocumentEvent e) {
        updateTreeFromPath();
      }

      public void changedUpdate(final DocumentEvent e) {
        updateTreeFromPath();
      }
    });

    myPathTextField.addKeyListener(new KeyAdapter() {
      public void keyPressed(final KeyEvent e) {
        processListNavigation(e);
      }
    });

    myPathTextField.addFocusListener(new FocusAdapter() {
      public void focusLost(final FocusEvent e) {
        closePopup();
      }
    });

    myNorthPanel = new JPanel(new BorderLayout());
    myNorthPanel.add(toolbarPanel, BorderLayout.NORTH);


    updateTextFieldShowing();

    panel.add(myNorthPanel, BorderLayout.NORTH);

    registerMouseListener(group);

    JScrollPane scrollPane = new JScrollPane(myFileSystemTree.getTree());
    scrollPane.setBorder(BorderFactory.createLineBorder(new Color(148, 154, 156)));
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.setPreferredSize(new Dimension(400, 400));


    return panel;
  }


  public JComponent getPreferredFocusedComponent() {
    return ourTextFieldShown ? myPathTextField : myFileSystemTree.getTree();
  }

  public final void dispose() {
    for (Disposable disposable : myDisposables) {
      disposable.dispose();
    }
    myDisposables.clear();
    myFileSystemTree.dispose();
    LocalFileSystem.getInstance().removeWatchedRoots(myRequests.values());
    super.dispose();
  }

  protected void doOKAction() {
    if (!isOKActionEnabled()) {
      return;
    }

    if (ourTextFieldShown) {
      final String text = getTextFieldText();
      if (text == null || !getFileFrom(text).exists()) {
        setErrorText("Specified path cannot be found");
        return;
      }
    }


    final VirtualFile[] selectedFiles = getSelectedFiles();
    try {
      myChooserDescriptor.validateSelectedFiles(selectedFiles);
    }
    catch (Exception e) {
      Messages.showErrorDialog(getContentPane(), e.getMessage(), UIBundle.message("file.chooser.default.title"));
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
    tree.addTreeExpansionListener(new FileTreeExpansionListener());
    setOKActionEnabled(false);

    myFileSystemTree.addListener(new FileSystemTree.Listener() {
      public void selectionChanged(final List<VirtualFile> selection) {
        updatePathFromTree(selection, false);
      }
    }, myDisposable);

    return tree;
  }

  protected final void registerMouseListener(final ActionGroup group) {
    myFileSystemTree.registerMouseListener(group);
  }

  protected VirtualFile[] getSelectedFiles() {
    return myFileSystemTree.getChoosenFiles();
  }

  private final Map<String, LocalFileSystem.WatchRequest> myRequests = new HashMap<String, LocalFileSystem.WatchRequest>();

  private final class FileTreeExpansionListener implements TreeExpansionListener {
    public void treeExpanded(TreeExpansionEvent event) {
      final Object[] path = event.getPath().getPath();
      Set<String> toAdd = new HashSet<String>();

      for (Object o : path) {
        final DefaultMutableTreeNode node = ((DefaultMutableTreeNode)o);
        Object userObject = node.getUserObject();
        if (userObject instanceof FileNodeDescriptor) {
          final VirtualFile file = ((FileNodeDescriptor)userObject).getElement().getFile();
          if (file != null) {
            final String rootPath = file.getPath();
            if (myRequests.get(rootPath) == null) {
              toAdd.add(rootPath);
            }
          }
        }
      }

      if (toAdd.size() > 0) {
        final Set<LocalFileSystem.WatchRequest> requests = LocalFileSystem.getInstance().addRootsToWatch(toAdd, false);
        for (LocalFileSystem.WatchRequest request : requests) {
          myRequests.put(request.getRootPath(), request);
        }
      }
    }

    public void treeCollapsed(TreeExpansionEvent event) {
      //Do not unwatch here!!!
    }
  }

  private final class FileTreeSelectionListener implements TreeSelectionListener {
    public void valueChanged(TreeSelectionEvent e) {
      TreePath[] paths = e.getPaths();

      boolean enabled = true;
      for (TreePath treePath : paths) {
        if (!e.isAddedPath(treePath)) {
          continue;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
        Object userObject = node.getUserObject();
        if (!(userObject instanceof FileNodeDescriptor)) {
          enabled = false;
          break;
        }
        FileElement descriptor = ((FileNodeDescriptor)userObject).getElement();
        VirtualFile file = descriptor.getFile();
        enabled = myChooserDescriptor.isFileSelectable(file);
      }
      setOKActionEnabled(enabled);
    }
  }

  public static abstract class FileChooserAction extends AnAction implements Disposable {
    private final FileSystemTree myFileSystemTree;
    private Disposable myDisposable;

    public FileChooserAction(String text, String description, Icon icon, FileSystemTree fileSystemTree, KeyStroke shortcut) {
      super(text, description, (shortcut == null) ? icon : new LabeledIcon(icon, null, KeyEvent.getKeyText(shortcut.getKeyCode()) + ". "));
      myFileSystemTree = fileSystemTree;
      if (shortcut != null) {
        final JTree tree = fileSystemTree.getTree();
        registerCustomShortcutSet(new CustomShortcutSet(shortcut), tree);
        myDisposable = new Disposable() {
          public void dispose() {
            unregisterCustomShortcutSet(tree);
          }
        };
      }
    }

    public void dispose() {
      if (myDisposable != null) {
        myDisposable.dispose();
        myDisposable = null;
      }
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
      super(UIBundle.message("file.chooser.show.hidden.action.name"), UIBundle.message("file.chooser.show.hidden.action.description"), IconLoader.getIcon("/actions/showHiddens.png"));
    }

    public boolean isSelected(AnActionEvent e) {
      return myFileSystemTree.areHiddensShown();
    }

    public void setSelected(AnActionEvent e, boolean state) {
      myFileSystemTree.showHiddens(state);
    }
  }

  private final class MyPanel extends JPanel implements DataProvider {
    public MyPanel() {
      super(new BorderLayout(0, 0));
    }

    public Object getData(String dataId) {
      if (DataConstants.VIRTUAL_FILE_ARRAY.equals(dataId)) {
        return getSelectedFiles();
      } else if (KEY.getName().equals(dataId)) {
        return FileChooserDialogImpl.this;
      }
      return null;
    }
  }

  public void toggleShowTextField() {
    ourTextFieldShown =! ourTextFieldShown;
    updateTextFieldShowing();
  }

  private void updateTextFieldShowing() {
    myTextFieldAction.update();
    myNorthPanel.remove(myPathTextFieldWrapper);
    if (!myChooserDescriptor.isChooseMultiple()) {
      if (ourTextFieldShown) {
        if (myFileSystemTree.getSelectedFile() != null) {
          final ArrayList<VirtualFile> selection = new ArrayList<VirtualFile>();
          selection.add(myFileSystemTree.getSelectedFile());
          updatePathFromTree(selection, true);
        }
        myNorthPanel.add(myPathTextFieldWrapper, BorderLayout.CENTER);
      } else {
        setErrorText(null);
      }
      myPathTextField.requestFocus();
    } else {
      myFileSystemTree.getTree().requestFocus();
    }

    myNorthPanel.revalidate();
    myNorthPanel.repaint();
  }


  private class TextFieldAction extends LinkLabel implements LinkListener {
    public TextFieldAction() {
      super("", null);
      setListener(this, null);
      update();
    }

    protected void onSetActive(final boolean active) {
      final String tooltip = AnAction
        .createTooltipText(ActionsBundle.message("action.FileChooser.TogglePathShowing.text"), ActionManager.getInstance().getAction("FileChooser.TogglePathShowing"));
      setToolTipText(tooltip);
    }

    protected String getStatusBarText() {
      return ActionsBundle.message("action.FileChooser.TogglePathShowing.text");
    }

    public void update() {
      if (myChooserDescriptor.isChooseMultiple()) {
        setVisible(false);
      } else {
        setVisible(true);
        setText(ourTextFieldShown ? IdeBundle.message("file.chooser.hide.path") : IdeBundle.message("file.chooser.show.path"));
      }
    }

    public void linkSelected(final LinkLabel aSource, final Object aLinkData) {
      toggleShowTextField();
    }
  }

  private void updatePathFromTree(final List<VirtualFile> selection, boolean now) {
    if (!ourTextFieldShown) return;
    if (myTreeIsUpdating) return;

    final String text = selection.size() == 0 ? "" : selection.get(0).getPresentableUrl();
    final Update update = new Update("pathFromTree") {
      public void run() {
        myPathIsUpdating = true;
        myPathTextField.setText(text);
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            myPathTextField.selectAll();
            myPathIsUpdating = false;
            setErrorText(null);
          }
        });
      }
    };
    if (now) {
      update.run();
    } else {
      myTextUpdate.queue(update);
    }
  }

  private void updateTreeFromPath() {
    if (!ourTextFieldShown) return;
    if (myPathIsUpdating) return;

    myTextUpdate.queue(new Update("treeFromPath.1") {
      public void run() {
        final String text = getTextFieldText();
        if (text == null) return;

        myFileLocator.addTaskFirst(new Runnable() {
          public void run() {
            File toFind = getFileFrom(text);

            final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(toFind);
            myTextUpdate.queue(new Update("treeFromPath.2") {
              public void run() {
                selectInTree(vFile, text);
              }
            });

            suggestCompletion();
          }
        });
      }
    });
  }

  private static File getFileFrom(final String text) {
    File toFind = new File(text);
    if (text.length() == 0) {
      final File[] roots = File.listRoots();
      if (roots.length > 0) {
        toFind = roots[0];
      }
    }
    return toFind;
  }

  private void suggestCompletion() {
    final List<File> toComplete = getCompletion(getTextFieldText(), new FileFilter() {
      public boolean accept(final File pathname) {
        final VirtualFile vFile = LocalFileSystem.getInstance().findFileByIoFile(pathname);
        return myChooserDescriptor.isFileVisible(vFile, myFileSystemTree.areHiddensShown());
      }
    });

    myTextUpdate.queue(new Update("completion") {
      public void run() {
        if (myList == null) {
          myList = new JList();
          myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
          myList.setCellRenderer(new ColoredListCellRenderer() {
            protected void customizeCellRenderer(final JList list,
                                                 final Object value, final int index, final boolean selected, final boolean hasFocus) {
              clear();
              append(((File)value).getName(), new SimpleTextAttributes(list.getFont().getStyle(), list.getForeground()));
            }
          });
        }


        if (myCurrentPopup != null) {
          if (toComplete.equals(myCurrentCompletion)) {
            myCurrentPopup.setLocation(getLocationForCaret());
            return;
          } else {
            closePopup();
          }
        }

        myCurrentCompletion = toComplete;

        if (myCurrentCompletion.size() == 0) return;

        final Object selected = myList.getSelectedIndex() < myList.getModel().getSize() ? myList.getSelectedValue() : null;
        myList.setModel(new AbstractListModel() {
          public int getSize() {
            return myCurrentCompletion.size();
          }

          public Object getElementAt(final int index) {
            return myCurrentCompletion.get(index);
          }
        });
        if (selected != null) {
          myList.setSelectedValue(selected, true);
        }
        final PopupChooserBuilder builder = JBPopupFactory.getInstance().createListPopupBuilder(myList);
        myCurrentPopup = builder.setRequestFocus(false).setResizable(false).setCancelCalllback(new Computable<Boolean>() {
          public Boolean compute() {
            SwingUtilities.invokeLater(new Runnable() {
              public void run() {
                myPathTextField.requestFocus();
              }
            });
            return Boolean.TRUE;
          }
        }).createPopup();
        myCurrentPopup.showInScreenCoordinates(myPathTextField, getLocationForCaret());
      }
    });
  }

  private boolean isPopupShowing() {
    return myCurrentPopup != null && myList != null && myList.isShowing();
  }

  private void closePopup() {
    if (myCurrentPopup != null) {
      myCurrentPopup.cancel();
      myCurrentPopup = null;
    }
    myCurrentCompletion = null;
  }

  private void processChosenFromCompletion(final File file) {
    if (file == null) return;
    myPathTextField.setText(file.getAbsolutePath());
  }

  private void processListNavigation(final KeyEvent e) {
    if (togglePopup(e)) return;

    if (!isPopupShowing()) return;

    final Object action = getAction(e, myList);

    if ("selectNextRow".equals(action)) {
      ListScrollingUtil.moveDown(myList, e.getModifiersEx());
    } else if ("selectPreviousRow".equals(action)) {
      ListScrollingUtil.moveUp(myList, e.getModifiersEx());
    } else if ("scrollDown".equals(action)) {
      ListScrollingUtil.movePageDown(myList);
    } else if ("scrollUp".equals(action)) {
      ListScrollingUtil.movePageUp(myList);
    } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
      myCurrentPopup.cancel();
      e.consume();
      processChosenFromCompletion((File)myList.getSelectedValue());
    }
  }

  private boolean togglePopup(KeyEvent e) {
    if (!ourTextFieldShown) return false;

    final KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
    final Object action = ((InputMap)UIManager.get("ComboBox.ancestorInputMap")).get(stroke);
    if ("selectNext".equals(action)) {
      if (!isPopupShowing()) {
        suggestCompletion();
      }
      return true;
    } else if ("selectPrevious".equals(action)) {
      if (isPopupShowing()) {
        closePopup();
      }
      return true;
    } else if ("togglePopup".equals(action)) {
      if (isPopupShowing()) {
        closePopup();
      } else {
        suggestCompletion();
      }
      return true;
    } else {
      if (!isPopupShowing()) {
        final Keymap active = KeymapManager.getInstance().getActiveKeymap();
        final String[] ids = active.getActionIds(stroke);
        if (ids.length > 0 && "CodeCompletion".equals(ids[0])) {
          suggestCompletion();        
        }
      }
    }

    return false;
  }

  private Object getAction(final KeyEvent e, final JComponent comp) {
    final KeyStroke stroke = KeyStroke.getKeyStroke(e.getKeyCode(), e.getModifiers());
    final Object action = comp.getInputMap().get(stroke);
    return action;
  }

  private Point getLocationForCaret() {
    Point point = null;

    try {
      final Rectangle rec = myPathTextField.modelToView(myPathTextField.getCaretPosition());
      point = new Point((int)rec.getMaxX(), (int)rec.getMaxY());
    }
    catch (BadLocationException e) {
      return myPathTextField.getCaret().getMagicCaretPosition();
    }

    SwingUtilities.convertPointToScreen(point, myPathTextField);

    return point;
  }

  static List<File> getCompletion(String typed, final FileFilter filter) {
    List<File> result = new ArrayList<File>();

    File current = getCurrentParent(typed);

    if (current == null) return result;
    if (typed == null || typed.length() == 0) return result;

    final String typedText = new File(typed).getPath();
    final String parentText = current.getAbsolutePath();

    if (!typedText.startsWith(parentText)) return result;

    String prefix = typedText.substring(parentText.length());
    if (prefix.startsWith(File.separator)) {
      prefix = prefix.substring(File.separator.length());
    } else if (typed.endsWith(File.separator)) {
      prefix = "";      
    } 

    final String effectivePrefix = prefix;
    final File[] files = current.listFiles(new FileFilter() {
      public boolean accept(final File pathname) {
        if (filter != null && !filter.accept(pathname)) return false;
        return pathname.getName().toUpperCase().startsWith(effectivePrefix.toUpperCase());
      }
    });

    if (files == null) return result;

    for (File each : files) {
      result.add(each);
    }

    return result;
  }

  private static File getCurrentParent(final String typed) {
    if (typed == null) return null;
    File lastFound = new File(typed);
    if (lastFound.exists()) return lastFound;

    final String[] splits = new File(typed).getAbsolutePath().split(File.separator);
    StringBuffer fullPath = new StringBuffer();
    for (int i = 0; i < splits.length; i++) {
      String each = splits[i];
      fullPath.append(getFileFrom(each).getName());
      if (i < splits.length - 1) {
        fullPath.append(File.separator);
      }
      final File file = getFileFrom(fullPath.toString());
      if (!file.exists()) return lastFound;
      lastFound = file;
    }

    return lastFound;
  }

  private void selectInTree(final VirtualFile vFile, String fromText) {
    if (vFile != null && vFile.isValid()) {
      if (fromText.equalsIgnoreCase(getTextFieldText())) {
        myTreeIsUpdating = true;
        if (!Arrays.asList(myFileSystemTree.getSelectedFiles()).contains(vFile)) {
          myFileSystemTree.select(vFile, new Runnable() {
            public void run() {
              myTreeIsUpdating = false;
              setErrorText(null);
            }
          });
        } else {
          myTreeIsUpdating = false;
          setErrorText(null);
        }
      }
    } else {
      reportFileNotFound();
    }
  }

  private void reportFileNotFound() {
    myTreeIsUpdating = false;
    setErrorText(null);
  }

  private String getTextFieldText() {
    final String text = myPathTextField.getText();
    if (text == null) return null;
    return text.trim();
  }



}
