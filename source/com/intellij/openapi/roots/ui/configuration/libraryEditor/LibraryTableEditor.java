package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.DataManager;
import com.intellij.ide.IconUtilEx;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.javaee.serverInstances.ApplicationServersManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.ui.Util;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.LibraryTableModifiableModelProvider;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesModifiableModel;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectRootConfigurable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
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
import org.jetbrains.annotations.Nullable;

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
public class LibraryTableEditor implements Disposable {
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

  private LibraryTableModifiableModelProvider myLibraryTable;
  private final boolean myEditingModuleLibraries;
  private LibraryTableTreeBuilder myTreeBuilder;
  private LibraryTable.ModifiableModel myTableModifiableModel;
  private static final Icon INVALID_ITEM_ICON = IconLoader.getIcon("/nodes/ppInvalid.png");

  private final Collection<Runnable> myListeners = new ArrayList<Runnable>();

  @Nullable private Project myProject = null;

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

  public static LibraryTableEditor editLibraryTable(LibraryTableModifiableModelProvider provider, Project project){
    LibraryTableEditor result = new LibraryTableEditor(provider);
    result.myProject = project;
    result.init(new LibraryTableTreeStructure(result));
    return result;
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

  public static LibraryTableEditor editLibrary(final LibraryTableModifiableModelProvider provider,
                                               final Library library,
                                               final Project project) {
    final LibraryTableEditor tableEditor = editLibrary(provider, library);
    tableEditor.myProject = project;
    return tableEditor;
  }

  public static LibraryTableEditor editLibrary(LibraryTableModifiableModelProvider provider, Library library){
    LibraryTableEditor result = new LibraryTableEditor(provider);
    result.init(new LibraryTreeStructure(result, library));
    result.myAddLibraryButton.setVisible(false);
    result.myRenameLibraryButton.setVisible(false);
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

    myAddLibraryButton.setText(myEditingModuleLibraries? ProjectBundle.message("library.add.jar.directory.action") :
                               ProjectBundle.message("library.create.library.action"));
    myAddLibraryButton.addActionListener(new AddLibraryAction());
    myRemoveButton.addActionListener(new RemoveAction());
    if (myEditingModuleLibraries) {
      myAttachClassesButton.setVisible(false);
      myRenameLibraryButton.setVisible(false);
    }
    else {
      myRenameLibraryButton.setVisible(true);
      myRenameLibraryButton.addActionListener(new RenameLibraryAction());
      myAttachClassesButton.setVisible(true);
      myAttachClassesButton.addActionListener(new AttachClassesAction());
    }
    myAttachSourcesButton.addActionListener(new AttachSourcesAction());
    myAttachJavadocsButton.addActionListener(new AttachJavadocAction());
    myAttachUrlJavadocsButton.addActionListener(new AttachUrlJavadocAction());

    treeSelectionListener.updateButtons();
    Disposer.register(this, myTreeBuilder);
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public static boolean showEditDialog(final Component parent, LibraryTable libraryTable, final Collection<Library> selection) {
    final LibraryTableEditor libraryTableEditor = LibraryTableEditor.editLibraryTable(libraryTable);
    final boolean ok = libraryTableEditor.openDialog(parent, selection, true);
    if (selection != null && ok) {
      selection.clear();
      selection.addAll(Arrays.asList(libraryTableEditor.getSelectedLibraries()));
    }
    Disposer.dispose(libraryTableEditor);
    return ok;
  }

  public void selectLibrary(Library library, boolean expand) {
    LibraryTableTreeContentElement element = new LibraryElement(library, this, false);
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
    if (myTableModifiableModel instanceof LibrariesModifiableModel){
      return ((LibrariesModifiableModel)myTableModifiableModel).getLibraryEditor(library);
    }
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
    if (myProject != null){
      ProjectRootConfigurable.getInstance(myProject).fireItemsChangeListener(library);
    }
  }

  /**
   * Should call this method in order to commit all the changes that were done by the editor
   */
  public void commitChanges() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (Library library : myLibraryToEditorMap.keySet()) {
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
    for (final Library library : myLibraryToEditorMap.keySet()) {
      final LibraryEditor libraryEditor = myLibraryToEditorMap.get(library);
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
    List<Object> elements = new ArrayList<Object>();
    for (TreePath selectionPath : selectionPaths) {
      final Object pathElement = getPathElement(selectionPath);
      if (pathElement != null) {
        elements.add(pathElement);
      }
    }
    return elements.toArray(new Object[elements.size()]);
  }

  private static Object getPathElement(final TreePath selectionPath) {
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
    if (myTreeBuilder.getTreeStructure() instanceof LibraryTreeStructure) {
      return ((LibraryTreeStructure)myTreeBuilder.getTreeStructure()).getLibrary();
    } else {
      return convertElementToLibrary(getSelectedElement());
    }
  }

  public Library[] getSelectedLibraries() {
    final List<Library> libs = new ArrayList<Library>();
    final Object[] selectedElements = getSelectedElements();
    for (Object selectedElement : selectedElements) {
      final Library library = convertElementToLibrary(selectedElement);
      if (library != null) {
        libs.add(library);
      }
    }
    return libs.toArray(new Library[libs.size()]);
  }

  private static Library convertElementToLibrary(Object selectedElement) {
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
    librariesChanged(false);

  }

  /**
   * @return true if Ok button was pressed on dialog close, false otherwise
   */
  public boolean openDialog(final Component parent, final Collection<Library> selection, final boolean expandSelectedItems) {
    final MyDialogWrapper dialogWrapper = new MyDialogWrapper(parent);
    if (selection != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          for (final Library library : selection) {
            selectLibrary(library, expandSelectedItems);
          }
        }
      }, ModalityState.stateForComponent(dialogWrapper.getContentPane()));
    }
    dialogWrapper.show();
    return dialogWrapper.isOK();
  }

  public ActionListener createAddLibraryAction(boolean select, JComponent parent){
    return new AddLibraryAction(select, parent);
  }

  @SuppressWarnings({"BoundFieldAssignment"})
  public void dispose() {
  }

  private class AddLibraryAction implements ActionListener {
    private final FileChooserDescriptor myFileChooserDescriptor = new FileChooserDescriptor(false, true, true, false, false, true);
    private boolean myNeedToSelect;
    private JComponent myParent;

    public AddLibraryAction() {
      this(false, myPanel);
    }

    public AddLibraryAction(boolean select, JComponent parent) {
      myNeedToSelect = select;
      myParent = parent;
      myFileChooserDescriptor.setTitle(ProjectBundle.message("library.choose.classes.title"));
      myFileChooserDescriptor.setDescription(ProjectBundle.message("library.choose.classes.description"));
    }

    public void actionPerformed(ActionEvent e) {
      final Module contextModule = (Module)DataManager.getInstance().getDataContext(myAddLibraryButton).getData(DataConstantsEx.MODULE_CONTEXT);
      myFileChooserDescriptor.setContextModule(contextModule);
      final VirtualFile[] files;
      final String name;
      if (myEditingModuleLibraries) {
        final Pair<String, VirtualFile[]> pair = new LibraryFileChooser(myFileChooserDescriptor, myParent, false, LibraryTableEditor.this).chooseNameAndFiles();
        files = filterAlreadyAdded(null, pair.getSecond(), OrderRootType.CLASSES);
        name = null;
      }
      else {
        final Pair<String, VirtualFile[]> pair = new LibraryFileChooser(myFileChooserDescriptor, myParent, true, LibraryTableEditor.this).chooseNameAndFiles();
        files = pair.getSecond();
        name = pair.getFirst();
      }
      if (files == null || files.length == 0) {
        return;
      }
      final Library[] libraryToSelect = new Library[] {null};
      final String[] libraryPresentableName = new String[]{null};
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          if (myEditingModuleLibraries) {
            for (VirtualFile file : files) {
              final Library library = myTableModifiableModel.createLibrary(null);
              getLibraryEditor(library).addRoot(file, OrderRootType.CLASSES);
              libraryToSelect[0] = library;
              libraryPresentableName[0] = file.getUrl();
            }
          }
          else {
            final Library library = myTableModifiableModel.createLibrary(name);
            final LibraryEditor libraryEditor = getLibraryEditor(library);
            for (VirtualFile file : files) {
              libraryEditor.addRoot(file, OrderRootType.CLASSES);
            }
            libraryToSelect[0] = library;
          }
        }
      });
      librariesChanged(true);
      if (libraryToSelect[0] != null) {
        selectLibrary(libraryToSelect[0], false);
        if (myProject != null){
          final ProjectRootConfigurable rootConfigurable = ProjectRootConfigurable.getInstance(myProject);
          final LibraryEditor libraryEditor = getLibraryEditor(libraryToSelect[0]);
          if (libraryEditor.hasChanges()) {
            ApplicationManager.getApplication().runWriteAction(new Runnable(){
              public void run() {
                libraryEditor.commit();  //update lib node
              }
            });
          }
          final DefaultMutableTreeNode libraryNode = rootConfigurable.createLibraryNode(libraryToSelect[0], libraryPresentableName[0]);
          if (myNeedToSelect){
            rootConfigurable.selectNodeInTree(libraryNode);
          }
        }
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
      fireLibrariesChanged();
      myTree.requestFocus();
    }
  }

  private void attachFiles(final Library library, final VirtualFile[] files, final OrderRootType rootType) {
    final VirtualFile[] filesToAttach = filterAlreadyAdded(library, files, rootType);
    if (filesToAttach.length > 0) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          final LibraryEditor libraryEditor = getLibraryEditor(library);
          for (VirtualFile aFilesToAttach : filesToAttach) {
            libraryEditor.addRoot(aFilesToAttach, rootType);
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
      for (Library library : libraries) {
        final VirtualFile[] libraryFiles = getLibraryEditor(library).getFiles(rootType);
        for (VirtualFile libraryFile : libraryFiles) {
          alreadyAdded.add(libraryFile);
        }
      }
    }
    else {
      final VirtualFile[] libraryFiles = getLibraryEditor(lib).getFiles(rootType);
      for (VirtualFile libraryFile : libraryFiles) {
        alreadyAdded.add(libraryFile);
      }
    }
    chosenFilesSet.removeAll(alreadyAdded);
    return chosenFilesSet.toArray(new VirtualFile[chosenFilesSet.size()]);
  }

  private class AttachClassesAction extends AttachItemAction {
    @SuppressWarnings({"RefusedBequest"})
    protected FileChooserDescriptor createDescriptor() {
      return new FileChooserDescriptor(false, true, true, false, false, true);
    }

    protected String getTitle() {
      final Library selectedLibrary = getSelectedLibrary();
      if (selectedLibrary != null) {
        return ProjectBundle.message("library.attach.classes.to.library.action", getLibraryEditor(selectedLibrary).getName());
      }
      else {
        return ProjectBundle.message("library.attach.classes.action");
      }
    }

    protected String getDescription() {
      return ProjectBundle.message("library.attach.classes.description");
    }

    protected OrderRootType getRootType() {
      return OrderRootType.CLASSES;
    }
  }

  private class AttachSourcesAction extends AttachItemAction {
    protected String getTitle() {
      return ProjectBundle.message("library.attach.sources.action");
    }

    protected String getDescription() {
      return ProjectBundle.message("library.attach.sources.description");
    }

    protected OrderRootType getRootType() {
      return OrderRootType.SOURCES;
    }
  }

  private class AttachJavadocAction extends AttachItemAction {
    protected String getTitle() {
      return ProjectBundle.message("library.attach.javadoc.action");
    }

    protected String getDescription() {
      return ProjectBundle.message("library.attach.javadoc.description");
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
          for (Object selectedElement : selectedElements) {
            if (selectedElement instanceof LibraryElement) {
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
      librariesChanged(true);
    }

  }

  protected void librariesChanged(boolean putFocusIntoTree) {
    myTreeBuilder.updateFromRoot();
    if (putFocusIntoTree) {
      myTree.requestFocus();
    }
    fireLibrariesChanged();
  }

  private void fireLibrariesChanged() {
    Runnable[] runnables = myListeners.toArray(new Runnable[myListeners.size()]);
    for (Runnable listener : runnables) {
      listener.run();
    }
  }

  public void addListener(Runnable listener) {
    myListeners.add(listener);
  }

  public void removeListener(Runnable listener) {
    myListeners.remove(listener);
  }

  private class RenameLibraryAction implements ActionListener {
    public void actionPerformed(ActionEvent e) {
      final Library selectedLibrary = getSelectedLibrary();
      if (selectedLibrary == null) {
        return;
      }
      final LibraryEditor libraryEditor = getLibraryEditor(selectedLibrary);
      final String currentName = selectedLibrary.getName();
      final String newName = Messages.showInputDialog(myTree, ProjectBundle.message("library.rename.prompt"),
                                                      ProjectBundle.message("library.rename.title", libraryEditor.getName()), Messages.getQuestionIcon(), libraryEditor.getName(), new InputValidator() {
        public boolean checkInput(String inputString) {
          return true;
        }
        public boolean canClose(String libraryName) {
          if (!currentName.equals(libraryName)) {
            if (libraryAlreadyExists(libraryName)) {
              Messages.showErrorDialog(ProjectBundle.message("library.name.already.exists.error", libraryName),
                                       ProjectBundle.message("library.name.already.exists.title"));
              return false;
            }
          }
          return true;
        }
      });
      if (newName != null) {
        libraryEditor.setName(newName);
      }
      librariesChanged(true);
    }

  }

  boolean libraryAlreadyExists(String libraryName) {
    for (Iterator it = myTableModifiableModel.getLibraryIterator(); it.hasNext(); ) {
      final Library lib = (Library)it.next();
      final LibraryEditor editor = getLibraryEditor(lib);
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
      final String tableLevel = LibraryTableEditor.this.myLibraryTable.getTableLevel();
      if (LibraryTablesRegistrar.PROJECT_LEVEL.equals(tableLevel)) {
        setTitle(ProjectBundle.message("library.configure.project.title"));
      }
      else if (LibraryTablesRegistrar.APPLICATION_LEVEL.equals(tableLevel)) {
        setTitle(ProjectBundle.message("library.configure.global.title"));
      }
      else if (ApplicationServersManager.APPLICATION_SERVER_MODULE_LIBRARIES.equals(tableLevel)) {
        setTitle(ProjectBundle.message("library.configure.appserver.title"));
      }
      init();
    }

    @SuppressWarnings({"RefusedBequest"})
    protected String getDimensionServiceKey() {
      return "#com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryTableEditor.MyDialogWrapper";
    }

    @SuppressWarnings({"RefusedBequest"})
    public JComponent getPreferredFocusedComponent() {
      return myTree;
    }

    protected void doOKAction() {
      commitChanges();
      super.doOKAction();
      ApplicationManager.getApplication().invokeLater(new Runnable(){
        public void run() {
          Disposer.dispose(LibraryTableEditor.this);
        }
      });
    }

    public void doCancelAction() {
      cancelChanges();
      Disposer.dispose(LibraryTableEditor.this);
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
      final Class<? extends Object> elementsClass = getElementsClass(selectedElements);
      myRemoveButton.setEnabled(
        elementsClass != null &&
        !(elementsClass.isAssignableFrom(ClassesElement.class) || elementsClass.equals(SourcesElement.class) || elementsClass.isAssignableFrom(JavadocElement.class))
      );
      myRenameLibraryButton.setEnabled(selectedElements.length == 1 && elementsClass != null && elementsClass.equals(LibraryElement.class));
      if (elementsClass != null && elementsClass.isAssignableFrom(ItemElement.class)) {
        myRemoveButton.setText(ProjectBundle.message("library.detach.action"));
      }
      else {
        myRemoveButton.setText(ProjectBundle.message("library.remove.action"));
      }
      boolean attachActionsEnabled = selectedElements.length == 1 || getSelectedLibrary() != null;
      myAttachClassesButton.setEnabled(attachActionsEnabled);
      myAttachJavadocsButton.setEnabled(attachActionsEnabled);
      myAttachUrlJavadocsButton.setEnabled(attachActionsEnabled);
      myAttachSourcesButton.setEnabled(attachActionsEnabled);
    }

    private Class<? extends Object> getElementsClass(Object[] elements) {
      if (elements.length == 0) {
        return null;
      }
      Class<? extends Object> cls = null;
      for (Object element : elements) {
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

    private static boolean hasCapitals(String str) {
      for (int idx = 0; idx < str.length(); idx++) {
        if (Character.isUpperCase(str.charAt(idx))) {
          return true;
        }
      }
      return false;
    }
  }
}
