package de.plushnikov.intellij.plugin.action.delombok;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

public class DelombokEverythingHandler implements CodeInsightActionHandler {

  private final DelombokGetterHandler delombokGetterHandler;
  private final DelombokSetterHandler delombokSetterHandler;

  public DelombokEverythingHandler() {
    delombokGetterHandler = new DelombokGetterHandler();
    delombokSetterHandler = new DelombokSetterHandler();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (StdFileTypes.JAVA.equals(file.getFileType())) {
      final PsiJavaFile javaFile = (PsiJavaFile) file;

      for (PsiClass psiClass : javaFile.getClasses()) {
        delombokGetterHandler.processClass(project, psiClass);
        delombokSetterHandler.processClass(project, psiClass);

      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

}
