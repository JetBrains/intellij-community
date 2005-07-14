package com.intellij.ide.util;

import com.intellij.ide.projectView.BaseProjectTreeBuilder;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.AbstractProjectTreeStructure;
import com.intellij.ide.projectView.impl.ProjectAbstractTreeStructureBase;
import com.intellij.ide.projectView.impl.ProjectTreeBuilder;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.ChooseByNamePanel;
import com.intellij.ide.util.gotoByName.ChooseByNamePopupComponent;
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.ui.Tree;
import gnu.trove.THashSet;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class TreeFileChooserDialog extends DialogWrapper implements TreeFileChooser {
  private Tree myTree;
  private PsiFile mySelectedFile = null;
  private final Project myProject;
  private BaseProjectTreeBuilder myBuilder;
  private TabbedPaneWrapper myTabbedPane;
  private ChooseByNamePanel myGotoByNamePanel;
  private final PsiFile myInitialFile;
  private final PsiFileFilter myFilter;
  private final FileType myFileType;

  public TreeFileChooserDialog(final Project project,
                               String title, final PsiFile initialFile, FileType fileType, PsiFileFilter filter) {
    super(project, true);
    myInitialFile = initialFile;
    myFilter = filter;
    myFileType = fileType;
    setTitle(title);
    myProject = project;
    init();
    if (initialFile != null) {
      // dialog does not exist yet
      SwingUtilities.invokeLater(new Runnable(){
        public void run() {
          selectFile(initialFile);
        }
      });
    }

    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        handleSelectionChanged();
      }
    });
  }

  protected JComponent createCenterPanel() {
    final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    myTree = new Tree(model);

    final ProjectAbstractTreeStructureBase treeStructure = new AbstractProjectTreeStructure(myProject) {
      public boolean isFlattenPackages() {
        return false;
      }

      public boolean isShowMembers() {
        return false;
      }

      public boolean isHideEmptyMiddlePackages() {
        return true;
      }

      public Object[] getChildElements(final Object element) {
        return filterFiles(super.getChildElements(element));
      }

      public boolean isAbbreviatePackageNames() {
        return false;
      }

      public boolean isShowLibraryContents() {
        return false;
      }

      public boolean isShowModules() {
        return false;
      }
    };
    myBuilder = new ProjectTreeBuilder(myProject, myTree, model, AlphaComparator.INSTANCE, treeStructure);

    myTree.setRootVisible(false);
    myTree.expandRow(0);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    myTree.setCellRenderer(new NodeRenderer());
    myTree.putClientProperty("JTree.lineStyle", "Angled");

    final JScrollPane scrollPane = new JScrollPane(myTree);
    scrollPane.setPreferredSize(new Dimension(500, 300));

    myTree.addKeyListener(new KeyAdapter() {
      public void keyPressed(final KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          doOKAction();
        }
      }
    });

    myTree.addMouseListener(new MouseAdapter() {
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          final TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
          if (path != null && myTree.isPathSelected(path)) {
            doOKAction();
          }
        }
      }
    });

    myTree.addTreeSelectionListener(
      new TreeSelectionListener() {
        public void valueChanged(final TreeSelectionEvent e) {
          handleSelectionChanged();
        }
      }
    );

    new TreeSpeedSearch(myTree);

    myTabbedPane = new TabbedPaneWrapper();

    final JPanel dummyPanel = new JPanel(new BorderLayout());
    String name = null;
    if (myInitialFile != null) {
      name = myInitialFile.getName();
    }
    myGotoByNamePanel = new ChooseByNamePanel(myProject, new MyGotoFileModel(), name, true) {
      protected void close(final boolean isOk) {
        super.close(isOk);

        if (isOk) {
          doOKAction();
        }
        else {
          doCancelAction();
        }
      }

      protected void initUI(final ChooseByNamePopupComponent.Callback callback, final ModalityState modalityState, boolean allowMultipleSelection) {
        super.initUI(callback, modalityState, allowMultipleSelection);
        dummyPanel.add(myGotoByNamePanel.getPanel(), BorderLayout.CENTER);
        //IdeFocusTraversalPolicy.getPreferredFocusedComponent(myGotoByNamePanel.getPanel()).requestFocus();
      }

      protected void choosenElementMightChange() {
        handleSelectionChanged();
      }
    };

    myTabbedPane.addTab("Project", scrollPane);
    myTabbedPane.addTab("Search by Name", dummyPanel);

    SwingUtilities.invokeLater(new Runnable(){
      public void run() {
        myGotoByNamePanel.invoke(new MyCallback(), ModalityState.stateForComponent(getRootPane()), false);
      }
    });

    myTabbedPane.addChangeListener(
      new ChangeListener() {
        public void stateChanged(final ChangeEvent e) {
          handleSelectionChanged();
        }
      }
    );

    return myTabbedPane.getComponent();
  }

  private void handleSelectionChanged(){
    final PsiFile selection = calcSelectedClass();
    setOKActionEnabled(selection != null);
  }

  protected void doOKAction() {
    mySelectedFile = calcSelectedClass();
    if (mySelectedFile == null) return;
    super.doOKAction();
  }

  public void doCancelAction() {
    mySelectedFile = null;
    super.doCancelAction();
  }

  public PsiFile getSelectedFile(){
    return mySelectedFile;
  }

  public void selectFile(final PsiFile file) {
    if (file == null) {
      throw new IllegalArgumentException();
    }
    // Select element in the tree
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        if (myBuilder != null) {
          myBuilder.select(file, file.getVirtualFile(), true);
        }
      }
    }, ModalityState.stateForComponent(getWindow()));
  }

  public void showDialog() {
    show();
  }

  private PsiFile calcSelectedClass() {
    if (myTabbedPane.getSelectedIndex() == 1) {
      return (PsiFile)myGotoByNamePanel.getChosenElement();
    }
    else {
      final TreePath path = myTree.getSelectionPath();
      if (path == null) return null;
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      final Object userObject = node.getUserObject();
      if (!(userObject instanceof PsiFileNode)) return null;
      final PsiFileNode descriptor = (PsiFileNode)userObject;
      return descriptor.getValue();
    }
  }


  protected void dispose() {
    if (myBuilder != null) {
      myBuilder.dispose();
      myBuilder = null;
    }
    super.dispose();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.ide.util.TreeFileChooserDialog";
  }

  public JComponent getPreferredFocusedComponent() {
    return myTree;
  }

  private final class MyGotoFileModel implements ChooseByNameModel {
    private int myMaxSize = WindowManagerEx.getInstanceEx().getFrame(myProject).getSize().width;
    public Object[] getElementsByName(final String name, final boolean checkBoxState) {
      final PsiFile[] psiFiles = PsiManager.getInstance(myProject).getShortNamesCache().getFilesByName(name);
      return filterFiles(psiFiles);
    }

    public String getPromptText() {
      return "Enter file name:";
    }

    public String getCheckBoxName() {
      return null;
    }

    public char getCheckBoxMnemonic() {
      return 0;
    }

    public String getNotInMessage() {
      return "";
    }

    public String getNotFoundMessage() {
      return "";
    }

    public boolean loadInitialCheckBoxState() {
      return true;
    }

    public void saveInitialCheckBoxState(final boolean state) {
    }

    public PsiElementListCellRenderer getListCellRenderer() {
      return new GotoFileCellRenderer(myMaxSize);
    }

    public String[] getNames(final boolean checkBoxState) {
      final String[] fileNames = PsiManager.getInstance(myProject).getShortNamesCache().getAllFileNames();

      final Set<String> array = new THashSet<String>();
      FileTypeManager fileTypeManager = FileTypeManager.getInstance();
      for (String fileName : fileNames) {

        if (myFileType != null && fileTypeManager.getFileTypeByFileName(fileName) != myFileType) {
          continue;
        }

        if (!array.contains(fileName)) {
          array.add(fileName);
        }
      }

      final String[] result = array.toArray(new String[array.size()]);
      Arrays.sort(result);
      return result;
    }

    public String getElementName(final Object element) {
      if (!(element instanceof PsiFile)) return null;
      return ((PsiFile)element).getName();
    }

  }

  private final class MyCallback extends ChooseByNamePopupComponent.Callback {
    public void elementChosen(final Object element) {
      mySelectedFile = (PsiFile)element;
      close(OK_EXIT_CODE);
    }
  }

  private Object[] filterFiles(final Object[] list) {
    final List<Object> result = new ArrayList<Object>(list.length);
    for (Object o : list) {
      final PsiFile psiFile;
      if (o instanceof PsiFile) {
        psiFile = (PsiFile)o;
      }
      else if (o instanceof PsiFileNode) {
        psiFile = ((PsiFileNode)o).getValue();
      }
      else {
        psiFile = null;
      }
      if (psiFile != null) {
        if (myFilter != null && !myFilter.accept(psiFile)) {
          continue;
        }
        if (myFileType != null && psiFile.getFileType() != myFileType) {
          continue;
        }
      }
      else {
        if (o instanceof ProjectViewNode && ((ProjectViewNode)o).getChildren().size() == 0) {
          continue;
        }
      }
      result.add(o);
    }
    return result.toArray(new Object[result.size()]);
  }
}
