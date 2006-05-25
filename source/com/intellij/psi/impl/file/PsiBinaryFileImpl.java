package com.intellij.psi.impl.file;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;

public class PsiBinaryFileImpl extends PsiElementBase implements PsiBinaryFile, Cloneable {
  private final PsiManagerImpl myManager;
  private String myName; // for myFile == null only
  private byte[] myContents; // for myFile == null only
  private long myModificationStamp;
  private FileType myFileType;
  private final FileViewProvider myViewProvider;

  public PsiBinaryFileImpl(PsiManagerImpl manager, FileViewProvider viewProvider) {
    myViewProvider = viewProvider;
    myManager = manager;
    final VirtualFile virtualFile = myViewProvider.getVirtualFile();
    myModificationStamp = virtualFile.getModificationStamp();
    myFileType = viewProvider.getVirtualFile().getFileType();
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myViewProvider.getVirtualFile();
  }

  public byte[] getStoredContents() {
    return myContents;
  }

  public String getName() {
    return !isCopy() ? getVirtualFile().getName() : myName;
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    checkSetName(name);

    if (isCopy()){
      myName = name;
      return this; // not absolutely correct - might change type
    }

    return PsiFileImplUtil.setName(this, name);
  }

  public void checkSetName(String name) throws IncorrectOperationException {
    if (isCopy()) return;
    PsiFileImplUtil.checkSetName(this, name);
  }

  public PsiDirectory getContainingDirectory() {
    VirtualFile parentFile = getVirtualFile().getParent();
    if (parentFile == null) return null;
    return getManager().findDirectory(parentFile);
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  @NotNull
  public Language getLanguage() {
    return Language.ANY;
  }

  public PsiManager getManager() {
    return myManager;
  }

  @NotNull
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
    return ""; // TODO throw new InsupportedOperationException()
  }

  @NotNull
  public char[] textToCharArray() {
    return ArrayUtil.EMPTY_CHAR_ARRAY; // TODO throw new InsupportedOperationException()
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
    clone.myName = getName();
    try{
      clone.myContents = !isCopy() ? getVirtualFile().contentsToByteArray() : myContents;
    }
    catch(IOException e){
    }
    return clone;
  }

  private boolean isCopy() {
    return myName != null;
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

  public void delete() throws IncorrectOperationException{
    checkDelete();
    PsiFileImplUtil.doDelete(this);
  }

  public void checkDelete() throws IncorrectOperationException{
    if (isCopy()){
      throw new IncorrectOperationException();
    }
    CheckUtil.checkWritable(this);
  }

  public PsiElement replace(PsiElement newElement) throws IncorrectOperationException {
    return null;
  }

  public boolean isValid() {
    if (isCopy()) return true; // "dummy" file
    return getVirtualFile().isValid() && myManager.getFileManager().findFile(getVirtualFile()) == this;
  }

  public boolean isWritable() {
    return isCopy() || getVirtualFile().isWritable();
  }

  public boolean isPhysical() {
    return !isCopy();
  }

  public PsiFile getOriginalFile() {
    return null;
  }

  @NonNls
  public String toString() {
    return "PsiBinaryFile:" + getName();
  }

  @NotNull
  public FileType getFileType() {
    return myFileType;
  }

  @NotNull
  public PsiFile[] getPsiRoots() {
    return new PsiFile[]{this};
  }

  @NotNull
  public FileViewProvider getViewProvider() {
    return myViewProvider;
  }

  public ASTNode getNode() {
    return null; // TODO throw new InsupportedOperationException()
  }

  public void subtreeChanged() {
  }
}
