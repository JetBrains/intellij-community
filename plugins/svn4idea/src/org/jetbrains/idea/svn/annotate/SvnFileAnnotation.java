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
package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.*;
import org.jetbrains.idea.svn.history.SvnFileRevision;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.*;

public class SvnFileAnnotation implements FileAnnotation {
  private final String myContents;
  private final VcsRevisionNumber myBaseRevision;
  private final MyPartiallyCreatedInfos myInfos;

  private final SvnVcs myVcs;
  private final VirtualFile myFile;
  private final List<AnnotationListener> myListeners = new ArrayList<AnnotationListener>();
  private final Map<Long, SvnFileRevision> myRevisionMap = new HashMap<Long, SvnFileRevision>();

  private final LineAnnotationAspect DATE_ASPECT = new SvnAnnotationAspect(SvnAnnotationAspect.DATE, true) {
    public String getValue(int lineNumber) {
      if (myInfos.size() <= lineNumber || lineNumber < 0) {
        return "";
      }
      else {
        final LineInfo lineInfo = myInfos.get(lineNumber);
        return (lineInfo == null) ? "" : DateFormatUtil.formatPrettyDate(lineInfo.getDate());
      }
    }
  };

  private final LineAnnotationAspect REVISION_ASPECT = new SvnAnnotationAspect(SvnAnnotationAspect.REVISION, false) {
    public String getValue(int lineNumber) {
      if (myInfos.size() <= lineNumber || lineNumber < 0) {
        return "";
      }
      else {
        final long revision = getRevision(lineNumber);
        return (revision == -1) ? "" : String.valueOf(revision);
      }
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
      if (myInfos.size() <= lineNumber || lineNumber < 0) {
        return "";
      }
      final LineInfo info = myInfos.get(lineNumber);
      if (info == null) return null;
      SvnFileRevision svnRevision = myRevisionMap.get(info.getRevision());
      if (svnRevision != null) {
        final String tooltip = "Revision " + info.getRevision() + ": " + svnRevision.getCommitMessage();
        return XmlStringUtil.escapeString(tooltip);
      }
      return "";
    }
  };

  private final LineAnnotationAspect AUTHOR_ASPECT = new SvnAnnotationAspect(SvnAnnotationAspect.AUTHOR, true) {
    public String getValue(int lineNumber) {
      if (myInfos.size() <= lineNumber || lineNumber < 0) {
        return "";
      }
      else {
        final LineInfo lineInfo = myInfos.get(lineNumber);
        return (lineInfo == null) ? "" : lineInfo.getAuthor();
      }
    }
  };
  private final SvnEntriesListener myListener = new SvnEntriesListener() {
    public void onEntriesChanged(VirtualFile directory) {
      if (directory != myFile.getParent()) return;
      final VcsRevisionNumber currentRevision = myVcs.getDiffProvider().getCurrentRevision(myFile);
      if (currentRevision != null && currentRevision.equals(myBaseRevision)) return;

      final AnnotationListener[] listeners = myListeners.toArray(new AnnotationListener[myListeners.size()]);
      for (int i = 0; i < listeners.length; i++) {
        listeners[i].onAnnotationChanged();
      }
    }
  };
  private final SvnConfiguration myConfiguration;
  private boolean myShowMergeSources;
  // null if full annotation
  private SvnRevisionNumber myFirstRevisionNumber;

  public void setRevision(final long revision, final SvnFileRevision svnRevision) {
    myRevisionMap.put(revision, svnRevision);
  }

  public void clearRevisions() {
    myRevisionMap.clear();
  }

  public SvnFileRevision getRevision(final long revision) {
    return myRevisionMap.get(revision);
  }

  public void setFirstRevision(SVNRevision svnRevision) {
    myFirstRevisionNumber = new SvnRevisionNumber(svnRevision);
  }

  public SvnRevisionNumber getFirstRevisionNumber() {
    return myFirstRevisionNumber;
  }

  static class LineInfo {
    private final Date myDate;
    private final long myRevision;
    private final String myAuthor;

    public LineInfo(final Date date, final long revision, final String author) {
      myDate = date;
      myRevision = revision;
      myAuthor = author;
    }

    public Date getDate() {
      return myDate;
    }

    public long getRevision() {
      return myRevision;
    }

    public String getAuthor() {
      return myAuthor;
    }
  }

  public SvnFileAnnotation(final SvnVcs vcs, final VirtualFile file, final String contents, final VcsRevisionNumber baseRevision) {
    myVcs = vcs;
    myFile = file;
    myContents = contents;
    myBaseRevision = baseRevision;
    myVcs.getSvnEntriesFileListener().addListener(myListener);
    myConfiguration = SvnConfiguration.getInstance(vcs.getProject());
    myShowMergeSources = myConfiguration.SHOW_MERGE_SOURCES_IN_ANNOTATE;

    myInfos = new MyPartiallyCreatedInfos();
  }

  public void addListener(AnnotationListener listener) {
    myListeners.add(listener);
    myConfiguration.addAnnotationListener(listener);
  }

  public void removeListener(AnnotationListener listener) {
    myListeners.remove(listener);
    myConfiguration.removeAnnotationListener(listener);
  }

  public void dispose() {
    myVcs.getSvnEntriesFileListener().removeListener(myListener);
  }

  public LineAnnotationAspect[] getAspects() {
    return new LineAnnotationAspect[]{REVISION_ASPECT, DATE_ASPECT, AUTHOR_ASPECT};
  }

  public String getToolTip(final int lineNumber) {
    if (myInfos.size() <= lineNumber || lineNumber < 0) {
      return "";
    }
    final LineInfo info = myInfos.get(lineNumber);
    if (info == null) return "";
    SvnFileRevision svnRevision = myRevisionMap.get(info.getRevision());
    if (svnRevision != null) {
      if (myInfos.getAnnotationSource(lineNumber).showMerged()) {
        return "Merge source revision " + info.getRevision() + ": " + svnRevision.getCommitMessage();
      } else {
        return "Revision " + info.getRevision() + ": " + svnRevision.getCommitMessage();
      }
    }
    return "";
  }

  public String getAnnotatedContent() {
    return myContents;
  }

  public void setLineInfo(final int lineNumber, final Date date, final long revision, final String author,
                             @Nullable final Date mergeDate, final long mergeRevision, @Nullable final String mergeAuthor) {
    myInfos.appendNumberedLineInfo(lineNumber, date, revision, author, mergeDate, mergeRevision, mergeAuthor);
  }

  public void appendLineInfo(final Date date, final long revision, final String author,
                             @Nullable final Date mergeDate, final long mergeRevision, @Nullable final String mergeAuthor) {
    myInfos.appendNumberedLineInfo(date, revision, author, mergeDate, mergeRevision, mergeAuthor);
  }

  @Nullable
  public VcsRevisionNumber originalRevision(final int lineNumber) {
    if (myInfos.size() <= lineNumber || lineNumber < 0) {
      return null;
    }
    final SvnFileRevision revision = myRevisionMap.get(myInfos.originalRevision(lineNumber));
    return revision == null ? null : revision.getRevisionNumber();
  }

  public VcsRevisionNumber getLineRevisionNumber(final int lineNumber) {
    if (myInfos.size() <= lineNumber || lineNumber < 0) {
      return null;
    }
    final LineInfo info = myInfos.get(lineNumber);
    if (info == null) return null;
    final long revision = info.getRevision();
    if (revision >= 0) {
      return new SvnRevisionNumber(SVNRevision.create(revision));
    }
    return null;
  }

  public List<VcsFileRevision> getRevisions() {
    final List<VcsFileRevision> result = new ArrayList<VcsFileRevision>(myRevisionMap.values());
    Collections.sort(result, new Comparator<VcsFileRevision>() {
      public int compare(final VcsFileRevision o1, final VcsFileRevision o2) {
        return -1 * o1.getRevisionNumber().compareTo(o2.getRevisionNumber());
      }
    });
    return result;
  }

  public boolean revisionsNotEmpty() {
    return ! myRevisionMap.isEmpty();
  }

  public VirtualFile getFile() {
    return myFile;
  }

  public int getNumLines() {
    return myInfos.size();
  }

  @Nullable
  public AnnotationSourceSwitcher getAnnotationSourceSwitcher() {
    if (! myShowMergeSources) return null;
    return new AnnotationSourceSwitcher() {
      @NotNull
      public AnnotationSource getAnnotationSource(int lineNumber) {
        return myInfos.getAnnotationSource(lineNumber);
      }

      public boolean mergeSourceAvailable(int lineNumber) {
        return myInfos.mergeSourceAvailable(lineNumber);
      }

      @NotNull
      public LineAnnotationAspect getRevisionAspect() {
        return ORIGINAL_REVISION_ASPECT;
      }

      @NotNull
      public AnnotationSource getDefaultSource() {
        return AnnotationSource.getInstance(myShowMergeSources);
      }

      public void switchTo(AnnotationSource source) {
        myInfos.setShowMergeSource(source.showMerged());
      }
    };
  }

  private abstract class SvnAnnotationAspect extends LineAnnotationAspectAdapter {
    public SvnAnnotationAspect(String id, boolean showByDefault) {
      super(id, showByDefault);
    }

    protected long getRevision(final int lineNum) {
      final LineInfo lineInfo = myInfos.get(lineNum);
      return (lineInfo == null) ? -1 : lineInfo.getRevision();
    }

    @Override
    protected void showAffectedPaths(int lineNum) {
      if (lineNum >= 0 && lineNum < myInfos.size()) {
        final long revision = getRevision(lineNum);
        if (revision >= 0) {
          ShowAllAffectedGenericAction.showSubmittedFiles(myVcs.getProject(), new SvnRevisionNumber(SVNRevision.create(revision)),
                                                          myFile, myVcs.getKeyInstanceMethod());
        }
      }
    }
  }

  private static class MyPartiallyCreatedInfos {
    private boolean myShowMergeSource;
    private final Map<Integer, LineInfo> myMappedLineInfo;
    private final Map<Integer, LineInfo> myMergeSourceInfos;
    private int myMaxIdx;

    private MyPartiallyCreatedInfos() {
      myMergeSourceInfos = new HashMap<Integer, LineInfo>();
      myMappedLineInfo = new HashMap<Integer, LineInfo>();
      myMaxIdx = 0;
    }

    boolean isShowMergeSource() {
      return myShowMergeSource;
    }

    void setShowMergeSource(boolean showMergeSource) {
      myShowMergeSource = showMergeSource;
    }

    int size() {
      return myMaxIdx + 1;
    }

    void appendNumberedLineInfo(final Date date, final long revision, final String author,
                               @Nullable final Date mergeDate, final long mergeRevision, @Nullable final String mergeAuthor) {
      appendNumberedLineInfo(myMaxIdx + 1, date, revision, author, mergeDate, mergeRevision, mergeAuthor);
    }

    void appendNumberedLineInfo(final int lineNumber, final Date date, final long revision, final String author,
                               @Nullable final Date mergeDate, final long mergeRevision, @Nullable final String mergeAuthor) {
      if (date == null) return;
      if (myMappedLineInfo.get(lineNumber) != null) return;
      myMaxIdx = (myMaxIdx < lineNumber) ? lineNumber : myMaxIdx;
      myMappedLineInfo.put(lineNumber, new LineInfo(date, revision, author));
      if (mergeDate != null) {
        myMergeSourceInfos.put(lineNumber, new LineInfo(mergeDate, mergeRevision, mergeAuthor));
      }
    }

    LineInfo get(final int idx) {
      if (myShowMergeSource) {
        final LineInfo lineInfo = myMergeSourceInfos.get(idx);
        if (lineInfo != null) {
          return lineInfo;
        }
      }
      return myMappedLineInfo.get(idx);
    }

    AnnotationSource getAnnotationSource(final int line) {
      return myShowMergeSource ? AnnotationSource.getInstance(myMergeSourceInfos.containsKey(line)) : AnnotationSource.LOCAL;
    }

    public long originalRevision(final int line) {
      if (line >= size()) return -1;
      final LineInfo lineInfo = myMappedLineInfo.get(line);
      return lineInfo == null ? -1 : lineInfo.getRevision();
    }

    public boolean mergeSourceAvailable(int lineNumber) {
      return myMergeSourceInfos.containsKey(lineNumber);
    }
  }
}
