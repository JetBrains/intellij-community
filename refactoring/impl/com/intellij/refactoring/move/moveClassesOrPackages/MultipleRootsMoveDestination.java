package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

/**
 *  @author dsl
 */
public class MultipleRootsMoveDestination extends AutocreatingMoveDestination {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.move.moveClassesOrPackages.MultipleRootsMoveDestination");

  public MultipleRootsMoveDestination(PackageWrapper aPackage) {
    super(aPackage);
  }

  public PackageWrapper getTargetPackage() {
    return myPackage;
  }


  public PsiDirectory getTargetDirectory(PsiDirectory source) throws IncorrectOperationException {
    if (JavaDirectoryService.getInstance().isSourceRoot(source)) return null;
    return getOrCreateDirectoryForSource(source.getVirtualFile());
  }

  public PsiDirectory getTargetDirectory(PsiFile source) throws IncorrectOperationException {
    return getOrCreateDirectoryForSource(source.getVirtualFile());
  }

  public PsiDirectory getTargetIfExists(PsiFile source) {
    return findTargetDirectoryForSource(source.getVirtualFile());
  }

  public String verify(PsiFile source) {
    VirtualFile virtualFile = source.getVirtualFile();
    if (virtualFile.isDirectory()) {
      virtualFile = virtualFile.getParent();
      LOG.assertTrue(virtualFile.isDirectory());
    }

    final VirtualFile sourceRootForFile = myFileIndex.getSourceRootForFile(virtualFile);
    if (sourceRootForFile == null) {
      return "";
    }
    return checkCanCreateInSourceRoot(sourceRootForFile);
  }

  @Nullable
  public String verify(PsiDirectory source) {
    VirtualFile virtualFile = source.getVirtualFile();
    final VirtualFile sourceRootForFile = myFileIndex.getSourceRootForFile(virtualFile);
    if (sourceRootForFile == null) {
      return "";
    }
    if (virtualFile.equals(sourceRootForFile)) return null;
    return checkCanCreateInSourceRoot(sourceRootForFile);
  }

  @Nullable
  public String verify(PsiPackage source) {
    PsiDirectory[] directories = source.getDirectories();
    for (final PsiDirectory directory : directories) {
      String s = verify(directory);
      if (s != null) return s;
    }
    return null;
  }

  public void analyzeModuleConflicts(final Collection<PsiElement> elements,
                                     ArrayList<String> conflicts, final UsageInfo[] usages) {
  }

  public PsiDirectory getTargetIfExists(PsiDirectory source) {
    return findTargetDirectoryForSource(source.getVirtualFile());
  }


  protected PsiDirectory findTargetDirectoryForSource(final VirtualFile file) {
    final VirtualFile sourceRoot = myFileIndex.getSourceRootForFile(file);
    LOG.assertTrue(sourceRoot != null);
    return RefactoringUtil.findPackageDirectoryInSourceRoot(myPackage, sourceRoot);
  }

  protected PsiDirectory getOrCreateDirectoryForSource(final VirtualFile file)
    throws IncorrectOperationException {
    final VirtualFile sourceRoot = myFileIndex.getSourceRootForFile(file);
    LOG.assertTrue(sourceRoot != null);
    return RefactoringUtil.createPackageDirectoryInSourceRoot(myPackage, sourceRoot);
  }
}
