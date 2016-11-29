package org.jetbrains.idea.svn.commandLine;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.ProgressEvent;
import org.jetbrains.idea.svn.api.ProgressTracker;
import org.tmatesoft.svn.core.SVNException;

import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Konstantin Kolosovsky.
 */
public class BaseUpdateCommandListener extends LineCommandAdapter {

  @NotNull
  private final UpdateOutputLineConverter converter;

  @Nullable
  private final ProgressTracker handler;

  @NotNull
  private final AtomicReference<SVNException> exception;

  public BaseUpdateCommandListener(@NotNull File base, @Nullable ProgressTracker handler) {
    this.handler = handler;
    this.converter = new UpdateOutputLineConverter(base);
    exception = new AtomicReference<>();
  }

  @Override
  public void onLineAvailable(String line, Key outputType) {
    if (ProcessOutputTypes.STDOUT.equals(outputType)) {
      final ProgressEvent event = converter.convert(line);
      if (event != null) {
        beforeHandler(event);
        try {
          callHandler(event);
        }
        catch (SVNException e) {
          cancel();
          exception.set(e);
        }
      }
    }
  }

  private void callHandler(ProgressEvent event) throws SVNException {
    if (handler != null) {
      handler.consume(event);
    }
  }

  public void throwWrappedIfException() throws SvnBindException {
    SVNException e = exception.get();

    if (e != null) {
      throw new SvnBindException(e);
    }
  }

  protected void beforeHandler(@NotNull ProgressEvent event) {
  }
}
