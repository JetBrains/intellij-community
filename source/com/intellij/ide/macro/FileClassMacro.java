
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.DataAccessors;

public final class FileClassMacro extends Macro {
  public String getName() {
    return "FileClass";
  }

  public String getDescription() {
    return IdeBundle.message("macro.class.name");
  }

  public String expand(DataContext dataContext) {
    //Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    //if (project == null) {
    //  return null;
    //}
    //VirtualFile file = (VirtualFile)dataContext.getData(DataConstantsEx.VIRTUAL_FILE);
    //if (file == null) {
    //  return null;
    //}
    //PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    //if (!(psiFile instanceof PsiJavaFile)) {
    //  return null;
    //}
    final PsiJavaFile javaFile = DataAccessors.PSI_JAVA_FILE.from(dataContext);
    if (javaFile == null) return null;
    PsiClass[] classes = javaFile.getClasses();
    if (classes.length == 1) {
      return classes[0].getQualifiedName();
    }
    String fileName = javaFile.getVirtualFile().getNameWithoutExtension();
    for (int i = 0; i < classes.length; i++) {
      PsiClass aClass = classes[i];
      String name = aClass.getName();
      if (fileName.equals(name)) {
        return aClass.getQualifiedName();
      }
    }
    return null;
  }
}
