// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion.model;

public class SearchParameters {

  public static final SearchParameters DEFAULT = new SearchParameters(200, 2000, false, 300);
  public static final SearchParameters FULL = new SearchParameters(200, 2000, true, 0);
  private final int maxResults;
  private final long millisToWait;
  private final boolean showAll;
  private final int myThrottleTime;


  public SearchParameters(int maxResults, long wait, boolean showAll, int throttleTime) {
    this.maxResults = maxResults;
    this.millisToWait = wait;
    this.showAll = showAll;
    myThrottleTime = throttleTime;
  }

  public int getMaxResults() {
    return maxResults;
  }


  public long getMillisToWait() {
    return millisToWait;
  }

  public boolean isShowAll() {
    return showAll;
  }

  public int getThrottleTime() {
    return myThrottleTime;
  }
}
