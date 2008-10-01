package org.jetbrains.idea.maven.runner;

import org.jetbrains.idea.maven.utils.Path;

import java.io.File;
import java.util.*;

public class MavenRunnerParameters implements Cloneable {
  private boolean isPomExecution;
  private Path myWorkingDirPath;
  private List<String> myGoals;
  private SortedSet<String> myProfiles;

  public MavenRunnerParameters() {
    this(true, "", null, null);
  }

  public MavenRunnerParameters(boolean isPomExecution, String workingDirPath, List<String> goals, Collection<String> profiles) {
    this.isPomExecution = isPomExecution;
    setWorkingDirPath(workingDirPath);
    setGoals(goals);
    setProfiles(profiles);
  }

  public MavenRunnerParameters(MavenRunnerParameters that) {
    this(that.isPomExecution, that.getWorkingDirPath(), that.myGoals, that.myProfiles);
  }

  public boolean isPomExecution() {
    return isPomExecution;
  }

  public String getWorkingDirPath() {
    return myWorkingDirPath.getPath();
  }

  public void setWorkingDirPath(String workingDirPath) {
    myWorkingDirPath = new Path(workingDirPath);
  }

  public File getWorkingDirFile() {
    return new File(myWorkingDirPath.getPath());
  }

  public String getPomFilePath() {
    if (!isPomExecution) return null;
    return new File(myWorkingDirPath.getPath(), "pom.xml").getPath();
  }

  public List<String> getGoals() {
    return myGoals;
  }

  public void setGoals(List<String> goals) {
    myGoals = new ArrayList<String>();
    if (goals != null) {
      myGoals.addAll(goals);
    }
  }

  public Collection<String> getProfiles() {
    return myProfiles;
  }

  public void setProfiles(Collection<String> profiles) {
    myProfiles = new TreeSet<String>();
    if (profiles != null) {
      myProfiles.addAll(profiles);
    }
  }

  public MavenRunnerParameters clone() {
    return new MavenRunnerParameters(this);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final MavenRunnerParameters that = (MavenRunnerParameters)o;

    if (isPomExecution == that.isPomExecution) return false;
    if (myGoals != null ? !myGoals.equals(that.myGoals) : that.myGoals != null) return false;
    if (myWorkingDirPath != null ? !myWorkingDirPath.equals(that.myWorkingDirPath) : that.myWorkingDirPath != null) return false;
    if (myProfiles != null ? !myProfiles.equals(that.myProfiles) : that.myProfiles != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = isPomExecution ? 1 : 0;
    result = 31 * result + (myWorkingDirPath != null ? myWorkingDirPath.hashCode() : 0);
    result = 31 * result + (myGoals != null ? myGoals.hashCode() : 0);
    result = 31 * result + (myProfiles != null ? myProfiles.hashCode() : 0);
    return result;
  }
}
