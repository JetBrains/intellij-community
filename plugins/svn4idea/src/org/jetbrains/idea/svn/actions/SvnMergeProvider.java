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
package org.jetbrains.idea.svn.actions;

import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.merge.MergeData;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.SVNException;

import java.io.*;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Jul 29, 2005
 * Time: 1:12:23 AM
 * To change this template use File | Settings | File Templates.
 */
public class SvnMergeProvider implements MergeProvider {

  private final Project myProject;

  public SvnMergeProvider(final Project project) {
    myProject = project;
  }

  @NotNull
  public MergeData loadRevisions(VirtualFile file) throws VcsException {
    MergeData data = new MergeData();
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    File oldFile = null;
    File newFile = null;
    File workingFile = null;
    SVNWCClient client;
    try {
      client = vcs.createWCClient();
      SVNInfo info = client.doInfo(new File(file.getPath()), SVNRevision.WORKING);
      if (info != null) {
        oldFile = info.getConflictOldFile();
        newFile = info.getConflictNewFile();
        workingFile = info.getConflictWrkFile();
      }
    }
    catch (SVNException e) {
      throw new VcsException(e);
    }
    if (oldFile == null || newFile == null || workingFile == null) {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try {
        client.doGetFileContents(new File(file.getPath()), SVNRevision.UNDEFINED, SVNRevision.BASE, true, bos);
      }
      catch (SVNException e) {
        //
      }
      data.ORIGINAL = bos.toByteArray();
      data.LAST = bos.toByteArray();
      data.CURRENT = readFile(new File(file.getPath()));
    }
    else {
      data.ORIGINAL = readFile(oldFile);
      data.LAST = readFile(newFile);
      data.CURRENT = readFile(workingFile);
    }

    return data;
  }

  private byte[] readFile(File workingFile) {
    if (workingFile == null) {
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    InputStream is = null;
    try {
      is = new BufferedInputStream(new FileInputStream(workingFile));
      int r;
      while ((r = is.read()) >= 0) {
        bos.write(r);
      }
      bos.close();
    }
    catch (FileNotFoundException e) {
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }
    catch (IOException e) {
      return ArrayUtil.EMPTY_BYTE_ARRAY;
    }
    finally {
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {
          //
        }
      }
    }
    return bos.toByteArray();
  }

  public void conflictResolvedForFile(VirtualFile file) {
    SvnVcs vcs = SvnVcs.getInstance(myProject);
    try {
      SVNWCClient client = vcs.createWCClient();
      client.doResolve(new File(file.getPath()), false);
    }
    catch (SVNException e) {
      //
    }
    // the .mine/.r## files have been deleted
    final VirtualFile parent = file.getParent();
    if (parent != null) {
      parent.refresh(true, false);
    }
  }

}
