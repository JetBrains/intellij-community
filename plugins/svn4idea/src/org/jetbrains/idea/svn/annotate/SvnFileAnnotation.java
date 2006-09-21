/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.annotate.AnnotationListener;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.text.SyncDateFormat;
import org.jetbrains.idea.svn.SvnEntriesListener;
import org.jetbrains.idea.svn.SvnVcs;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class SvnFileAnnotation implements FileAnnotation {
  private final StringBuffer myContentBuffer = new StringBuffer();
  private final List<LineInfo> myLineInfos = new ArrayList<LineInfo>();
  private static final SyncDateFormat DATE_FORMAT = new SyncDateFormat(SimpleDateFormat.getDateInstance(SimpleDateFormat.SHORT));

  private final SvnVcs myVcs;


  private final List<AnnotationListener> myListeners = new ArrayList<AnnotationListener>();

  private final LineAnnotationAspect DATE_ASPECT = new LineAnnotationAspect() {
    public String getValue(int lineNumber) {
      if (myLineInfos.size() <= lineNumber || lineNumber < 0) {
        return "";
      }
      else {
        return DATE_FORMAT.format(myLineInfos.get(lineNumber).getDate());
      }
    }
  };

  private final LineAnnotationAspect REVISION_ASPECT = new LineAnnotationAspect() {
    public String getValue(int lineNumber) {
      if (myLineInfos.size() <= lineNumber || lineNumber < 0) {
        return "";
      }
      else {
        return String.valueOf(myLineInfos.get(lineNumber).getRevision());
      }
    }
  };

  private final LineAnnotationAspect AUTHOR_ASPECT = new LineAnnotationAspect() {
    public String getValue(int lineNumber) {
      if (myLineInfos.size() <= lineNumber || lineNumber < 0) {
        return "";
      }
      else {
        return myLineInfos.get(lineNumber).getAuthor();
      }
    }
  };
  private final SvnEntriesListener myListener = new SvnEntriesListener() {
    public void onEntriesChanged(VirtualFile directory) {
      final AnnotationListener[] listeners = myListeners.toArray(new AnnotationListener[myListeners.size()]);
      for (int i = 0; i < listeners.length; i++) {
        listeners[i].onAnnotationChanged();
      }
    }
  };

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


  public SvnFileAnnotation(final SvnVcs vcs) {
    myVcs = vcs;
    myVcs.getSvnEntriesFileListener().addListener(myListener);
  }

  public void addListener(AnnotationListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(AnnotationListener listener) {
    myListeners.remove(listener);
  }

  public void dispose() {
    myVcs.getSvnEntriesFileListener().removeListener(myListener);
  }

  public LineAnnotationAspect[] getAspects() {
    return new LineAnnotationAspect[]{REVISION_ASPECT, DATE_ASPECT, AUTHOR_ASPECT};
  }

  public String getAnnotatedContent() {
    return myContentBuffer.toString();
  }

  public void appendLineInfo(final Date date, final long revision, final String author, final String line) {
    myLineInfos.add(new LineInfo(date, revision, author));
    myContentBuffer.append(line);
    myContentBuffer.append("\n");
  }
}
