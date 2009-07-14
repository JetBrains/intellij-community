package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.refactoring.MoveDestination;
import com.intellij.util.IncorrectOperationException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim.Medvedev
 */
public class MoveJavaClassHandler implements MoveClassHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveClassesOrPackages.MoveJavaClassHandler");

  public PsiClass doMoveClass(@NotNull PsiClass aClass, @NotNull MoveDestination moveDestination) throws IncorrectOperationException {
    PsiFile file = aClass.getContainingFile();
    PsiDirectory newDirectory = moveDestination.getTargetDirectory(file);
    final PsiPackage newPackage = JavaDirectoryService.getInstance().getPackage(newDirectory);

    PsiClass newClass = null;
    if (file instanceof PsiJavaFile && ((PsiJavaFile)file).getClasses().length > 1) {
      correctSelfReferences(aClass, newPackage);
      final PsiClass created = JavaDirectoryService.getInstance().createClass(newDirectory, aClass.getName());
      if (aClass.getDocComment() == null) {
        final PsiDocComment createdDocComment = created.getDocComment();
        if (createdDocComment != null) {
          aClass.addAfter(createdDocComment, null);
        }
      }
      newClass = (PsiClass)created.replace(aClass);
      correctOldClassReferences(newClass, aClass);
      aClass.delete();
    }
    else if (file instanceof PsiJavaFile &&
             !newDirectory.equals(file.getContainingDirectory()) &&
             newDirectory.findFile(file.getName()) != null) {
      // moving second of two classes which were in the same file to a different directory (IDEADEV-3089)
      correctSelfReferences(aClass, newPackage);
      PsiFile newFile = newDirectory.findFile(file.getName());
      newClass = (PsiClass)newFile.add(aClass);
      aClass.delete();
    }
    return newClass;
  }

  private static void correctOldClassReferences(final PsiClass newClass, final PsiClass oldClass) {
    newClass.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        if (reference.isReferenceTo(oldClass)) {
          try {
            reference.bindToElement(newClass);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        super.visitReferenceElement(reference);
      }
    });
  }

  private static void correctSelfReferences(final PsiClass aClass, final PsiPackage newContainingPackage) {
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(aClass.getContainingFile().getContainingDirectory());
    if (aPackage != null) {
      aClass.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          if (reference.isQualified() && reference.isReferenceTo(aClass)) {
            final PsiElement qualifier = reference.getQualifier();
            if (qualifier instanceof PsiJavaCodeReferenceElement && ((PsiJavaCodeReferenceElement)qualifier).isReferenceTo(aPackage)) {
              try {
                ((PsiJavaCodeReferenceElement)qualifier).bindToElement(newContainingPackage);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          }
          super.visitReferenceElement(reference);
        }
      });
    }
  }

  public String getName(PsiClass clazz) {
    final PsiFile file = clazz.getContainingFile();
    if (!(file instanceof PsiJavaFile)) return null;
    return ((PsiJavaFile)file).getClasses().length > 1 ? clazz.getName() + "." + StdFileTypes.JAVA.getDefaultExtension() : file.getName();
  }
}
