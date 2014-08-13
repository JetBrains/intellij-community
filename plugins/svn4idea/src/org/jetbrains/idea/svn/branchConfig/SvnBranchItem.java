/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.branchConfig;

import java.util.Date;

/**
 * @author yole
 */
public class SvnBranchItem implements Comparable<SvnBranchItem> {
  private String myUrl;
  private long myCreationDateMillis;
  private long myRevision;

  // to be serializable
  public SvnBranchItem() {
  }

  public SvnBranchItem(final String url, final Date creationDate, final long revision) {
    myUrl = url;
    // descendant can be passed (and is passed) (java.util.Date is not final)
    myCreationDateMillis = creationDate.getTime();
    myRevision = revision;
  }

  public void setUrl(final String url) {
    myUrl = url;
  }

  public void setCreationDateMillis(final long creationDate) {
    myCreationDateMillis = creationDate;
  }

  public void setRevision(final long revision) {
    myRevision = revision;
  }

  public String getUrl() {
    return myUrl;
  }

  public long getCreationDateMillis() {
    return myCreationDateMillis;
  }

  public long getRevision() {
    return myRevision;
  }

  public int compareTo(SvnBranchItem o) {
    // === -compare()
    return myCreationDateMillis < o.myCreationDateMillis ? 1 : ((myCreationDateMillis == o.myCreationDateMillis) ? 0 : -1);
  }
}
