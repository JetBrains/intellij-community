package com.intellij.ide.hierarchy;

import com.intellij.ide.util.treeView.AlphaComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.ide.util.treeView.SourceComparator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

import java.util.Comparator;

/**
 * @author yole
 */
public class JavaHierarchyUtil {
  public static final String getPackageName(final PsiClass psiClass) {
    final PsiFile file = psiClass.getContainingFile();
    if (file instanceof PsiJavaFile){
      return ((PsiJavaFile)file).getPackageName();
    }
    else{
      return null;
    }
  }

  public static Comparator<NodeDescriptor> getComparator(Project project) {
    if (HierarchyBrowserManager.getInstance(project).SORT_ALPHABETICALLY) {
      return AlphaComparator.INSTANCE;
    }
    else {
      return SourceComparator.INSTANCE;
    }
  }
}
