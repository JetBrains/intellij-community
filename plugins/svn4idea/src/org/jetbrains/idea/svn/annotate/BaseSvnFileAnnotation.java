// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.checkin.CommitInfo;
import org.jetbrains.idea.svn.history.SvnFileRevision;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.intellij.openapi.vcs.annotate.AnnotationTooltipBuilder.buildSimpleTooltip;
import static com.intellij.xml.util.XmlStringUtil.escapeString;

public abstract class BaseSvnFileAnnotation extends FileAnnotation {
  private final String myContents;
  protected final VcsRevisionNumber myBaseRevision;
  private final MyPartiallyCreatedInfos myInfos;

  protected final SvnVcs myVcs;
  private final Long2ObjectMap<SvnFileRevision> myRevisionMap = new Long2ObjectOpenHashMap<>();

  private final LineAnnotationAspect DATE_ASPECT =
    new SvnAnnotationAspect(LineAnnotationAspect.DATE, VcsBundle.message("line.annotation.aspect.date"), true) {
      @Override
      public String getValue(@NotNull CommitInfo info) {
        return FileAnnotation.formatDate(info.getDate());
      }
    };

  private final LineAnnotationAspect REVISION_ASPECT =
    new SvnAnnotationAspect(LineAnnotationAspect.REVISION, VcsBundle.message("line.annotation.aspect.revision"), false) {
      @Override
      public String getValue(@NotNull CommitInfo info) {
        return String.valueOf(info.getRevisionNumber());
      }
    };

  private final LineAnnotationAspect ORIGINAL_REVISION_ASPECT =
    new SvnAnnotationAspect("Original revision", SvnBundle.message("annotation.original.revision"), false) {
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

        SvnFileRevision revision = myRevisionMap.get(info.getRevisionNumber());
        if (revision == null) return "";

        return escapeString(SvnBundle.message("tooltip.revision.number.message", info.getRevisionNumber(), revision.getCommitMessage()));
      }
    };

  private final LineAnnotationAspect AUTHOR_ASPECT =
    new SvnAnnotationAspect(LineAnnotationAspect.AUTHOR, VcsBundle.message("line.annotation.aspect.author"), true) {
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
  public LineAnnotationAspect @NotNull [] getAspects() {
    return new LineAnnotationAspect[]{REVISION_ASPECT, DATE_ASPECT, AUTHOR_ASPECT};
  }

  @Nullable
  @Override
  public String getToolTip(int lineNumber) {
    return getToolTip(lineNumber, false);
  }

  @Nullable
  @Override
  public String getHtmlToolTip(int lineNumber) {
    return getToolTip(lineNumber, true);
  }

  private @NlsContexts.Tooltip @Nullable String getToolTip(int lineNumber, boolean asHtml) {
    final CommitInfo info = myInfos.getOrNull(lineNumber);
    if (info == null) return null;

    SvnFileRevision revision = myRevisionMap.get(info.getRevisionNumber());
    if (revision == null) return null;

    String prefix = myInfos.getAnnotationSource(lineNumber).showMerged()
                    ? SvnBundle.message("label.merge.source.revision")
                    : SvnBundle.message("label.revision");
    return buildSimpleTooltip(getProject(), asHtml, prefix, String.valueOf(info.getRevisionNumber()), revision.getCommitMessage());
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

    return info != null && info.getRevisionNumber() >= 0 ? new SvnRevisionNumber(Revision.of(info.getRevisionNumber())) : null;
  }

  @Override
  public Date getLineDate(int lineNumber) {
    CommitInfo info = myInfos.getOrNull(lineNumber);

    return info != null ? info.getDate() : null;
  }

  @Override
  public List<VcsFileRevision> getRevisions() {
    final List<VcsFileRevision> result = new ArrayList<>(myRevisionMap.values());
    result.sort((o1, o2) -> o2.getRevisionNumber().compareTo(o1.getRevisionNumber()));
    return result;
  }

  @Override
  @Nullable
  public AnnotationSourceSwitcher getAnnotationSourceSwitcher() {
    if (!myShowMergeSources) return null;
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
    SvnAnnotationAspect(@NonNls String id, @NlsContexts.ListItem String displayName, boolean showByDefault) {
      super(id, displayName, showByDefault);
    }

    protected long getRevision(final int lineNum) {
      final CommitInfo lineInfo = myInfos.get(lineNum);
      return (lineInfo == null) ? -1 : lineInfo.getRevisionNumber();
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

  private static final class MyPartiallyCreatedInfos {
    private boolean myShowMergeSource;
    private final Int2ObjectMap<CommitInfo> myMappedLineInfo;
    private final Int2ObjectMap<CommitInfo> myMergeSourceInfos;
    private int myMaxIdx;

    private MyPartiallyCreatedInfos() {
      myMergeSourceInfos = new Int2ObjectOpenHashMap<>();
      myMappedLineInfo = new Int2ObjectOpenHashMap<>();
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
      myMaxIdx = Math.max(myMaxIdx, lineNumber);
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

      return info == null ? -1 : info.getRevisionNumber();
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

  @Nullable
  @Override
  public LineModificationDetailsProvider getLineModificationDetailsProvider() {
    return DefaultLineModificationDetailsProvider.create(this);
  }
}
