package com.intellij.psi.impl.file;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Properties;

public class PsiDirectoryImpl extends PsiElementBase implements PsiDirectory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiDirectoryImpl");

  private final PsiManagerImpl myManager;
  private VirtualFile myFile;

  @NonNls private static final String TEMPLATE_NAME_PROPERTY = "NAME";
  private LanguageLevel myLanguageLevel;

  public PsiDirectoryImpl(PsiManagerImpl manager, VirtualFile file) {
    myManager = manager;
    myFile = file;
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myFile;
  }

  public void setVirtualFile(VirtualFile file) {
    myFile = file;
  }

  public boolean isValid() {
    return myFile.isValid();
  }

  @NotNull
  public Language getLanguage() {
    return Language.ANY;
  }

  public PsiManager getManager() {
    return myManager;
  }

  public String getName() {
    return myFile.getName();
  }

  @NotNull
  public PsiElement setName(String name) throws IncorrectOperationException {
    checkSetName(name);

    /*
    final String oldName = myFile.getName();
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
    event.setElement(this);
    event.setPropertyName(PsiTreeChangeEvent.PROP_DIRECTORY_NAME);
    event.setOldValue(oldName);
    myManager.beforePropertyChange(event);
    */

    try {
      myFile.rename(myManager, name);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString());
    }

    /*
    PsiUndoableAction undoableAction = new PsiUndoableAction(){
      public void undo() throws IncorrectOperationException {
        if (!PsiDirectoryImpl.this.isValid()){
          throw new IncorrectOperationException();
        }
        setName(oldName);
      }
    };
    */

    /*
    event = new PsiTreeChangeEventImpl(myManager);
    event.setElement(this);
    event.setPropertyName(PsiTreeChangeEvent.PROP_DIRECTORY_NAME);
    event.setOldValue(oldName);
    event.setNewValue(name);
    event.setUndoableAction(undoableAction);
    myManager.propertyChanged(event);
    */
    return this;
  }

  public void checkSetName(String name) throws IncorrectOperationException {
    //CheckUtil.checkIsIdentifier(name);
    CheckUtil.checkWritable(this);
    VirtualFile parentFile = myFile.getParent();
    if (parentFile == null) {
      throw new IncorrectOperationException("Cannot rename root directory.");
    }
    VirtualFile child = parentFile.findChild(name);
    if (child != null && !child.equals(myFile)) {
      throw new IncorrectOperationException("File " + child.getPresentableUrl() + " already exists.");
    }
  }

  public PsiPackage getPackage() {
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
    String packageName = projectFileIndex.getPackageNameByDirectory(myFile);
    if (packageName == null) return null;
    return myManager.findPackage(packageName);
  }

  public PsiDirectory getParentDirectory() {
    VirtualFile parentFile = myFile.getParent();
    if (parentFile == null) return null;
    return myManager.findDirectory(parentFile);
  }

  @NotNull
  public PsiDirectory[] getSubdirectories() {
    VirtualFile[] files = myFile.getChildren();
    ArrayList<PsiDirectory> dirs = new ArrayList<PsiDirectory>();
    for (VirtualFile file : files) {
      PsiDirectory dir = myManager.findDirectory(file);
      if (dir != null) {
        dirs.add(dir);
      }
    }
    return dirs.toArray(new PsiDirectory[dirs.size()]);
  }

  @NotNull
  public PsiFile[] getFiles() {
    LOG.assertTrue(myFile.isValid());
    VirtualFile[] files = myFile.getChildren();
    ArrayList<PsiFile> psiFiles = new ArrayList<PsiFile>();
    for (VirtualFile file : files) {
      PsiFile psiFile = myManager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }
    return psiFiles.toArray(new PsiFile[psiFiles.size()]);
  }

  public PsiDirectory findSubdirectory(String name) {
    VirtualFile childVFile = myFile.findChild(name);
    if (childVFile == null) return null;
    return myManager.findDirectory(childVFile);
  }

  public PsiFile findFile(String name) {
    VirtualFile childVFile = myFile.findChild(name);
    if (childVFile == null) return null;
    return myManager.findFile(childVFile);
  }

  @NotNull
  public PsiClass[] getClasses() {
    LOG.assertTrue(isValid());

    VirtualFile[] vFiles = myFile.getChildren();
    ArrayList<PsiClass> classes = new ArrayList<PsiClass>();
    for (VirtualFile vFile : vFiles) {
      PsiFile file = myManager.findFile(vFile);
      if (file instanceof PsiJavaFile && !PsiUtil.isInJspFile(file)) {
        PsiClass[] fileClasses = ((PsiJavaFile)file).getClasses();
        for (PsiClass fileClass : fileClasses) {
          classes.add(fileClass);
        }
      }
    }
    return classes.toArray(new PsiClass[classes.size()]);
  }

  public void processChildren(PsiElementProcessor<PsiFileSystemItem> processor) {
    LOG.assertTrue(isValid());

    for (VirtualFile vFile : myFile.getChildren()) {
      if (vFile.isDirectory()) {
        PsiDirectory dir = myManager.findDirectory(vFile);
        if (dir != null) {
          if(!processor.execute(dir)) return;
        }
      }
      else {
        PsiFile file = myManager.findFile(vFile);
        if (file != null) {
          if(!processor.execute(file)) return;
        }
      }
    }
  }

  @NotNull
  public PsiElement[] getChildren() {
    LOG.assertTrue(isValid());

    VirtualFile[] files = myFile.getChildren();
    final ArrayList<PsiElement> children = new ArrayList<PsiElement>(files.length);
    processChildren(new PsiElementProcessor<PsiFileSystemItem>() {
      public boolean execute(final PsiFileSystemItem element) {
        children.add(element);
        return true;
      }
    });

    return children.toArray(PsiElement.EMPTY_ARRAY);
  }

  public PsiElement getParent() {
    return getParentDirectory();
  }

  public PsiFile getContainingFile() {
    return null;
  }

  public TextRange getTextRange() {
    return null;
  }

  public int getStartOffsetInParent() {
    return -1;
  }

  public int getTextLength() {
    return -1;
  }

  public PsiElement findElementAt(int offset) {
    return null;
  }

  public int getTextOffset() {
    return -1;
  }

  public String getText() {
    return ""; // TODO throw new InsupportedOperationException()
  }

  @NotNull
  public char[] textToCharArray() {
    return ArrayUtil.EMPTY_CHAR_ARRAY; // TODO throw new InsupportedOperationException()
  }

  public boolean textMatches(@NotNull CharSequence text) {
    return false;
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  public final boolean isWritable() {
    return myFile.isWritable();
  }

  public boolean isPhysical() {
    return !(myFile.getFileSystem() instanceof DummyFileSystem);
  }

  /**
   * @not_implemented
   */
  public PsiElement copy() {
    LOG.error("not implemented");
    return null;
  }

  @NotNull
  public PsiClass createClass(String name) throws IncorrectOperationException {
    return createSomeClass(name, FileTemplateManager.INTERNAL_CLASS_TEMPLATE_NAME);
  }

  @NotNull
  public PsiClass createClass(String name, String templateName) throws IncorrectOperationException {
    return createSomeClass(name, templateName);
  }

  @NotNull
  public PsiClass createInterface(String name) throws IncorrectOperationException {
    String templateName = FileTemplateManager.INTERNAL_INTERFACE_TEMPLATE_NAME;
    PsiClass someClass = createSomeClass(name, templateName);
    if (!someClass.isInterface()) {
      throw new IncorrectOperationException(getIncorrectTemplateMessage(templateName));
    }
    return someClass;
  }

  @NotNull
  public PsiClass createEnum(String name) throws IncorrectOperationException {
    String templateName = FileTemplateManager.INTERNAL_ENUM_TEMPLATE_NAME;
    PsiClass someClass = createSomeClass(name, templateName);
    if (!someClass.isEnum()) {
      throw new IncorrectOperationException(getIncorrectTemplateMessage(templateName));
    }
    return someClass;
  }

  @NotNull
  public PsiClass createAnnotationType(String name) throws IncorrectOperationException {
    String templateName = FileTemplateManager.INTERNAL_ANNOTATION_TYPE_TEMPLATE_NAME;
    PsiClass someClass = createSomeClass(name, templateName);
    if (!someClass.isAnnotationType()) {
      throw new IncorrectOperationException(getIncorrectTemplateMessage(templateName));
    }
    return someClass;
  }

  private PsiClass createSomeClass(String name, String templateName) throws IncorrectOperationException {
    checkCreateClassOrInterface(name);

    CodeStyleManager styleManager = CodeStyleManager.getInstance(myManager.getProject());

    FileTemplate template = FileTemplateManager.getInstance().getInternalTemplate(templateName);
    boolean adjustCode = template.isAdjust();

    Properties defaultProperties = FileTemplateManager.getInstance().getDefaultProperties();
    Properties properties = new Properties(defaultProperties);
    FileTemplateUtil.setPackageNameAttribute(properties, this);
    properties.setProperty(TEMPLATE_NAME_PROPERTY, name);
    String text;
    try {
      text = template.getText(properties);
    }
    catch (Exception e) {
      throw new RuntimeException("Unable to load template for " + FileTemplateManager.getInstance().internalTemplateToSubject(templateName),
                                 e);
    }

    PsiElementFactory factory = myManager.getElementFactory();
    String ext = StdFileTypes.JAVA.getDefaultExtension();
    final PsiJavaFile file = (PsiJavaFile)factory.createFileFromText(name + "." + ext, text);
    PsiClass[] classes = file.getClasses();
    if (classes.length != 1 || !name.equals(classes[0].getName())) {
      throw new IncorrectOperationException(getIncorrectTemplateMessage(templateName));
    }
    if (adjustCode) {
      styleManager.reformat(file);
    }

    PsiJavaFile newFile = (PsiJavaFile)add(file);
    return newFile.getClasses()[0];
  }

  private static String getIncorrectTemplateMessage(String templateName) {
    return PsiBundle.message("psi.error.incorroect.class.template.message",
                             FileTemplateManager.getInstance().internalTemplateToSubject(templateName), templateName);
  }

  public void checkCreateClass(String name) throws IncorrectOperationException {
    checkCreateClassOrInterface(name);
  }

  public void checkCreateInterface(String name) throws IncorrectOperationException {
    checkCreateClassOrInterface(name);
  }

  /**
   * @not_implemented
   */
  public void checkCreateClassOrInterface(String name) throws IncorrectOperationException {
    CheckUtil.checkIsIdentifier(myManager, name);
    CheckUtil.checkIsIdentifier(myManager, name);

    String fileName = name + "." + StdFileTypes.JAVA.getDefaultExtension();
    checkCreateFile(fileName);
  }

  @NotNull
  public PsiDirectory createSubdirectory(String name) throws IncorrectOperationException {
    checkCreateSubdirectory(name);

    try {
      VirtualFile file = getVirtualFile().createChildDirectory(myManager, name);
      return myManager.findDirectory(file);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString());
    }
  }

  public void checkCreateSubdirectory(String name) throws IncorrectOperationException {
    // TODO : another check?
    //CheckUtil.checkIsIdentifier(name);
    VirtualFile existingFile = getVirtualFile().findChild(name);
    if (existingFile != null) {
      throw new IncorrectOperationException(
        "Cannot create package - file \"" + existingFile.getPresentableUrl() + "\" already exists.");
    }
    CheckUtil.checkWritable(this);
  }

  @NotNull
  public PsiFile createFile(String name) throws IncorrectOperationException {
    checkCreateFile(name);

    try {
      VirtualFile vFile = getVirtualFile().createChildData(myManager, name);
      return myManager.findFile(vFile);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString());
    }
  }

  public void checkCreateFile(String name) throws IncorrectOperationException {
    FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    FileType type = fileTypeManager.getFileTypeByFileName(name);
    /* [dsl] now it is possible to create a Java file outside source path.
        if (type == FileType.JAVA) {
          if (getPackage() == null){
            throw new IncorrectOperationException("Cannot create java-files outside sourcepath");
          }
        }
        else
    */
    if (type == StdFileTypes.CLASS) {
      throw new IncorrectOperationException("Cannot create class-file");
    }

    VirtualFile existingFile = getVirtualFile().findChild(name);
    if (existingFile != null) {
      throw new IncorrectOperationException(
        "Cannot create file - file \"" + existingFile.getPresentableUrl() + "\" already exists.");
    }
    CheckUtil.checkWritable(this);
  }

  public boolean isSourceRoot() {
    if (myFile == null) return false;
    final VirtualFile sourceRoot = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex().getSourceRootForFile(myFile);
    return myFile.equals(sourceRoot);
  }

  public LanguageLevel getLanguageLevel() {
    if (myLanguageLevel == null) {
      myLanguageLevel = getLanguageLevelInner();
    }
    return myLanguageLevel;

  }

  private LanguageLevel getLanguageLevelInner() {
    final VirtualFile virtualFile = getVirtualFile();
    final Project project = getProject();
    final Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(virtualFile);
    if (module != null) {
      return module.getEffectiveLanguageLevel();
    }

    return PsiManager.getInstance(project).getEffectiveLanguageLevel();
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    checkAdd(element);
    if (element instanceof PsiDirectory) {
      LOG.error("not implemented");
      return null;
    }
    else if (element instanceof PsiFile) {
      PsiFile originalFile = (PsiFile)element;

      try {
        VirtualFile newVFile;
        final PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(myManager.getProject());
        if (originalFile instanceof PsiFileImpl) {
          newVFile = myFile.createChildData(myManager, originalFile.getName());
          String text = originalFile.getText();
          final PsiFile psiFile = getManager().findFile(newVFile);
          final Document document = psiFile == null ? null : psiDocumentManager.getDocument(psiFile);
          final FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
          if (document != null) {
            document.setText(text);
            fileDocumentManager.saveDocument(document);
          } else {
            String lineSeparator = fileDocumentManager.getLineSeparator(newVFile, getProject());
            if (!lineSeparator.equals("\n")) {
              text = StringUtil.convertLineSeparators(text, lineSeparator);
            }

            final Writer writer = LoadTextUtil.getWriter(newVFile, myManager, text, -1);
            try {
              writer.write(text);
            }
            finally {
              writer.close();
            }
          }
        }
        else {
          byte[] storedContents = ((PsiBinaryFileImpl)originalFile).getStoredContents();
          if (storedContents != null) {
            newVFile = myFile.createChildData(myManager, originalFile.getName());
            newVFile.setBinaryContent(storedContents);
          }
          else {
            newVFile = VfsUtil.copyFile(null, originalFile.getVirtualFile(), myFile);
          }
        }
        psiDocumentManager.commitAllDocuments();

        PsiFile newFile = myManager.findFile(newVFile);
        if (newFile instanceof PsiFileImpl) {
          ChangeUtil.encodeInformation((TreeElement)SourceTreeToPsiMap.psiElementToTree(newFile));
          PsiUtil.updatePackageStatement(newFile);
          ChangeUtil.decodeInformation((TreeElement)SourceTreeToPsiMap.psiElementToTree(newFile));
        }

        return newFile;
      }
      catch (IOException e) {
        throw new IncorrectOperationException(e.toString(),e);
      }
    }
    else if (element instanceof PsiClass) {
      final String name = ((PsiClass)element).getName();
      if (name != null) {
        final PsiClass newClass = createClass(name);
        return newClass.replace(element);
      } else {
        LOG.error("not implemented");
        return null;
      }
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    if (element instanceof PsiDirectory) {
      String name = ((PsiDirectory)element).getName();
      PsiDirectory[] subpackages = getSubdirectories();
      for (PsiDirectory dir : subpackages) {
        if (dir.getName().equals(name)) {
          throw new IncorrectOperationException(
            "File " + dir.getVirtualFile().getPresentableUrl() + " already exists.");
        }
      }
    }
    else if (element instanceof PsiFile) {
      String name = ((PsiFile)element).getName();
      PsiFile[] files = getFiles();
      for (PsiFile file : files) {
        if (file.getName().equals(name)) {
          throw new IncorrectOperationException(
            "File " + file.getVirtualFile().getPresentableUrl() + " already exists.");
        }
      }
    }
    else if (element instanceof PsiClass) {
      if (element.getParent() instanceof PsiFile) {
        checkCreateClassOrInterface(((PsiClass)element).getName());
      }
      else {
        LOG.error("not implemented");
      }
    }
    else {
      throw new IncorrectOperationException();
    }
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void delete() throws IncorrectOperationException {
    checkDelete();
    //PsiDirectory parent = getParentDirectory();

    /*
    PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
    event.setParent(parent);
    event.setChild(this);
    myManager.beforeChildRemoval(event);
    */

    try {
      myFile.delete(myManager);
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString(),e);
    }

    /*
    //TODO : allow undo
    PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
    treeEvent.setParent(parent);
    treeEvent.setChild(this);
    treeEvent.setUndoableAction(null);
    myManager.childRemoved(treeEvent);
    */
  }

  public void checkDelete() throws IncorrectOperationException {
    CheckUtil.checkDelete(myFile);
  }

  /**
   * @not_implemented
   */
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    LOG.error("not implemented");
    return null;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitDirectory(this);
  }

  public String toString() {
    return "PsiDirectory:" + myFile.getPresentableUrl();
  }

  public ASTNode getNode() {
    return null;
  }

  public boolean canNavigate() {
    return EditSourceUtil.canNavigate(this);
  }

  public boolean canNavigateToSource() {
    return false;
  }

  public void navigate(boolean requestFocus) {
    final ProjectView projectView = ProjectView.getInstance(getProject());
    projectView.changeView(ProjectViewPane.ID);
    projectView.getProjectViewPaneById(ProjectViewPane.ID).select(this, getVirtualFile(), requestFocus);
    ToolWindowManager.getInstance(getProject()).getToolWindow(ToolWindowId.PROJECT_VIEW).activate(null);
  }
}

