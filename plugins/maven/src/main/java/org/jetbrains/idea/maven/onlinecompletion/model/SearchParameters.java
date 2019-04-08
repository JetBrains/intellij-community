// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.onlinecompletion.model;

import java.util.EnumSet;

public class SearchParameters {

  public enum Flags {
    ALL_VERSIONS("requesting all versions"),
    NOT_DEDUPLICATE("Deduplication will not be called");

    Flags(String desc) {}
  }

  public static final SearchParameters DEFAULT = new SearchParameters(200, 1000, EnumSet.noneOf(Flags.class));
  private final int maxResults;
  private final long millisToWait;
  private final EnumSet<Flags> myFlags;


  public SearchParameters(int maxResults, long wait, EnumSet<Flags> flags) {
    this.maxResults = maxResults;
    millisToWait = wait;
    myFlags = flags;
  }

  public int getMaxResults() {
    return maxResults;
  }


  public long getMillisToWait() {
    return millisToWait;
  }

  public EnumSet<Flags> getFlags() {
    return EnumSet.copyOf(myFlags);
  }

  public SearchParameters withFlag(Flags flag) {
    EnumSet<SearchParameters.Flags> newFlags = this.getFlags().clone();
    newFlags.add(flag);
    SearchParameters newParameters = new SearchParameters(getMaxResults(), getMillisToWait(), newFlags);
    return newParameters;
  }

  public SearchParameters withoutFlag(Flags flag) {
    EnumSet<SearchParameters.Flags> newFlags = this.getFlags().clone();
    newFlags.remove(flag);
    SearchParameters newParameters = new SearchParameters(getMaxResults(), getMillisToWait(), newFlags);
    return newParameters;
  }


}
