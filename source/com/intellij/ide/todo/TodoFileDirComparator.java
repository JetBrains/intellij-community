/**
 * @author Vladimir Kondratyev
 */
package com.intellij.ide.todo;

import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;

import java.util.Comparator;

public final class TodoFileDirComparator implements Comparator{
  public static final TodoFileDirComparator ourInstance=new TodoFileDirComparator();

  private TodoFileDirComparator(){}

  public int compare(Object obj1,Object obj2){
    if((obj1 instanceof PsiFileNode)&&(obj2 instanceof PsiFileNode)){
      return compareFiles(((PsiFileNode)obj1).getValue(),((PsiFileNode)obj2).getValue());
    }else if((obj1 instanceof PsiFileNode)&&(obj2 instanceof PsiDirectoryNode)){
      return 1;
    }else if((obj1 instanceof PsiDirectoryNode)&&(obj2 instanceof PsiFileNode)){
      return -1;
    }else if((obj1 instanceof PsiDirectoryNode)&&(obj2 instanceof PsiDirectoryNode)){
      PsiDirectory psiDirectory1=((PsiDirectoryNode)obj1).getValue();
      PsiPackage psiPackage1=psiDirectory1.getPackage();
      PsiDirectory psiDirectory2=((PsiDirectoryNode)obj2).getValue();
      PsiPackage psiPackage2=psiDirectory2.getPackage();
      if(psiPackage1!=null&&psiPackage2==null){
        return -1;
      }else if(psiPackage1==null&&psiPackage2!=null){
        return 1;
      }else if(psiPackage1!=null){
        return psiPackage1.getQualifiedName().compareTo(psiPackage2.getQualifiedName());
      }else{
        String path1=psiDirectory1.getVirtualFile().getPath().toLowerCase();
        String path2=psiDirectory2.getVirtualFile().getPath().toLowerCase();
        return path1.compareTo(path2);
      }
    }else{
      throw new IllegalArgumentException(obj1.getClass().getName()+","+obj2.getClass().getName());
    }
  }

  private static int compareFiles(PsiFile psiFile1,PsiFile psiFile2){
    String path1=psiFile1.getVirtualFile().getPath().toLowerCase();
    String path2=psiFile2.getVirtualFile().getPath().toLowerCase();
    return path1.compareTo(path2);
  }
}
