/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.history;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.MarkerVcsContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.actions.AbstractShowPropertiesDiffAction;
import org.jetbrains.idea.svn.properties.PropertyData;
import org.tmatesoft.svn.core.SVNURL;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/22/12
 * Time: 10:28 AM
 */
public class SvnLazyPropertyContentRevision implements ContentRevision, MarkerVcsContentRevision, PropertyRevision {
  private final FilePath myPath;
  private final VcsRevisionNumber myNumber;
  private final Project myProject;
  private final SVNURL myUrl;
  private List<PropertyData> myContent;

  public SvnLazyPropertyContentRevision(FilePath path, VcsRevisionNumber number, Project project, SVNURL url) {
    myPath = path;
    myNumber = number;
    myProject = project;
    myUrl = url;
  }

  @Nullable
  @Override
  public List<PropertyData> getProperties() throws VcsException {
    if (myContent == null) {
      myContent = loadContent();
    }
    return myContent;
  }

  @Override
  public String getContent() throws VcsException {
    return AbstractShowPropertiesDiffAction.toSortedStringPresentation(getProperties());
  }

  private List<PropertyData> loadContent() throws VcsException {
    final SvnVcs vcs = SvnVcs.getInstance(myProject);
    final Ref<List<PropertyData>> ref = new Ref<>();
    final Ref<VcsException> exceptionRef = new Ref<>();
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        try {
          ref.set(AbstractShowPropertiesDiffAction.getPropertyList(vcs, myUrl, ((SvnRevisionNumber)myNumber).getRevision()));
        }
        catch (VcsException e) {
          exceptionRef.set(e);
        }
      }
    };
    if (ApplicationManager.getApplication().isDispatchThread()) {
      final boolean completed = ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(runnable, SvnBundle.message("progress.title.loading.file.properties"), true, myProject);
      if (!completed) {
        throw new VcsException("Properties load for revision " + getRevisionNumber().asString() + " was canceled.");
      }
    }
    else {
      runnable.run();
    }
    if (!exceptionRef.isNull()) throw exceptionRef.get();
    return ref.get();
  }

  @NotNull
  @Override
  public FilePath getFile() {
    return myPath;
  }

  @NotNull
  @Override
  public VcsRevisionNumber getRevisionNumber() {
    return myNumber;
  }

  @Override
  public VcsKey getVcsKey() {
    return SvnVcs.getKey();
  }
}
