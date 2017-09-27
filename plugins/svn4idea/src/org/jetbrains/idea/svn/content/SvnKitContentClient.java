/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.content;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.api.Target;
import org.jetbrains.idea.svn.commandLine.SvnBindException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class SvnKitContentClient extends BaseSvnClient implements ContentClient {

  @Override
  public byte[] getContent(@NotNull Target target, @Nullable SVNRevision revision, @Nullable SVNRevision pegRevision)
    throws VcsException, FileTooBigRuntimeException {
    final int maxSize = VcsUtil.getMaxVcsLoadedFileSize();
    ByteArrayOutputStream buffer = new ByteArrayOutputStream() {
      @Override
      public synchronized void write(int b) {
        if (size() > maxSize) throw new FileTooBigRuntimeException();
        super.write(b);
      }

      @Override
      public synchronized void write(byte[] b, int off, int len) {
        if (size() > maxSize) throw new FileTooBigRuntimeException();
        super.write(b, off, len);
      }

      @Override
      public synchronized void writeTo(OutputStream out) throws IOException {
        if (size() > maxSize) throw new FileTooBigRuntimeException();
        super.writeTo(out);
      }
    };
    SVNWCClient wcClient = myVcs.getSvnKitManager().createWCClient();
    try {
      if (target.isUrl()) {
        wcClient.doGetFileContents(target.getUrl(), pegRevision, revision, true, buffer);
      } else {
        wcClient.doGetFileContents(target.getFile(), pegRevision, revision, true, buffer);
      }
      ContentRevisionCache.checkContentsSize(target.getPath(), buffer.size());
    } catch (FileTooBigRuntimeException e) {
      ContentRevisionCache.checkContentsSize(target.getPath(), buffer.size());
    } catch (SVNException e) {
      throw new SvnBindException(e);
    }
    return buffer.toByteArray();
  }
}
