package org.jetbrains.idea.svn.integrate;

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
