package com.intellij.psi.impl.file;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Properties;

public class PsiDirectoryImpl extends PsiElementBase implements PsiDirectory {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiDirectoryImpl");

  private final PsiManagerImpl myManager;
  private VirtualFile myFile;

  public PsiDirectoryImpl(PsiManagerImpl manager, VirtualFile file) {
    myManager = manager;
    myFile = file;
  }

  public VirtualFile getVirtualFile() {
    return myFile;
  }

  public void setVirtualFile(VirtualFile file) {
    myFile = file;
  }

  public boolean isValid() {
    return myFile.isValid();
  }

  public PsiManager getManager() {
    return myManager;
  }

  public String getName() {
    return myFile.getName();
  }

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

  public PsiDirectory[] getSubdirectories() {
    VirtualFile[] files = myFile.getChildren();
    ArrayList<PsiDirectory> dirs = new ArrayList<PsiDirectory>();
    for (int i = 0; i < files.length; i++) {
      PsiDirectory dir = myManager.findDirectory(files[i]);
      if (dir != null) {
        dirs.add(dir);
      }
    }
    return dirs.toArray(new PsiDirectory[dirs.size()]);
  }

  public PsiFile[] getFiles() {
    LOG.assertTrue(myFile.isValid());
    VirtualFile[] files = myFile.getChildren();
    ArrayList<PsiFile> psiFiles = new ArrayList<PsiFile>();
    for (int i = 0; i < files.length; i++) {
      PsiFile psiFile = myManager.findFile(files[i]);
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

  public PsiClass[] getClasses() {
    LOG.assertTrue(isValid());

    VirtualFile[] vFiles = myFile.getChildren();
    ArrayList<PsiClass> classes = new ArrayList<PsiClass>();
    for (int i = 0; i < vFiles.length; i++) {
      PsiFile file = myManager.findFile(vFiles[i]);
      if (file instanceof PsiJavaFile) {
        PsiClass[] fileClasses = ((PsiJavaFile)file).getClasses();
        for (int j = 0; j < fileClasses.length; j++) {
          classes.add(fileClasses[j]);
        }
      }
    }
    return classes.toArray(new PsiClass[classes.size()]);
  }

  public PsiElement[] getChildren() {
    LOG.assertTrue(isValid());

    VirtualFile[] files = myFile.getChildren();
    ArrayList<PsiElement> children = new ArrayList<PsiElement>();
    for (int i = 0; i < files.length; i++) {
      VirtualFile vFile = files[i];
      if (vFile.isDirectory()) {
        PsiDirectory dir = myManager.findDirectory(vFile);
        if (dir != null) {
          children.add(dir);
        }
      }
      else {
        PsiFile file = myManager.findFile(vFile);
        if (file != null) {
          children.add(file);
        }
      }
    }
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
    return null;
  }

  public char[] textToCharArray() {
    return null;
  }

  public boolean textMatches(CharSequence text) {
    return false;
  }

  public boolean textMatches(PsiElement element) {
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

  public PsiClass createClass(String name) throws IncorrectOperationException {
    return createSomeClass(name, FileTemplateManager.INTERNAL_CLASS_TEMPLATE_NAME);
  }

  public PsiClass createInterface(String name) throws IncorrectOperationException {
    String templateName = FileTemplateManager.INTERNAL_INTERFACE_TEMPLATE_NAME;
    PsiClass someClass = createSomeClass(name, templateName);
    if (!someClass.isInterface()) {
      throw new IncorrectOperationException(getIncorrectTemplateMessage(templateName));
    }
    return someClass;
  }

  public PsiClass createEnum(String name) throws IncorrectOperationException {
    String templateName = FileTemplateManager.INTERNAL_ENUM_TEMPLATE_NAME;
    PsiClass someClass = createSomeClass(name, templateName);
    if (!someClass.isEnum()) {
      throw new IncorrectOperationException(getIncorrectTemplateMessage(templateName));
    }
    return someClass;
  }

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
    properties.setProperty("NAME", name);
    String text;
    try {
      text = template.getText(properties);
    }
    catch (Exception e) {
      throw new RuntimeException("Unable to load template for " + FileTemplateManager.getInstance().internalTemplateToSubject(templateName), e);
    }

    PsiElementFactory factory = myManager.getElementFactory();
    String ext = StdFileTypes.JAVA.getDefaultExtension();
    final PsiJavaFile file = (PsiJavaFile)factory.createFileFromText(name + "." + ext, text);
    PsiClass[] classes = file.getClasses();
    if (classes.length != 1 || !classes[0].getName().equals(name)) {
      throw new IncorrectOperationException(getIncorrectTemplateMessage(templateName));
    }
    if (adjustCode) {
      styleManager.reformat(file);
    }

    PsiJavaFile newFile = (PsiJavaFile)add(file);
    return newFile.getClasses()[0];
  }

  private String getIncorrectTemplateMessage(String templateName) {
    String incorrectTemplateMessage = "Cannot create " + FileTemplateManager.getInstance().internalTemplateToSubject(templateName) + " - incorrect " + templateName + " template.";
    return incorrectTemplateMessage;
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

  public PsiFile createFile(String name) throws IncorrectOperationException {
    checkCreateFile(name);

    try {
      VirtualFile vFile = getVirtualFile().createChildData(myManager, name);
      final PsiFile psiFile = myManager.findFile(vFile);
      return psiFile;
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

  public PsiElement add(PsiElement element) throws IncorrectOperationException {
    checkAdd(element);
    if (element instanceof PsiDirectory) {
      LOG.error("not implemented");
      return null;
    }
    else if (element instanceof PsiFile) {
      PsiFile originalFile = (PsiFile)element;

      try {
        VirtualFile newVFile;
        if (originalFile instanceof com.intellij.psi.impl.source.PsiFileImpl) {
          newVFile = myFile.createChildData(myManager, originalFile.getName());
          String lineSeparator = FileDocumentManager.getInstance().getLineSeparator(newVFile, getProject());
          String text = originalFile.getText();
          if (!lineSeparator.equals("\n")) {
            text = StringUtil.convertLineSeparators(text, lineSeparator);
          }
          Writer writer = newVFile.getWriter(myManager); //?
          writer.write(text);
          writer.close();
        }
        else {
          byte[] storedContents = ((PsiBinaryFileImpl)originalFile).getStoredContents();
          if (storedContents != null) {
            newVFile = myFile.createChildData(myManager, originalFile.getName());
            OutputStream out = newVFile.getOutputStream(myManager);
            out.write(storedContents);
            out.close();
          }
          else {
            newVFile = VfsUtil.copyFile(null, originalFile.getVirtualFile(), myFile);
          }
        }
        PsiDocumentManager.getInstance(myManager.getProject()).commitAllDocuments();

        PsiFile newFile = myManager.findFile(newVFile);
        if (newFile instanceof com.intellij.psi.impl.source.PsiFileImpl) {
          ChangeUtil.encodeInformation(SourceTreeToPsiMap.psiElementToTree(newFile));
          PsiUtil.updatePackageStatement(newFile);
          ChangeUtil.decodeInformation(SourceTreeToPsiMap.psiElementToTree(newFile));
        }

        return newFile;
      }
      catch (IOException e) {
        throw new IncorrectOperationException(e.toString());
      }
    }
    else if (element instanceof PsiClass) {
      if (element.getParent() instanceof PsiJavaFile) {
        PsiJavaFile newFile = (PsiJavaFile)add(element.getParent());
        PsiClass[] classes = ((PsiJavaFile)element.getParent()).getClasses();
        PsiClass[] newClasses = newFile.getClasses();
        LOG.assertTrue(classes.length == newClasses.length);
        for (int i = 0; i < classes.length; i++) {
          if (classes[i] == element) return newClasses[i];
        }
        LOG.assertTrue(false);
        return null;
      }
      else {
        LOG.error("not implemented");
        return null;
      }
    }
    else {
      LOG.assertTrue(false);
      return null;
    }
  }

  public void checkAdd(PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
    if (element instanceof PsiDirectory) {
      String name = ((PsiDirectory)element).getName();
      PsiDirectory[] subpackages = getSubdirectories();
      for (int i = 0; i < subpackages.length; i++) {
        PsiDirectory dir = subpackages[i];
        if (dir.getName().equals(name)) {
          throw new IncorrectOperationException(
            "File " + dir.getVirtualFile().getPresentableUrl() + " already exists.");
        }
      }
    }
    else if (element instanceof PsiFile) {
      String name = ((PsiFile)element).getName();
      PsiFile[] files = getFiles();
      for (int i = 0; i < files.length; i++) {
        PsiFile file = files[i];
        if (file.getName().equals(name)) {
          throw new IncorrectOperationException(
            "File " + file.getVirtualFile().getPresentableUrl() + " already exists.");
        }
      }
    }
    else if (element instanceof PsiClass) {
      if (element.getParent() instanceof PsiFile) {
        checkAdd(element.getParent());
      }
      else {
        LOG.error("not implemented");
      }
    }
    else {
      throw new IncorrectOperationException();
    }
  }

  public PsiElement addBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAddBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAddAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
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
      throw new IncorrectOperationException(e.toString());
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
  public PsiElement replace(PsiElement newElement) throws IncorrectOperationException {
    LOG.error("not implemented");
    return null;
  }

  /**
   * @not_implemented
   */
  public void checkReplace(PsiElement newElement) throws IncorrectOperationException {
    LOG.error("not implemented");
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitDirectory(this);
  }

  public String toString() {
    return "PsiDirectory:" + myFile.getPresentableUrl();
  }
}

