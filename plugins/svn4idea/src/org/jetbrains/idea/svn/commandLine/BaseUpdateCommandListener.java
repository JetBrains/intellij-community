package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Konstantin Kolosovsky.
 */
public class BaseUpdateCommandListener extends LineCommandListener {

  @NotNull
  private final UpdateOutputLineConverter converter;

  @NotNull
  private final ISVNEventHandler handler;

  @NotNull
  private final AtomicReference<SVNException> exception;

  public BaseUpdateCommandListener(@NotNull File base, @NotNull ISVNEventHandler handler) {
    this.handler = handler;
    this.converter = new UpdateOutputLineConverter(base);
    exception = new AtomicReference<SVNException>();
  }

  @Override
  public void baseDirectory(File file) {
  }

  @Override
  public void onLineAvailable(String line, Key outputType) {
    if (ProcessOutputTypes.STDOUT.equals(outputType)) {
      final SVNEvent event = converter.convert(line);
      if (event != null) {
        beforeHandler(event);
        try {
          handler.handleEvent(event, 0.5);
        }
        catch (SVNException e) {
          cancel();
          exception.set(e);
        }
      }
    }
  }

  public void throwIfException() throws SVNException {
    SVNException e = exception.get();

    if (e != null) {
      throw e;
    }
  }

  public void throwWrappedIfException() throws VcsException {
    SVNException e = exception.get();

    if (e != null) {
      throw new VcsException(e);
    }
  }

  protected void beforeHandler(@NotNull SVNEvent event) {
  }
}
