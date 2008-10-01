package org.jetbrains.idea.maven.runner;

import com.intellij.execution.PsiLocation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

import java.util.List;

public class MavenGoalLocation extends PsiLocation<PsiFile> {
  private List<String> myGoals;

  public MavenGoalLocation(Project p, VirtualFile f, List<String> goals) {
    super(p, PsiManager.getInstance(p).findFile(f));
    myGoals = goals;
  }

  public List<String> getGoals() {
    return myGoals;
  }
}
