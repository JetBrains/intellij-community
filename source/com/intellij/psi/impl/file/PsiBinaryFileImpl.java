package com.intellij.psi.impl.file;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.IncorrectOperationException;

import java.io.IOException;
import java.io.OutputStream;

public class PsiBinaryFileImpl extends PsiElementBase implements PsiBinaryFile, Cloneable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiBinaryFileImpl");

  private final PsiManagerImpl myManager;
  private VirtualFile myFile;
  private String myName; // for myFile == null only
  private byte[] myContents; // for myFile == null only
  private long myModificationStamp;
  private FileType myFileType;

  public PsiBinaryFileImpl(PsiManagerImpl manager, VirtualFile vFile) {
    myManager = manager;
    myFile = vFile;
    myModificationStamp = myFile.getModificationStamp();
    myFileType = FileTypeManager.getInstance().getFileTypeByFile(myFile);
  }

  public VirtualFile getVirtualFile() {
    return myFile;
  }

  public void setVirtualFile(VirtualFile file) throws IOException {
    myFile = file;
    if (file != null){
      myName = null;
      if (myContents != null){
        OutputStream out = myFile.getOutputStream(myManager);
        out.write(myContents);
        out.close();
        myContents = null;
      }
    }
  }

  public byte[] getStoredContents() {
    return myContents;
  }

  public String getName() {
    return myFile != null ? myFile.getName() : myName;
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    checkSetName(name);

    if (myFile == null){
      myName = name;
      return this; // not absolutely correct - might change type
    }

    return PsiFileImplUtil.setName(this, name);
  }

  public void checkSetName(String name) throws IncorrectOperationException {
    if (myFile == null) return;
    PsiFileImplUtil.checkSetName(this, name);
  }

  public PsiDirectory getContainingDirectory() {
    VirtualFile parentFile = myFile.getParent();
    if (parentFile == null) return null;
    return getManager().findDirectory(parentFile);
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  public void setModificationStamp(long modificationStamp) {
    myModificationStamp = modificationStamp;
  }

  public PsiElement[] getOnDemandImports(boolean includeImplicit, boolean checkIncludes) {
    return null;
  }

  public PsiClass[] getSingleClassImports(boolean checkIncludes) {
    return null;
  }

  public String[] getImplicitlyImportedPackages() {
    return null;
  }

  public PsiJavaCodeReferenceElement[] getImplicitlyImportedPackageReferences() {
    return null;
  }

  public PsiJavaCodeReferenceElement findImportReferenceTo(PsiClass aClass) {
    return null;
  }

  public PsiManager getManager() {
    return myManager;
  }

  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiElement getParent() {
    return getContainingDirectory();
  }

  public PsiFile getContainingFile() {
    return this;
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

  public void accept(PsiElementVisitor visitor) {
    visitor.visitBinaryFile(this);
  }

  public PsiElement copy() {
    PsiBinaryFileImpl clone = (PsiBinaryFileImpl)clone();
    clone.myFile = null;
    clone.myName = getName();
    try{
      clone.myContents = myFile != null ? myFile.contentsToByteArray() : myContents;
    }
    catch(IOException e){
    }
    return clone;
  }

  public PsiElement add(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAdd(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAddBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAddAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void delete() throws IncorrectOperationException{
    checkDelete();
    PsiFileImplUtil.doDelete(this);
  }

  public void checkDelete() throws IncorrectOperationException{
    if (myFile == null){
      throw new IncorrectOperationException();
    }
    CheckUtil.checkWritable(this);
  }

  public PsiElement replace(PsiElement newElement) throws IncorrectOperationException {
    return null;
  }

  public void checkReplace(PsiElement newElement) throws IncorrectOperationException {
  }

  public boolean isValid() {
    if (myFile == null) return true; // "dummy" file
    if (!myFile.isValid()) return false;
    return myManager.getFileManager().findFile(myFile) == this;
  }

  public boolean isWritable() {
    return myFile != null ? myFile.isWritable() : true;
  }

  public boolean isPhysical() {
    return myFile != null;
  }

  public String getDetectedLineSeparator() {
    throw new UnsupportedOperationException();
  }

  public PsiFile getOriginalFile() {
    return null;
  }

  public String toString() {
    return "PsiBinaryFile:" + getName();
  }

  public boolean canContainJavaCode() {
    return false;
  }

  public FileType getFileType() {
    return myFileType;
  }

  public PsiFile[] getPsiRoots() {
    return new PsiFile[]{this};
  }

  public PsiFile createPseudoPhysicalCopy() {
    LOG.assertTrue(false);
    return null;
  }
}
