package org.jetbrains.plugins.coursecreator.format;

import com.google.gson.annotations.Expose;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.coursecreator.CCProjectService;

import java.io.File;

public class TaskWindow {

  private static final Logger LOG = Logger.getInstance(TaskWindow.class.getName());
  @Expose public int line;
  @Expose public int start;
  @Expose public String hint;
  @Expose public String possible_answer;
  @Expose public int length;
  public String myTaskText;
  public int myReplacementLength;
  public String myHint;

  public TaskWindow() {}

  public TaskWindow(int line, int start, int length, String selectedText) {
    this.line = line;
    this.start = start;
    myReplacementLength = length;
    this.possible_answer = selectedText;
  }

  public void setTaskText(@NotNull final String taskText) {
    myTaskText = taskText;
    length = myTaskText.length();
  }

  public String getTaskText() {
    return myTaskText;
  }

  public int getReplacementLength() {
    return myReplacementLength;
  }

  public void setHint(String hint) {
    myHint = hint;
  }

  public String getHintName() {
    return myHint;
  }

  public void removeResources(@NotNull final Project project) {
    if (myHint != null) {
      VirtualFile hints = project.getBaseDir().findChild("hints");
      if (hints == null) {
        return;
      }
      File hintFile = new File(hints.getPath(), myHint);
      CCProjectService.deleteProjectFile(hintFile, project);
    }
  }
}