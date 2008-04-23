package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author yole
 */
public class SvnBranchItem implements Comparable<SvnBranchItem> {
  private String myUrl;
  private Date myCreationDate;
  private long myRevision;

  public SvnBranchItem(final String url, final Date creationDate, final long revision) {
    myUrl = url;
    myCreationDate = creationDate;
    myRevision = revision;
  }

  public String getUrl() {
    return myUrl;
  }

  @Nullable
  public Date getCreationDate() {
    return myCreationDate;
  }

  public long getRevision() {
    return myRevision;
  }

  public int compareTo(SvnBranchItem o) {
    return -Comparing.compare(myCreationDate, o.getCreationDate());
  }
}
