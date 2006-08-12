/**
 * @author Vladimir Kondratyev
 */
package com.intellij.ide.todo;

import com.intellij.ide.projectView.impl.nodes.PackageElementNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.projectView.impl.nodes.PsiFileNode;
import com.intellij.ide.todo.nodes.ModuleToDoNode;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;

import java.util.Comparator;

public final class TodoFileDirAndModuleComparator implements Comparator{
  public static final TodoFileDirAndModuleComparator ourInstance=new TodoFileDirAndModuleComparator();

  private TodoFileDirAndModuleComparator(){}

  public int compare(Object obj1,Object obj2){
    if((obj1 instanceof PsiFileNode)&&(obj2 instanceof PsiFileNode)){
      return compareFiles(((PsiFileNode)obj1).getValue(),((PsiFileNode)obj2).getValue());
    }else if((obj1 instanceof PsiFileNode)&&(obj2 instanceof PsiDirectoryNode || obj2 instanceof PackageElementNode)){
      return 1;
    }else if((obj1 instanceof PsiDirectoryNode || obj1 instanceof PackageElementNode)&&(obj2 instanceof PsiFileNode)){
      return -1;
    }else if(obj1 instanceof PsiDirectoryNode && obj2 instanceof PackageElementNode){
      return -1;
    }else if(obj1 instanceof PackageElementNode && obj2 instanceof PsiDirectoryNode){
      return 1;
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
        return compareDirs(psiDirectory1, psiDirectory2);
      }
    } else if ((obj1 instanceof PackageElementNode) && (obj2 instanceof PackageElementNode)){
      return ((PackageElementNode)obj1).getValue().getPackage().getQualifiedName().compareTo(((PackageElementNode)obj2).getValue().getPackage().getQualifiedName());
    } else if (obj1 instanceof ModuleToDoNode && obj2 instanceof ModuleToDoNode){
      return ((ModuleToDoNode)obj1).getValue().getName().compareTo(((ModuleToDoNode)obj2).getValue().getName());
    } else if(obj1 instanceof ModuleToDoNode) {
      return -1;
    } else if(obj2 instanceof ModuleToDoNode) {
      return 1;
    } else{
      throw new IllegalArgumentException(obj1.getClass().getName()+","+obj2.getClass().getName());
    }
  }

  private static int compareFiles(PsiFile psiFile1,PsiFile psiFile2){
    return compareVirtualFiles(psiFile1.getVirtualFile(), psiFile2.getVirtualFile());
  }

  private static int compareDirs(PsiDirectory psiDirectory1, PsiDirectory psiDirectory2){
    return compareVirtualFiles(psiDirectory1.getVirtualFile(), psiDirectory2.getVirtualFile());
  }

  private static int compareVirtualFiles(VirtualFile virtualFile1, VirtualFile virtualFile2) {
    String path1=virtualFile1.getPath();
    String path2=virtualFile2.getPath();
    return path1.compareToIgnoreCase(path2);
  }
}
