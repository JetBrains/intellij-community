package com.jetbrains.python.edu;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class InvalidTaskFileFix {
  private final Project myProject;
  private CharSequence myText;
  private int myOffset;

  public InvalidTaskFileFix(@NotNull CharSequence text, int offset, @NotNull final Project project) {
    myText = text;
    myOffset = offset;
    myProject = project;
  }

  public void applyFix(@NotNull final Document document) {
    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            //we replace only empty text in this fix
            document.replaceString(myOffset, myOffset, myText);
            FileDocumentManager.getInstance().saveDocument(document);
          }
        });
      }
    });
  }

  public Project getProject() {
    return myProject;
  }
}
