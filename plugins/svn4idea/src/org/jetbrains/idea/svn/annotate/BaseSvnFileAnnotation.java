// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.history.SvnFileRevision;

import java.util.*;

public abstract class BaseSvnFileAnnotation extends FileAnnotation {
  protected final String myContents;
  protected final VcsRevisionNumber myBaseRevision;
  private final MyPartiallyCreatedInfos myInfos;

  protected final SvnVcs myVcs;
  private final Map<Long, SvnFileRevision> myRevisionMap = new HashMap<>();

  private final LineAnnotationAspect DATE_ASPECT = new SvnAnnotationAspect(LineAnnotationAspect.DATE, true) {

    @Override
    public String getValue(@NotNull CommitInfo info) {
      return DateFormatUtil.formatPrettyDate(info.getDate());
    }
  };

  private final LineAnnotationAspect REVISION_ASPECT = new SvnAnnotationAspect(LineAnnotationAspect.REVISION, false) {

    @Override
    public String getValue(@NotNull CommitInfo info) {
      return String.valueOf(info.getRevision());
    }
  };

  private final LineAnnotationAspect ORIGINAL_REVISION_ASPECT = new SvnAnnotationAspect(SvnBundle.message("annotation.original.revision"), false) {
    @Override
    public String getValue(int lineNumber) {
      final long value = myInfos.originalRevision(lineNumber);
      return (value == -1) ? "" : String.valueOf(value);
    }

    @Override
    protected long getRevision(int lineNum) {
      return myInfos.originalRevision(lineNum);
    }

    @Override
    public String getTooltipText(int lineNumber) {
      // TODO: Check what is the difference in returning "" or null
      if (!myInfos.isValid(lineNumber)) return "";

      CommitInfo info = myInfos.get(lineNumber);
      if (info == null) return null;

      SvnFileRevision revision = myRevisionMap.get(info.getRevision());
      return revision != null ? XmlStringUtil.escapeString("Revision " + info.getRevision() + ": " + revision.getCommitMessage()) : "";
    }
  };

  private final LineAnnotationAspect AUTHOR_ASPECT = new SvnAnnotationAspect(LineAnnotationAspect.AUTHOR, true) {

    @Override
    public String getValue(@NotNull CommitInfo info) {
      return info.getAuthor();
    }
  };

  private final SvnConfiguration myConfiguration;
  private final boolean myShowMergeSources;
  // null if full annotation
  private SvnRevisionNumber myFirstRevisionNumber;

  public void setRevision(final long revision, final SvnFileRevision svnRevision) {
    myRevisionMap.put(revision, svnRevision);
  }

  public SvnFileRevision getRevision(final long revision) {
    return myRevisionMap.get(revision);
  }

  public void setFirstRevision(Revision revision) {
    myFirstRevisionNumber = new SvnRevisionNumber(revision);
  }

  public SvnRevisionNumber getFirstRevisionNumber() {
    return myFirstRevisionNumber;
  }

  public BaseSvnFileAnnotation(@NotNull SvnVcs vcs, final String contents, final VcsRevisionNumber baseRevision) {
    super(vcs.getProject());
    myVcs = vcs;
    myContents = contents;
    myBaseRevision = baseRevision;
    myConfiguration = vcs.getSvnConfiguration();
    myShowMergeSources = myConfiguration.isShowMergeSourcesInAnnotate();

    myInfos = new MyPartiallyCreatedInfos();
  }

  @Override
  public LineAnnotationAspect[] getAspects() {
    return new LineAnnotationAspect[]{REVISION_ASPECT, DATE_ASPECT, AUTHOR_ASPECT};
  }

  @Override
  public String getToolTip(final int lineNumber) {
    final CommitInfo info = myInfos.getOrNull(lineNumber);
    if (info == null) return "";

    SvnFileRevision revision = myRevisionMap.get(info.getRevision());
    if (revision != null) {
      String prefix = myInfos.getAnnotationSource(lineNumber).showMerged() ? "Merge source revision" : "Revision";

      return prefix + " " + info.getRevision() + ": " + revision.getCommitMessage();
    }
    return "";
  }

  @Override
  public String getAnnotatedContent() {
    return myContents;
  }

  public void setLineInfo(int lineNumber, @NotNull CommitInfo info, @Nullable CommitInfo mergeInfo) {
    myInfos.appendNumberedLineInfo(lineNumber, info, mergeInfo);
  }

  @Override
  @Nullable
  public VcsRevisionNumber originalRevision(final int lineNumber) {
    SvnFileRevision revision = myInfos.isValid(lineNumber) ? myRevisionMap.get(myInfos.originalRevision(lineNumber)) : null;

    return revision != null ? revision.getRevisionNumber() : null;
  }

  @Override
  public VcsRevisionNumber getLineRevisionNumber(final int lineNumber) {
    CommitInfo info = myInfos.getOrNull(lineNumber);

    return info != null && info.getRevision() >= 0 ? new SvnRevisionNumber(Revision.of(info.getRevision())) : null;
  }

  @Override
  public Date getLineDate(int lineNumber) {
    CommitInfo info = myInfos.getOrNull(lineNumber);

    return info != null ? info.getDate() : null;
  }

  @Override
  public List<VcsFileRevision> getRevisions() {
    final List<VcsFileRevision> result = new ArrayList<>(myRevisionMap.values());
    Collections.sort(result, (o1, o2) -> o2.getRevisionNumber().compareTo(o1.getRevisionNumber()));
    return result;
  }

  @Override
  @Nullable
  public AnnotationSourceSwitcher getAnnotationSourceSwitcher() {
    if (! myShowMergeSources) return null;
    return new AnnotationSourceSwitcher() {
      @Override
      @NotNull
      public AnnotationSource getAnnotationSource(int lineNumber) {
        return myInfos.getAnnotationSource(lineNumber);
      }

      @Override
      public boolean mergeSourceAvailable(int lineNumber) {
        return myInfos.mergeSourceAvailable(lineNumber);
      }

      @Override
      @NotNull
      public LineAnnotationAspect getRevisionAspect() {
        return ORIGINAL_REVISION_ASPECT;
      }

      @Override
      @NotNull
      public AnnotationSource getDefaultSource() {
        return AnnotationSource.getInstance(myShowMergeSources);
      }

      @Override
      public void switchTo(AnnotationSource source) {
        myInfos.setShowMergeSource(source.showMerged());
      }
    };
  }

  @Override
  public int getLineCount() {
    return myInfos.size();
  }

  @Override
  public VcsKey getVcsKey() {
    return SvnVcs.getKey();
  }

  private abstract class SvnAnnotationAspect extends LineAnnotationAspectAdapter {

    public SvnAnnotationAspect(String id, boolean showByDefault) {
      super(id, showByDefault);
    }

    protected long getRevision(final int lineNum) {
      final CommitInfo lineInfo = myInfos.get(lineNum);
      return (lineInfo == null) ? -1 : lineInfo.getRevision();
    }

    @Override
    protected void showAffectedPaths(int lineNum) {
      if (myInfos.isValid(lineNum)) {
        final long revision = getRevision(lineNum);
        if (revision >= 0) {
          showAllAffectedPaths(new SvnRevisionNumber(Revision.of(revision)));
        }
      }
    }

    @Override
    public String getValue(int lineNumber) {
      CommitInfo info = myInfos.getOrNull(lineNumber);

      return info == null ? "" : getValue(info);
    }

    public String getValue(@NotNull CommitInfo info) {
      return "";
    }
  }

  protected abstract void showAllAffectedPaths(SvnRevisionNumber number);

  private static class MyPartiallyCreatedInfos {
    private boolean myShowMergeSource;
    private final Map<Integer, CommitInfo> myMappedLineInfo;
    private final Map<Integer, CommitInfo> myMergeSourceInfos;
    private int myMaxIdx;

    private MyPartiallyCreatedInfos() {
      myMergeSourceInfos = ContainerUtil.newHashMap();
      myMappedLineInfo = ContainerUtil.newHashMap();
      myMaxIdx = 0;
    }

    void setShowMergeSource(boolean showMergeSource) {
      myShowMergeSource = showMergeSource;
    }

    int size() {
      return myMaxIdx + 1;
    }

    void appendNumberedLineInfo(final int lineNumber, @NotNull CommitInfo info, @Nullable CommitInfo mergeInfo) {
      if (myMappedLineInfo.get(lineNumber) != null) return;
      myMaxIdx = (myMaxIdx < lineNumber) ? lineNumber : myMaxIdx;
      myMappedLineInfo.put(lineNumber, info);
      if (mergeInfo != null) {
        myMergeSourceInfos.put(lineNumber, mergeInfo);
      }
    }

    CommitInfo get(final int idx) {
      if (myShowMergeSource) {
        final CommitInfo lineInfo = myMergeSourceInfos.get(idx);
        if (lineInfo != null) {
          return lineInfo;
        }
      }
      return myMappedLineInfo.get(idx);
    }

    @Nullable
    CommitInfo getOrNull(int lineNumber) {
      return isValid(lineNumber) ? get(lineNumber) : null;
    }

    private boolean isValid(int lineNumber) {
      return lineNumber >= 0 && lineNumber < size();
    }

    AnnotationSource getAnnotationSource(final int line) {
      return myShowMergeSource ? AnnotationSource.getInstance(myMergeSourceInfos.containsKey(line)) : AnnotationSource.LOCAL;
    }

    public long originalRevision(final int line) {
      CommitInfo info = line < size() ? myMappedLineInfo.get(line) : null;

      return info == null ? -1 : info.getRevision();
    }

    public boolean mergeSourceAvailable(int lineNumber) {
      return myMergeSourceInfos.containsKey(lineNumber);
    }
  }

  @Nullable
  @Override
  public VcsRevisionNumber getCurrentRevision() {
    return myBaseRevision;
  }
}
