package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.IconUtilEx;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.projectRoots.ui.Util;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.http.HttpFileSystem;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;
import com.intellij.util.ui.Tree;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 11, 2004
 */
public class LibraryTableEditor {
  static final UrlComparator ourUrlComparator = new UrlComparator();

  private JPanel myPanel;
  private JButton myAddLibraryButton;
  private JButton myRemoveButton;
  private JButton myRenameLibraryButton;
  private JButton myAttachClassesButton;
  private JButton myAttachSourcesButton;
  private JButton myAttachJavadocsButton;
  private JButton myAttachUrlJavadocsButton;
  private JPanel myTreePanel;
  private Tree myTree;
  private Map<Library, LibraryEditor> myLibraryToEditorMap = new HashMap<Library, LibraryEditor>();

  private final LibraryTableModifiableModelProvider myLibraryTable;
  private final boolean myEditingModuleLibraries;
  private LibraryTableTreeBuilder myTreeBuilder;
  private LibraryTable.ModifiableModel myTableModifiableModel;
  private static final Icon INVALID_ITEM_ICON = IconLoader.getIcon("/nodes/ppInvalid.png");

  private LibraryTableEditor(final LibraryTable libraryTable) {
    this(new LibraryTableModifiableModelProvider() {
      public LibraryTable.ModifiableModel getModifiableModel() {
        return libraryTable.getModifiableModel();
      }

      public String getTableLevel() {
        return libraryTable.getTableLevel();
      }
    });
  }

  public static LibraryTableEditor editLibraryTable(LibraryTableModifiableModelProvider provider){
    LibraryTableEditor result = new LibraryTableEditor(provider);
    result.init(new LibraryTableTreeStructure(result));
    return result;
  }

  public static LibraryTableEditor editLibraryTable(LibraryTable libraryTable){
    LibraryTableEditor result = new LibraryTableEditor(libraryTable);
    result.init(new LibraryTableTreeStructure(result));
    return result;
  }

  public static LibraryTableEditor editLibrary(LibraryTableModifiableModelProvider provider, Library library){
    LibraryTableEditor result = new LibraryTableEditor(provider);
    result.init(new LibraryTreeStructure(result, library));
    result.myAddLibraryButton.setVisible(false);
    result.myRenameLibraryButton.setVisible(false);
    return result;
  }

  public static LibraryTableEditor create(final LibraryTable libraryTable){
    LibraryTableEditor result = new LibraryTableEditor(libraryTable);
    result.init(new LibraryTableTreeStructure(result));
    return result;
  }

  private LibraryTableEditor(LibraryTableModifiableModelProvider provider){
    myLibraryTable = provider;
    myTableModifiableModel = myLibraryTable.getModifiableModel();
    final String tableLevel = provider.getTableLevel();
    myEditingModuleLibraries = LibraryTableImplUtil.MODULE_LEVEL.equals(tableLevel);
  }

  private void init(AbstractTreeStructure treeStructure) {
    myTree = new Tree(new DefaultTreeModel(new DefaultMutableTreeNode()));
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    new MyTreeSpeedSearch(myTree);
    myTree.setCellRenderer(new LibraryTreeRenderer());
    final MyTreeSelectionListener treeSelectionListener = new MyTreeSelectionListener();
    myTree.getSelectionModel().addTreeSelectionListener(treeSelectionListener);
    myTreeBuilder = new LibraryTableTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), treeStructure);
    myTreePanel.setLayout(new BorderLayout());
    myTreePanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);

    myAddLibraryButton.setText(myEditingModuleLibraries? "Add Jar/Directory..." : "Create Library...");
    myAddLibraryButton.setMnemonic(myEditingModuleLibraries? 'J' : 'L');
    myAddLibraryButton.addActionListener(new AddLibraryAction());
    myRemoveButton.addActionListener(new RemoveAction());
    myRemoveButton.setMnemonic('R');
    myRenameLibraryButton.setMnemonic('e');
    if (myEditingModuleLibraries) {
      myAttachClassesButton.setVisible(false);
      myRenameLibraryButton.setVisible(false);
    }
    else {
      myRenameLibraryButton.setVisible(true);
      myRenameLibraryButton.addActionListener(new RenameLibraryAction());
      myAttachClassesButton.setVisible(true);
      myAttachClassesButton.setMnemonic('C');
      myAttachClassesButton.addActionListener(new AttachClassesAction());
    }
    myAttachSourcesButton.addActionListener(new AttachSourcesAction());
    myAttachSourcesButton.setMnemonic('S');
    myAttachJavadocsButton.addActionListener(new AttachJavadocAction());
    myAttachJavadocsButton.setMnemonic('J');
    myAttachUrlJavadocsButton.addActionListener(new AttachUrlJavadocAction());
    myAttachUrlJavadocsButton.setMnemonic('U');

    treeSelectionListener.updateButtons();
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public static boolean showEditDialog(final Component parent, LibraryTable libraryTable, final Collection<Library> selection) {
    final LibraryTableEditor libraryTableEditor = LibraryTableEditor.editLibraryTable(libraryTable);
    final MyDialogWrapper dialogWrapper = libraryTableEditor.new MyDialogWrapper(parent);
    if (selection != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          for (Iterator<Library> iterator = selection.iterator(); iterator.hasNext();) {
            final Library library = iterator.next();
            libraryTableEditor.selectLibrary(library, true);
          }
        }
      }, ModalityState.stateForComponent(dialogWrapper.getContentPane()));
    }
    dialogWrapper.show();
    final boolean ok = dialogWrapper.isOK();
    if (selection != null && ok) {
      selection.clear();
      selection.addAll(Arrays.asList(libraryTableEditor.getSelectedLibraries()));
    }
    return ok;
  }

  public void selectLibrary(Library library, boolean expand) {
    LibraryTableTreeContentElement element = new LibraryElement(library, this);
    myTreeBuilder.buildNodeForElement(element);
    DefaultMutableTreeNode node = myTreeBuilder.getNodeForElement(element);
    if (node == null) {
      return;
    }
    myTree.requestFocus();
    final TreePath treePath = new TreePath(node.getPath());
    TreeUtil.selectPath(myTree, treePath);
    if (expand) {
      myTree.expandPath(treePath);
    }
  }

  public void disableAttachButtons() {
    myAttachJavadocsButton.setVisible(false);
    myAttachSourcesButton.setVisible(false);
    myAttachUrlJavadocsButton.setVisible(false);
  }


  public LibraryEditor getLibraryEditor(Library library) {
    LibraryEditor libraryEditor = myLibraryToEditorMap.get(library);
    if (libraryEditor == null) {
      libraryEditor = new LibraryEditor(library);
      myLibraryToEditorMap.put(library, libraryEditor);
    }
    return libraryEditor;
  }

  private void removeLibrary(Library library) {
    myLibraryToEditorMap.remove(library);
    myTableModifiableModel.removeLibrary(library);
  }

  /**
   * Should call this method in order to commit all the changes that were done by the editor
   */
  public void commitChanges() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (Iterator<Library> it = myLibraryToEditorMap.keySet().iterator(); it.hasNext();) {
          Library library = it.next();
          final LibraryEditor libraryEditor = myLibraryToEditorMap.get(library);
          libraryEditor.commit();
        }
        myTableModifiableModel.commit();
      }
    });
    myTableModifiableModel = myLibraryTable.getModifiableModel();
    myLibraryToEditorMap.clear();
  }

  public void cancelChanges() {

    myLibraryToEditorMap.clear();
  }

  public boolean hasChanges() {
    if (myTableModifiableModel.isChanged()) {
      return true;
    }
    for (Iterator<Library> it = myLibraryToEditorMap.keySet().iterator(); it.hasNext();) {
      final LibraryEditor libraryEditor = myLibraryToEditorMap.get(it.next());
      if (libraryEditor.hasChanges()) {
        return true;
      }
    }
    return false;
  }

  public Library[] getLibraries() {
    return myTableModifiableModel.getLibraries();
  }

  private Object getSelectedElement() {
    final TreePath selectionPath = myTreeBuilder.getTree().getSelectionPath();
    return getPathElement(selectionPath);
  }

  private Object[] getSelectedElements() {
    final TreePath[] selectionPaths = myTreeBuilder.getTree().getSelectionPaths();
    if (selectionPaths == null) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    List elements = new ArrayList();
    for (int idx = 0; idx < selectionPaths.length; idx++) {
      TreePath selectionPath = selectionPaths[idx];
      final Object pathElement = getPathElement(selectionPath);
      if (pathElement != null) {
        elements.add(pathElement);
      }
    }
    return elements.toArray(new Object[elements.size()]);
  }

  private Object getPathElement(final TreePath selectionPath) {
    if (selectionPath == null) {
      return null;
    }
    final DefaultMutableTreeNode lastPathComponent = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
    if (lastPathComponent == null) {
      return null;
    }
    final Object userObject = lastPathComponent.getUserObject();
    if (!(userObject instanceof NodeDescriptor))  {
      return null;
    }
    final Object element = ((NodeDescriptor)userObject).getElement();
    if (!(element instanceof LibraryTableTreeContentElement)) {
      return null;
    }
    return element;
  }

  private Library getSelectedLibrary() {
    return convertElementToLibrary(getSelectedElement());
  }

  private Library[] getSelectedLibraries() {
    final List<Library> libs = new ArrayList<Library>();
    final Object[] selectedElements = getSelectedElements();
    for (int idx = 0; idx < selectedElements.length; idx++) {
      final Library library = convertElementToLibrary(selectedElements[idx]);
      if (library != null) {
        libs.add(library);
      }
    }
    return libs.toArray(new Library[libs.size()]);
  }

  private Library convertElementToLibrary(Object selectedElement) {
    LibraryElement libraryElement = null;
    if (selectedElement instanceof LibraryElement) {
      libraryElement = (LibraryElement)selectedElement;
    }
    else if (selectedElement instanceof ItemElement) {
      selectedElement = ((ItemElement)selectedElement).getParent();
    }
    if (selectedElement instanceof ClassesElement) {
      libraryElement = ((ClassesElement)selectedElement).getParent();
    }
    else if (selectedElement instanceof SourcesElement) {
      libraryElement = ((SourcesElement)selectedElement).getParent();
    }
    else if (selectedElement instanceof JavadocElement) {
      libraryElement = ((JavadocElement)selectedElement).getParent();
    }
    return libraryElement != null? libraryElement.getLibrary() : null;
  }

  public void renameLibrary(Library library, String newName) {
    if (library == null) {
      return;
    }
    final LibraryEditor libraryEditor = getLibraryEditor(library);
    if (newName != null) {
      libraryEditor.setName(newName);
    }
    librariesChanged();

  }

  private class AddLibraryAction implements ActionListener {
    private final FileChooserDescriptor myFileChooserDescriptor = new FileChooserDescriptor(false, true, true, false, false, true);

    public AddLibraryAction() {
      myFileChooserDescriptor.setTitle("Choose Library Classes");
      myFileChooserDescriptor.setDescription("Select jars or directories in which library classes can be found");
    }

    public void actionPerformed(ActionEvent e) {
      final VirtualFile[] files;
      final String name;
      if (myEditingModuleLibraries) {
        final Pair<String, VirtualFile[]> pair = new LibraryFileChooser(myFileChooserDescriptor, myPanel, false, LibraryTableEditor.this).chooseNameAndFiles();
        files = filterAlreadyAdded(null, pair.getSecond(), OrderRootType.CLASSES);
        name = null;
      }
      else {
        final Pair<String, VirtualFile[]> pair = new LibraryFileChooser(myFileChooserDescriptor, myPanel, true, LibraryTableEditor.this).chooseNameAndFiles();
        files = pair.getSecond();
        name = pair.getFirst();
      }
      if (files == null || files.length == 0) {
        return;
      }
      final Library[] libraryToSelect = new Library[] {null};
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          if (myEditingModuleLibraries) {
            for (int idx = 0; idx < files.length; idx++) {
              VirtualFile file = files[idx];
              final Library library = myTableModifiableModel.createLibrary(null);
              getLibraryEditor(library).addRoot(file, OrderRootType.CLASSES);
              libraryToSelect[0] = library;
            }
            commitChanges();
          }
          else {
            final Library library = myTableModifiableModel.createLibrary(name);
            final LibraryEditor libraryEditor = getLibraryEditor(library);
            for (int i = 0; i < files.length; i++) {
              libraryEditor.addRoot(files[i], OrderRootType.CLASSES);
            }
            libraryToSelect[0] = library;
          }
        }
      });
      librariesChanged();
      if (libraryToSelect[0] != null) {
        selectLibrary(libraryToSelect[0], false);
      }
    }
  }

  private abstract class AttachItemAction implements ActionListener {
    private final FileChooserDescriptor myDescriptor;

    protected abstract String getTitle();
    protected abstract String getDescription();
    protected abstract OrderRootType getRootType();

    protected AttachItemAction() {
      myDescriptor = createDescriptor();
    }

    protected FileChooserDescriptor createDescriptor() {
      return new FileChooserDescriptor(false, true, true, false, true, true);
    }

    public final void actionPerformed(ActionEvent e) {
      final Library library = getSelectedLibrary();
      if (library != null) {
        myDescriptor.setTitle(getTitle());
        myDescriptor.setTitle(getDescription());
        attachFiles(library, FileChooser.chooseFiles(myPanel, myDescriptor), getRootType());
      }
      myTree.requestFocus();
    }
  }

  private void attachFiles(final Library library, final VirtualFile[] files, final OrderRootType rootType) {
    final VirtualFile[] filesToAttach = filterAlreadyAdded(library, files, rootType);
    if (filesToAttach.length > 0) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final LibraryEditor libraryEditor = getLibraryEditor(library);
          for (int i = 0; i < filesToAttach.length; i++) {
            libraryEditor.addRoot(filesToAttach[i], rootType);
          }
          if (myEditingModuleLibraries) {
            commitChanges();
          }
        }
      });
      myTreeBuilder.updateFromRoot();
    }
  }

  private VirtualFile[] filterAlreadyAdded(Library lib, VirtualFile[] files, final OrderRootType rootType) {
    if (files == null || files.length == 0) {
      return VirtualFile.EMPTY_ARRAY;
    }
    final Set<VirtualFile> chosenFilesSet = new HashSet<VirtualFile>(Arrays.asList(files));
    final Set<VirtualFile> alreadyAdded = new HashSet<VirtualFile>();
    if (lib == null) {
      final Library[] libraries = myTableModifiableModel.getLibraries();
      for (int idx = 0; idx < libraries.length; idx++) {
        final VirtualFile[] libraryFiles = getLibraryEditor(libraries[idx]).getFiles(rootType);
        for (int i = 0; i < libraryFiles.length; i++) {
          alreadyAdded.add(libraryFiles[i]);
        }
      }
    }
    else {
      final VirtualFile[] libraryFiles = getLibraryEditor(lib).getFiles(rootType);
      for (int i = 0; i < libraryFiles.length; i++) {
        alreadyAdded.add(libraryFiles[i]);
      }
    }
    chosenFilesSet.removeAll(alreadyAdded);
    return chosenFilesSet.toArray(new VirtualFile[chosenFilesSet.size()]);
  }

  private class AttachClassesAction extends AttachItemAction {
    protected FileChooserDescriptor createDescriptor() {
      return new FileChooserDescriptor(false, true, true, false, false, true);
    }

    protected String getTitle() {
      final Library selectedLibrary = getSelectedLibrary();
      String title = "Attach Classes";
      if (selectedLibrary != null) {
        title += "to Library \"" + getLibraryEditor(selectedLibrary).getName() + "\"";
      }
      return title;
    }

    protected String getDescription() {
      return "Select jar/zip files or directories in which library classes are located";
    }

    protected OrderRootType getRootType() {
      return OrderRootType.CLASSES;
    }
  }

  private class AttachSourcesAction extends AttachItemAction {
    protected String getTitle() {
      return "Attach Sources";
    }

    protected String getDescription() {
      return "Select jar/zip files or directories in which library sources are located";
    }

    protected OrderRootType getRootType() {
      return OrderRootType.SOURCES;
    }
  }

  private class AttachJavadocAction extends AttachItemAction {
    protected String getTitle() {
      return "Attach Javadoc";
    }

    protected String getDescription() {
      return "Select jar/zip files or directories in which library javadoc documentation is located";
    }

    protected OrderRootType getRootType() {
      return OrderRootType.JAVADOC;
    }
  }

  private class AttachUrlJavadocAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      final Library library = getSelectedLibrary();
      if (library != null) {
        final VirtualFile vFile = Util.showSpecifyJavadocUrlDialog(myPanel);
        if (vFile != null) {
          attachFiles(library, new VirtualFile[] {vFile}, OrderRootType.JAVADOC);
        }
      }
      myTree.requestFocus();
    }
  }

  private class RemoveAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      final Object[] selectedElements = getSelectedElements();
      if (selectedElements.length == 0) {
        return;
      }
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          for (int idx = 0; idx < selectedElements.length; idx++) {
            Object selectedElement = selectedElements[idx];
            if (selectedElement instanceof LibraryElement) {
              // todo: any confirmation on library remove?
              removeLibrary(((LibraryElement)selectedElement).getLibrary());
            }
            else if (selectedElement instanceof ItemElement) {
              final ItemElement itemElement = ((ItemElement)selectedElement);
              final Library library = itemElement.getLibrary();
              getLibraryEditor(library).removeRoot(itemElement.getUrl(), itemElement.getRootType());
            }
          }
          if (myEditingModuleLibraries) {
            commitChanges();
          }
        }
      });
      librariesChanged();
    }

  }

  protected void librariesChanged() {
    myTreeBuilder.updateFromRoot();
    myTree.requestFocus();
  }

  private class RenameLibraryAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      final Library selectedLibrary = getSelectedLibrary();
      if (selectedLibrary == null) {
        return;
      }
      final LibraryEditor libraryEditor = getLibraryEditor(selectedLibrary);
      final String currentName = selectedLibrary.getName();
      final String newName = Messages.showInputDialog(myTree, "Enter new library name", "Rename library \"" + libraryEditor.getName() + "\"", Messages.getQuestionIcon(), libraryEditor.getName(), new InputValidator() {
        public boolean checkInput(String inputString) {
          return true;
        }
        public boolean canClose(String libraryName) {
          if (!currentName.equals(libraryName)) {
            if (libraryAlreadyExists(libraryName)) {
              Messages.showErrorDialog("Library \"" + libraryName + "\" already exists", "Library Already Exists");
              return false;
            }
          }
          return true;
        }
      });
      if (newName != null) {
        libraryEditor.setName(newName);
      }
      librariesChanged();
    }

  }

  boolean libraryAlreadyExists(String libraryName) {
    for (Iterator it = myTableModifiableModel.getLibraryIterator(); it.hasNext(); ) {
      final Library lib = (Library)it.next();
      final LibraryEditor editor = myLibraryToEditorMap.get(lib);
      final String libName = (editor != null)? editor.getName() : lib.getName();
      if (libraryName.equals(libName)) {
        return true;
      }
    }
    return false;
  }

  private class MyDialogWrapper extends DialogWrapper {

    public MyDialogWrapper(final Component parent) {
      super(parent, true);
      String levelName = "";
      final String tableLevel = LibraryTableEditor.this.myLibraryTable.getTableLevel();
      if (tableLevel == LibraryTablesRegistrar.PROJECT_LEVEL) {
        levelName = "Project ";
      }
      else if (tableLevel == LibraryTablesRegistrar.APPLICATION_LEVEL) {
        levelName = "Global ";
      }
      setTitle("Configure " + levelName + "Libraries");
      init();
    }

    protected String getDimensionServiceKey() {
      return "#com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor.MyDialogWrapper";
    }

    public JComponent getPreferredFocusedComponent() {
      return myTree;
    }

    protected void doOKAction() {
      commitChanges();
      super.doOKAction();
    }

    public void doCancelAction() {
      cancelChanges();
      super.doCancelAction();
    }

    protected JComponent createCenterPanel() {
      return LibraryTableEditor.this.getComponent();
    }
  }



  private class MyTreeSelectionListener implements TreeSelectionListener {
    public void valueChanged(TreeSelectionEvent e) {
      updateButtons();
    }

    public void updateButtons() {
      final Object[] selectedElements = getSelectedElements();
      final Class elementsClass = getElementsClass(selectedElements);
      myRemoveButton.setEnabled(
        elementsClass != null &&
        !(elementsClass.isAssignableFrom(ClassesElement.class) || elementsClass.equals(SourcesElement.class) || elementsClass.isAssignableFrom(JavadocElement.class))
      );
      myRenameLibraryButton.setEnabled(selectedElements.length == 1 && elementsClass != null && elementsClass.equals(LibraryElement.class));
      if (elementsClass != null && elementsClass.isAssignableFrom(ItemElement.class)) {
        myRemoveButton.setText("Detach");
        myRemoveButton.setMnemonic('D');
      }
      else {
        myRemoveButton.setText("Remove");
        myRemoveButton.setMnemonic('R');
      }
      boolean attachActionsEnabled = selectedElements.length == 1;
      myAttachClassesButton.setEnabled(attachActionsEnabled);
      myAttachJavadocsButton.setEnabled(attachActionsEnabled);
      myAttachUrlJavadocsButton.setEnabled(attachActionsEnabled);
      myAttachSourcesButton.setEnabled(attachActionsEnabled);
    }

    private Class getElementsClass(Object[] elements) {
      if (elements.length == 0) {
        return null;
      }
      Class cls = null;
      for (int idx = 0; idx < elements.length; idx++) {
        Object element = elements[idx];
        if (cls == null) {
          cls = element.getClass();
        }
        else {
          if (!cls.equals(element.getClass())) {
            return null;
          }
        }
      }
      return cls;
    }
  }



  static Icon getIconForUrl(final String url, final boolean isValid) {
    final Icon icon;
    if (isValid) {
      VirtualFile presentableFile;
      if (isJarFileRoot(url)) {
        presentableFile = LocalFileSystem.getInstance().findFileByPath(getPresentablePath(url));
      }
      else {
        presentableFile = VirtualFileManager.getInstance().findFileByUrl(url);
      }
      if (presentableFile != null && presentableFile.isValid()) {
        if (presentableFile.getFileSystem() instanceof HttpFileSystem) {
          icon = Icons.WEB_ICON;
        }
        else {
          icon = presentableFile.isDirectory()? Icons.DIRECTORY_CLOSED_ICON : IconUtilEx.getIcon(presentableFile, 0, null);
        }
      }
      else {
        icon = INVALID_ITEM_ICON;
      }
    }
    else {
      icon = INVALID_ITEM_ICON;
    }
    return icon;
  }

  static String getPresentablePath(final String url) {
    String presentablePath = VirtualFileManager.extractPath(url);
    if (isJarFileRoot(url)) {
      presentablePath = presentablePath.substring(0, presentablePath.length() - JarFileSystem.JAR_SEPARATOR.length());
    }
    return presentablePath;
  }

  private static boolean isJarFileRoot(final String url) {
    return VirtualFileManager.extractPath(url).endsWith(JarFileSystem.JAR_SEPARATOR);
  }

  private static class MyTreeSpeedSearch extends TreeSpeedSearch {
    public MyTreeSpeedSearch(final Tree tree) {
      super(tree);
    }

    public boolean isMatchingElement(Object element, String pattern) {
      Object userObject = ((DefaultMutableTreeNode)((TreePath)element).getLastPathComponent()).getUserObject();
      if (userObject instanceof ItemElementDescriptor || userObject instanceof LibraryElementDescriptor) {
        String str = getElementText(element);
        if (str == null) {
          return false;
        }
        if (!hasCapitals(pattern)) { // be case-sensitive only if user types capitals
          str = str.toLowerCase();
        }
        if (pattern.indexOf(File.separator) >= 0) {
          return compare(str,pattern);
        }
        final StringTokenizer tokenizer = new StringTokenizer(str, File.separator);
        while (tokenizer.hasMoreTokens()) {
          final String token = tokenizer.nextToken();
          if (compare(token,pattern)) {
            return true;
          }
        }
        return false;
      }
      else {
        return super.isMatchingElement(element, pattern);
      }
    }

    private boolean hasCapitals(String str) {
      for (int idx = 0; idx < str.length(); idx++) {
        if (Character.isUpperCase(str.charAt(idx))) {
          return true;
        }
      }
      return false;
    }
  }
}
