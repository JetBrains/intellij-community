package org.jetbrains.idea.maven.project;

import org.apache.maven.model.Resource;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class MavenResource implements Serializable {
  private String myDirectory;
  private boolean myFiltered;
  private String myTargetPath;
  private List<String> myIncludes;
  private List<String> myExcludes;

  protected MavenResource() {
  }

  public MavenResource(Resource resource) {
    myDirectory = resource.getDirectory();
    myFiltered = resource.isFiltering();
    myTargetPath = resource.getTargetPath();
    myIncludes = resource.getIncludes();
    myExcludes = resource.getExcludes();

    if (myIncludes == null) myIncludes = Collections.EMPTY_LIST;
    if (myExcludes == null) myExcludes = Collections.EMPTY_LIST;
  }

  public String getDirectory() {
    return myDirectory;
  }

  public boolean isFiltered() {
    return myFiltered;
  }

  public String getTargetPath() {
    return myTargetPath;
  }

  public List<String> getIncludes() {
    return myIncludes;
  }

  public List<String> getExcludes() {
    return myExcludes;
  }
}
