package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MoveClassesOrPackagesUtil {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil");

  public static UsageInfo[] findUsages(final PsiElement element,
                                       boolean searchInStringsAndComments,
                                       boolean searchInNonJavaFiles,
                                       final String newQName) {
    PsiManager manager = element.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();

    if (Comparing.equal(getQualfiedName(element), newQName)) return new UsageInfo[0];

    ArrayList<UsageInfo> results = new ArrayList<UsageInfo>();
    Set foundReferences = new HashSet();

    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
    PsiReference[] references = helper.findReferences(element, projectScope, false);
    for (int i = 0; i < references.length; i++) {
      PsiReference reference = references[i];
      TextRange range = reference.getRangeInElement();
      if (foundReferences.contains(reference)) continue;
      results.add(
        new MoveRenameUsageInfo(reference.getElement(), reference, range.getStartOffset(), range.getEndOffset(),
                                element, false));
      foundReferences.add(reference);
    }

    findNonCodeUsages(searchInStringsAndComments, searchInNonJavaFiles, element, newQName, results);

    return (UsageInfo[])results.toArray(new UsageInfo[results.size()]);
  }

  private static String getQualfiedName(final PsiElement element) {
    final String oldQName;
    if (element instanceof PsiClass) {
      oldQName = ((PsiClass)element).getQualifiedName();
    }
    else if (element instanceof PsiPackage) {
      oldQName = ((PsiPackage)element).getQualifiedName();
    }
    else if (element instanceof PsiDirectory) {
      final PsiPackage aPackage = ((PsiDirectory)element).getPackage();
      oldQName = aPackage != null ? aPackage.getQualifiedName() : null;
    }
    else {
      oldQName = null;
    }
    return oldQName;
  }

  public static void findNonCodeUsages(boolean searchInStringsAndComments,
                                       boolean searchInNonJavaFiles,
                                       final PsiElement element,
                                       final String newQName,
                                       ArrayList<UsageInfo> results) {
    if (searchInStringsAndComments || searchInNonJavaFiles) {
      final String stringToSearch = getStringToSearch(element);
      RefactoringUtil.UsageInfoFactory factory = createUsageInfoFactory(element, newQName);

      if (searchInStringsAndComments) {
        RefactoringUtil.addUsagesInStringsAndComments(element, stringToSearch, results, factory);
      }

      if (searchInNonJavaFiles) {
        GlobalSearchScope projectScope = GlobalSearchScope.projectScope(element.getProject());
        RefactoringUtil.addUsagesInNonJavaFiles(element, stringToSearch, projectScope, results, factory);
      }
    }
  }

  private static RefactoringUtil.UsageInfoFactory createUsageInfoFactory(final PsiElement element,
                                                                         final String newQName) {
    return new RefactoringUtil.UsageInfoFactory() {
      public UsageInfo createUsageInfo(PsiElement usage, int startOffset, int endOffset) {
        int start = usage.getTextRange().getStartOffset();
        return NonCodeUsageInfo.create(usage.getContainingFile(), start + startOffset, start + endOffset, element,
                                       newQName);
      }
    };
  }

  public static String getStringToSearch(PsiElement element) {
    if (element instanceof PsiPackage) {
      return ((PsiPackage)element).getQualifiedName();
    }
    else if (element instanceof PsiClass) {
      return ((PsiClass)element).getQualifiedName();
    }
    else {
      LOG.error("Unknown element type");
      return null;
    }
  }

  // Does not process non-code usages!
  public static PsiPackage doMovePackage(PsiPackage aPackage, MoveDestination moveDestination, UsageInfo[] usages)
    throws IncorrectOperationException {
    PsiManager manager = aPackage.getManager();
    final PackageWrapper targetPackage = moveDestination.getTargetPackage();

    // Collect destination packages
    Map<MoveRenameUsageInfo, String> targetPackages = new HashMap<MoveRenameUsageInfo, String>();
    final String newPrefix;
    if ("".equals(targetPackage.getQualifiedName())) {
      newPrefix = "";
    }
    else {
      newPrefix = targetPackage.getQualifiedName() + ".";
    }

    final String newPackageQualifiedName = newPrefix + aPackage.getName();

    for (int i = 0; i < usages.length; i++) {
      MoveRenameUsageInfo usage = (MoveRenameUsageInfo)usages[i];
      LOG.assertTrue(usage.referencedElement instanceof PsiPackage);
      final PsiPackage oldPackage = (PsiPackage)usage.referencedElement;
      LOG.assertTrue(!"".equals(oldPackage.getName()));
      targetPackages.put(usage, newPrefix + oldPackage.getName());
    }

    // do actual move
    final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(aPackage.getProject());
    PsiDirectory[] dirs = aPackage.getDirectories(projectScope);
    for (int i = 0; i < dirs.length; i++) {
      PsiDirectory dir = dirs[i];
      final PsiDirectory targetDirectory = moveDestination.getTargetDirectory(dir);
      if (targetDirectory != null) {
        moveDirectoryRecursively(dir, targetDirectory);
      }
    }

    // rename all references
    for (int i = 0; i < usages.length; i++) {
      MoveRenameUsageInfo usage = (MoveRenameUsageInfo)usages[i];
      if (!usage.getElement().isValid()) continue;
      PsiReference reference = usage.reference;
      if (reference != null) {
        final String newQName = targetPackages.get(usage);
        final PsiPackage newPackage = manager.findPackage(newQName);
        reference.bindToElement(newPackage);
      }
    }

    aPackage.handleQualifiedNameChange(newPackageQualifiedName);

    return manager.findPackage(newPackageQualifiedName);
  }

  public static void moveDirectoryRecursively(PsiDirectory dir, PsiDirectory destination)
    throws IncorrectOperationException {
    moveDirectoryRecursively(dir, destination, new HashSet<VirtualFile>());
  }

  public static void moveDirectoryRecursively(PsiDirectory dir, PsiDirectory destination, HashSet<VirtualFile> movedPaths)
    throws IncorrectOperationException {
    final PsiManager manager = dir.getManager();
    final VirtualFile destVFile = destination.getVirtualFile();
    final VirtualFile sourceVFile = dir.getVirtualFile();
    if (movedPaths.contains(sourceVFile)) return;
    String targetName = dir.getName();
    final String sourcePackageName = dir.getPackage().getName();
    if (!targetName.equals(sourcePackageName)) {
      targetName = sourcePackageName;
    }
    final PsiDirectory subdirectoryInDest;
    final boolean isSourceRoot = RefactoringUtil.isSourceRoot(dir);
    if (VfsUtil.isAncestor(sourceVFile, destVFile, false) || isSourceRoot) {
      PsiDirectory exitsingSubdir = destination.findSubdirectory(targetName);
      if (exitsingSubdir == null) {
        subdirectoryInDest = destination.createSubdirectory(targetName);
        movedPaths.add(subdirectoryInDest.getVirtualFile());
      } else {
        subdirectoryInDest = exitsingSubdir;
      }
    } else {
      subdirectoryInDest = destination.findSubdirectory(targetName);
    }

    if (subdirectoryInDest == null) {
      VirtualFile virtualFile = dir.getVirtualFile();
      manager.moveDirectory(dir, destination);
      movedPaths.add(virtualFile);
    }
    else {
      final PsiFile[] files = dir.getFiles();
      for (int i = 0; i < files.length; i++) {
        PsiFile file = files[i];
        try {
          subdirectoryInDest.checkAdd(file);
        }
        catch (IncorrectOperationException e) {
          continue;
        }
        manager.moveFile(file, subdirectoryInDest);
      }

      final PsiDirectory[] subdirectories = dir.getSubdirectories();
      for (int i = 0; i < subdirectories.length; i++) {
        PsiDirectory subdirectory = subdirectories[i];
        if (!subdirectory.equals(subdirectoryInDest)) {
          moveDirectoryRecursively(subdirectory, subdirectoryInDest, movedPaths);
        }
      }
      if (!isSourceRoot && dir.getFiles().length == 0 && dir.getSubdirectories().length == 0) {
        dir.delete();
      }
    }
  }
// Does not process non-code usages!
  public static PsiClass doMoveClass(PsiClass aClass, MoveDestination moveDestination, UsageInfo[] usages)
    throws IncorrectOperationException {
    PsiManager manager = aClass.getManager();

// the class is already there, this is true when multiple classes are defined in the same file
    PsiFile file = aClass.getContainingFile();
    PsiDirectory newDirectory = moveDestination.getTargetDirectory(file);
    if (!newDirectory.equals(file.getContainingDirectory())) {
// do actual move
      manager.moveFile(file, newDirectory);

      if (file instanceof PsiJavaFile) { // Q: move this code into some util?
        PsiPackage newPackage = newDirectory.getPackage();
        PsiJavaFile javaFile = (PsiJavaFile)file;
        PsiPackageStatement packageStatement = javaFile.getPackageStatement();
        String packageName = newPackage.getQualifiedName();
        if (packageStatement != null) {
          if (packageName.length() > 0) {
            packageStatement.getPackageReference().bindToElement(newPackage);
          }
          else {
            packageStatement.delete();
          }
        }
        else {
          if (packageName.length() > 0) {
            String text = "package " + packageName + ";";
            String ext = StdFileTypes.JAVA.getDefaultExtension();
            PsiJavaFile dummyFile = (PsiJavaFile)manager.getElementFactory().createFileFromText("_Dummy_." + ext, text);
            packageStatement = dummyFile.getPackageStatement();
            file.add(packageStatement);
          }
        }
      }
    }

// rename all references
    for (int i = 0; i < usages.length; i++) {
      MoveRenameUsageInfo usage = (MoveRenameUsageInfo)usages[i];
      if (usage.getElement() == null || !usage.getElement().isValid()) continue;
      PsiReference reference = usage.reference;
      if (reference != null) {
        PsiElement parent = reference.getElement().getParent();
        if (parent instanceof PsiImportStatement) {
          if (parent.getContainingFile().getContainingDirectory().equals(newDirectory)) {
            parent.delete(); // remove import statement to the class in the same package
            continue;
          }
        }

        reference.bindToElement(aClass);
      }
    }

    return aClass;
  }

  public static String getPackageName(PackageWrapper aPackage) {
    if (aPackage == null) {
      return null;
    }
    String name = aPackage.getQualifiedName();
    if (name.length() > 0) {
      return name;
    }
    else {
      return UsageViewUtil.DEFAULT_PACKAGE_NAME;
    }
  }
}