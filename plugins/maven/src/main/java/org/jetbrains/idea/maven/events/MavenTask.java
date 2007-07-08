package org.jetbrains.idea.maven.events;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.builder.executor.MavenBuildParameters;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import java.util.Arrays;
import java.util.Comparator;

/**
 * @author Vladislav.Kaznacheev
*/
public class MavenTask implements Cloneable {

  public String pomPath;
  public String goal;

  public final static Comparator<MavenTask> ourComparator = new Comparator<MavenTask>() {
    public int compare(final MavenTask o1, final MavenTask o2) {
      return o1.toString().compareTo(o2.toString());
    }
  };

  public MavenTask() {
  }

  public MavenTask(final String pomPath, final String goal) {
    this.pomPath = pomPath;
    this.goal = goal;
  }

  protected MavenTask clone() {
    return new MavenTask(pomPath, goal);
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenTask task = (MavenTask)o;

    if (goal != null ? !goal.equals(task.goal) : task.goal != null) return false;
    if (pomPath != null ? !pomPath.equals(task.pomPath) : task.pomPath != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (pomPath != null ? pomPath.hashCode() : 0);
    result = 31 * result + (goal != null ? goal.hashCode() : 0);
    return result;
  }

  public String toString(){
    return pomPath + "#" + goal;
  }

  @Nullable
  public MavenBuildParameters createBuildParameters(final MavenProjectsState projectsState) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(pomPath);
    if (virtualFile != null) {
      return new MavenBuildParameters(pomPath, Arrays.asList(goal), projectsState.getProfiles(virtualFile));
    }
    return null;
  }
}
