package org.jetbrains.idea.maven.tasks;

import com.intellij.execution.BeforeRunTask;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.idea.maven.project.MavenProject;

public class MavenBeforeRunTask extends BeforeRunTask {
  private String myProjectPath;
  private String myGoal;

  public MavenBeforeRunTask() {
  }

  public MavenBeforeRunTask(String projectPath, String goal) {
    myProjectPath = projectPath;
    myGoal = goal;
  }

  public String getProjectPath() {
    return myProjectPath;
  }

  public void setProjectPath(String projectPath) {
    myProjectPath = projectPath;
  }

  public String getGoal() {
    return myGoal;
  }

  public void setGoal(String goal) {
    myGoal = goal;
  }

  public boolean isFor(MavenProject project, String goal) {
    if (myProjectPath == null || myGoal == null) return false;
    return FileUtil.pathsEqual(project.getPath(), myProjectPath) && goal.equals(myGoal);
  }

  @Override
  public void writeExternal(Element element) {
    super.writeExternal(element);
    if (myProjectPath != null) element.setAttribute("file", myProjectPath);
    if (myGoal != null) element.setAttribute("goal", myGoal);
  }

  @Override
  public void readExternal(Element element) {
    super.readExternal(element);
    myProjectPath = element.getAttributeValue("file");
    myGoal = element.getAttributeValue("goal");
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    MavenBeforeRunTask that = (MavenBeforeRunTask)o;

    if (myGoal != null ? !myGoal.equals(that.myGoal) : that.myGoal != null) return false;
    if (myProjectPath != null ? !myProjectPath.equals(that.myProjectPath) : that.myProjectPath != null) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myProjectPath != null ? myProjectPath.hashCode() : 0);
    result = 31 * result + (myGoal != null ? myGoal.hashCode() : 0);
    return result;
  }
}
