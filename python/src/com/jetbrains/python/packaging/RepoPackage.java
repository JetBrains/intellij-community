package com.jetbrains.python.packaging;

/**
 * User: catherine
 */
public class RepoPackage implements Comparable {
  private final String myName;
  private final String myRepoUrl;

  public RepoPackage(String name, String repoUrl) {
    myName = name;
    myRepoUrl = repoUrl;
  }

  public String getName() {
    return myName;
  }

  public String getRepoUrl() {
    return myRepoUrl;
  }

  @Override
  public int compareTo(Object o) {
    if (o instanceof RepoPackage)
      return myName.compareTo(((RepoPackage)o).getName());
    return 0;
  }
}