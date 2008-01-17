package org.jetbrains.idea.maven.runner.executor;

import java.io.File;
import java.util.*;

public class MavenRunnerParameters implements Cloneable {
  private String myPomPath;
  private List<String> myGoals;
  private SortedSet<String> myProfiles;

  public MavenRunnerParameters() {
    this("", null, null);
  }

  public MavenRunnerParameters(String pomPath, List<String> goals, Collection<String> profiles) {
    setPomPath(pomPath);
    setGoals(goals);
    setProfiles(profiles);
  }

  public MavenRunnerParameters(MavenRunnerParameters that) {
    this(that.myPomPath, that.myGoals, that.myProfiles);
  }

  public String getPomPath() {
    return myPomPath;
  }

  public void setPomPath(String pomPath) {
    myPomPath = pomPath;
  }

  public File getPomFile() {
    return new File(myPomPath);
  }

  public File getWorkingDir() {
    return getPomFile().getParentFile();
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

    if (myGoals != null ? !myGoals.equals(that.myGoals) : that.myGoals != null) return false;
    if (myPomPath != null ? !myPomPath.equals(that.myPomPath) : that.myPomPath != null) return false;
    if (myProfiles != null ? !myProfiles.equals(that.myProfiles) : that.myProfiles != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myPomPath != null ? myPomPath.hashCode() : 0);
    result = 31 * result + (myGoals != null ? myGoals.hashCode() : 0);
    result = 31 * result + (myProfiles != null ? myProfiles.hashCode() : 0);
    return result;
  }
}
