package com.jetbrains.python;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@ApiStatus.Experimental
public class PythonDialogService {
  public static PythonDialogService getInstance() {
    return ServiceManager.getService(PythonDialogService.class);
  }

  public int showChooseDialog(String message,
                                     @Nls(capitalization = Nls.Capitalization.Title) String title,
                                     String[] values,
                                     String initialValue,
                                     @Nullable Icon icon) {
    throw new FailException();
  }

  public void showNoExternalDocumentationDialog(Project project, QualifiedName name) {
    throw new FailException();
  }

  private final static class FailException extends UnsupportedOperationException {
    private FailException() {
      super("no UI in PSI");
    }
  }
}
