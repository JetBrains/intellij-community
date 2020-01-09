package org.jetbrains.idea.reposearch;

public class SearchParameters {

  private final boolean myCache;
  private final boolean myLocalOnly;

  public SearchParameters(boolean cache, boolean localOnly) {
    myCache = cache;
    myLocalOnly = localOnly;
  }

  public boolean useCache() {
    return myCache;
  }

  public boolean isLocalOnly() {
    return myLocalOnly;
  }
}
