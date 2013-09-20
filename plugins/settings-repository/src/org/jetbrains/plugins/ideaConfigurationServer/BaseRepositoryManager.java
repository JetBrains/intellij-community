package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.QueueProcessor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public abstract class BaseRepositoryManager implements RepositoryManager {
  protected static final Logger LOG = Logger.getInstance(BaseRepositoryManager.class);

  protected final File dir;

  // avoid FS recursive scan
  protected final Set<String> filesToAdd = new THashSet<String>();
  protected boolean someFilesWereRemoved;

  // application could be terminated incorrectly (force quit), so, we should ensure that modified files will be added to index on first commit after application start
  protected boolean isFirstCommitAfterApplicationStart = true;

  protected final QueueProcessor<ThrowableRunnable<Exception>> taskProcessor = new QueueProcessor<ThrowableRunnable<Exception>>(new Consumer<ThrowableRunnable<Exception>>() {
    @Override
    public void consume(ThrowableRunnable<Exception> task) {
      try {
        task.run();
      }
      catch (Throwable e) {
        try {
          LOG.error(e);
        }
        catch (Throwable e2) {
          //noinspection CallToPrintStackTrace
          e2.printStackTrace();
        }
      }
    }
  });

  protected BaseRepositoryManager() {
    dir = new File(IcsManager.getPluginSystemDir(), "repository");
  }

  @Override
  @Nullable
  public InputStream read(@NotNull String path) throws IOException {
    File file = new File(dir, path);
    //noinspection IOResourceOpenedButNotSafelyClosed
    return file.exists() ? new FileInputStream(file) : null;
  }

  @Override
  public void write(@NotNull final String path, @NotNull final byte[] content, final int size, boolean async) {
    synchronized (filesToAdd) {
      filesToAdd.add(path);
    }

    if (async) {
      taskProcessor.add(new ThrowableRunnable<Exception>() {
        @Override
        public void run() throws Exception {
          synchronized (filesToAdd) {
            if (!filesToAdd.contains(path)) {
              // delete was requested
              return;
            }
          }

          doWrite(path, content, size);
        }
      });
    }
    else {
      try {
        doWrite(path, content, size);
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  private void doWrite(String path, byte[] content, int size) throws IOException {
    FileUtil.writeToFile(new File(dir, path), content, 0, size);
  }

  @NotNull
  @Override
  public Collection<String> listSubFileNames(@NotNull String path) {
    File[] files = new File(dir, path).listFiles();
    if (files == null || files.length == 0) {
      return Collections.emptyList();
    }

    List<String> result = new ArrayList<String>(files.length);
    for (File file : files) {
      result.add(file.getName());
    }
    return result;
  }

  @Override
  public final void deleteAsync(@NotNull final String path) {
    synchronized (filesToAdd) {
      filesToAdd.remove(path);
    }

    taskProcessor.add(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        synchronized (filesToAdd) {
          if (filesToAdd.contains(path)) {
            // write was requested
            return;
          }
        }

        someFilesWereRemoved = true;
        doDelete(path);
      }
    });
  }

  protected abstract void doDelete(@NotNull String path) throws Exception;

  @Override
  public final void updateRepository() {
    taskProcessor.add(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        if (hasRemoteRepository()) {
          doUpdateRepository();
        }
      }
    });
  }

  protected abstract boolean hasRemoteRepository();

  protected abstract void doUpdateRepository() throws Exception;

  protected final ActionCallback execute(@NotNull final ThrowableConsumer<ProgressIndicator, Exception> task, @NotNull final ProgressIndicator indicator) {
    final ActionCallback callback = new ActionCallback();
    taskProcessor.add(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        try {
          task.consume(indicator);
          callback.setDone();
        }
        catch (Throwable e) {
          callback.reject(e.getMessage());
          LOG.error(e);
        }
      }
    });
    return callback;
  }
}