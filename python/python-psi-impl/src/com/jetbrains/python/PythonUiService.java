package com.jetbrains.python;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public class PythonUiService {

  public void showBalloonInfo(Project project, String message) {}

  public void showBalloonError(Project project, String message) {}

  public Editor openTextEditor(PsiFile file) {
    return null;
  }

  public boolean showYesDialog(Project project, String title, String message) {
    return false;
  }

  public static PythonUiService getInstance() {
    return ServiceManager.getService(PythonUiService.class);
  }
}
