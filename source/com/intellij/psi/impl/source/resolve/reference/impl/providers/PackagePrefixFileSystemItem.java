package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

/**
 * @author Gregory.Shrago
*/
class PackagePrefixFileSystemItem extends PsiElementBase implements PsiFileSystemItem {
  private final PsiDirectory myDirectory;
  private final int myIndex;
  private final PsiPackage[] myPackages;

  public static PackagePrefixFileSystemItem create(final PsiDirectory directory) {
    final ArrayList<PsiPackage> packages = new ArrayList<PsiPackage>();
    for (PsiPackage cur = JavaDirectoryService.getInstance().getPackage(directory); cur != null; cur = cur.getParentPackage()) {
      packages.add(0, cur);
    }
    return new PackagePrefixFileSystemItem(directory, 0, packages.toArray(new PsiPackage[packages.size()]));
  }

  private PackagePrefixFileSystemItem(final PsiDirectory directory, int index, final PsiPackage[] packages) {
    myDirectory = directory;
    myIndex = index;
    myPackages = packages;
  }

  @NotNull
  public String getName() {
    return myPackages[myIndex].getName();
  }

  public PsiElement setName(@NonNls @NotNull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkSetName(final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public boolean isDirectory() {
    return true;
  }

  public PsiFileSystemItem getParent() {
    return myIndex > 0 ? new PackagePrefixFileSystemItem(myDirectory, myIndex - 1, myPackages) : myDirectory.getParent();
  }

  public PsiFile getContainingFile() throws PsiInvalidElementAccessException {
    return null;
  }

  public TextRange getTextRange() {
    return null;
  }

  public int getStartOffsetInParent() {
    return 0;
  }

  public int getTextLength() {
    return 0;
  }

  @Nullable
  public PsiElement findElementAt(final int offset) {
    return null;
  }

  public int getTextOffset() {
    return 0;
  }

  @NonNls
  public String getText() {
    return "";
  }

  @NotNull
  public char[] textToCharArray() {
    return ArrayUtil.EMPTY_CHAR_ARRAY;
  }

  public boolean textMatches(@NotNull @NonNls final CharSequence text) {
    return false;
  }

  public boolean textMatches(@NotNull final PsiElement element) {
    return false;
  }

  public void accept(@NotNull final PsiElementVisitor visitor) {
  }

  public PsiElement copy() {
    return null;
  }

  public PsiElement add(@NotNull final PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addBefore(@NotNull final PsiElement element, final PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(@NotNull final PsiElement element, final PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAdd(@NotNull final PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void delete() throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkDelete() throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement replace(@NotNull final PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public boolean isValid() {
    return myDirectory.isValid();
  }

  public boolean isWritable() {
    final VirtualFile file = getVirtualFile();
    return file != null && file.isWritable();
  }

  public boolean isPhysical() {
    final VirtualFile file = getVirtualFile();
    return file != null && !(file.getFileSystem() instanceof DummyFileSystem);
  }

  @Nullable
  public ASTNode getNode() {
    return null;
  }

  public boolean processChildren(final PsiElementProcessor<PsiFileSystemItem> processor) {
    if (myIndex == myPackages.length - 1) {
      return myDirectory.processChildren(processor);
    }
    else {
      return processor.execute(new PackagePrefixFileSystemItem(myDirectory, myIndex+1, myPackages));
    }
  }

  @NotNull
  public Language getLanguage() {
    return Language.ANY;
  }

  public PsiManager getManager() {
    return myDirectory.getManager();
  }

  @NotNull
  public PsiElement[] getChildren() {
    return myIndex == myPackages.length -1? myDirectory.getChildren() : new PsiElement[] {new PackagePrefixFileSystemItem(myDirectory, myIndex + 1, myPackages)};
  }

  public boolean canNavigate() {
    return getVirtualFile() != null;
  }

  public VirtualFile getVirtualFile() {
    if (myIndex == myPackages.length - 1) {
      return myDirectory.getVirtualFile();
    }
    else {
      return null;
    }
  }

  @Nullable
  public Icon getIcon(final int flags) {
    return myDirectory.getIcon(flags);
  }
}
