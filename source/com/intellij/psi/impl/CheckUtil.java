package com.intellij.psi.impl;

import com.intellij.psi.*;

import com.intellij.openapi.vfs.VirtualFile;

import com.intellij.util.IncorrectOperationException;

/**
 *
 */
public class CheckUtil {
  public static void checkIsIdentifier(PsiManager manager, String text) throws IncorrectOperationException{
    if (!manager.getNameHelper().isIdentifier(text)){
      throw new IncorrectOperationException("\"" + text + "\" is not an identifier." );
    }
  }

  public static void checkWritable(PsiElement element) throws IncorrectOperationException{
    if (!element.isWritable()){
      if (element instanceof PsiDirectory){
        throw new IncorrectOperationException("Cannot modify a read-only directory " + ((PsiDirectory)element).getVirtualFile().getPresentableUrl() + ".");
      }
      else{
        PsiFile file = element.getContainingFile();
        if (file == null){
          throw new IncorrectOperationException();
        }
        throw new IncorrectOperationException("Cannot modify a read-only file " + file.getVirtualFile().getPresentableUrl() + ".");
      }
    }
  }

  public static void checkNotCompiled(PsiElement element) throws IncorrectOperationException{
    if (element instanceof PsiCompiledElement){
      PsiClass aClass;
      if (element instanceof PsiFile){
        aClass = ((PsiJavaFile)element).getClasses()[0];
      }
      else{
        PsiElement parent = element;
        while(!(parent instanceof PsiClass)){
          parent = parent.getParent();
        }
        aClass = (PsiClass)parent;
      }
      throw new IncorrectOperationException("Cannot modify a compiled class " + aClass.getQualifiedName() + ".");
    }
  }

  public static void checkDelete(VirtualFile file) throws IncorrectOperationException{
    if (!file.isWritable()){
      throw new IncorrectOperationException("Cannot delete a read-only file " + file.getPresentableUrl() + ".");
    }
    if (file.isDirectory()){
      VirtualFile[] children = file.getChildren();
      for(int i = 0; i < children.length; i++) {
        checkDelete(children[i]);
      }
    }
  }
}
