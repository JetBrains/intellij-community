package org.jetbrains.idea.svn.content;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc2.SvnTarget;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Konstantin Kolosovsky.
 */
public class SvnKitContentClient extends BaseSvnClient implements ContentClient {

  @Override
  public byte[] getContent(@NotNull SvnTarget target, @Nullable SVNRevision revision, @Nullable SVNRevision pegRevision)
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
      if (target.isURL()) {
        wcClient.doGetFileContents(target.getURL(), pegRevision, revision, true, buffer);
      } else {
        wcClient.doGetFileContents(target.getFile(), pegRevision, revision, true, buffer);
      }
      ContentRevisionCache.checkContentsSize(target.getPathOrUrlString(), buffer.size());
    } catch (FileTooBigRuntimeException e) {
      ContentRevisionCache.checkContentsSize(target.getPathOrUrlString(), buffer.size());
    } catch (SVNException e) {
      throw new VcsException(e);
    }
    return buffer.toByteArray();
  }
}
