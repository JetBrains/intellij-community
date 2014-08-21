package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public abstract class BaseRepositoryManager implements RepositoryManager {
  public static final Logger LOG = Logger.getInstance(BaseRepositoryManager.class);

  protected final File dir;

  protected final Object lock = new Object();

  protected BaseRepositoryManager() {
    dir = new File(IcsManager.getPluginSystemDir(), "repository");
  }

  @NotNull
  @Override
  public Collection<String> listSubFileNames(@NotNull String path) {
    String[] files = new File(dir, path).list();
    if (files == null || files.length == 0) {
      return Collections.emptyList();
    }
    return Arrays.asList(files);
  }

  @Override
  @Nullable
  public InputStream read(@NotNull String path) throws IOException {
    File file = new File(dir, path);
    //noinspection IOResourceOpenedButNotSafelyClosed
    return file.exists() ? new FileInputStream(file) : null;
  }

  @Override
  public void write(@NotNull final String path, @NotNull final byte[] content, final int size, final boolean async) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Write " + path);
    }

    try {
      synchronized (lock) {
        File file = new File(dir, path);
        FileUtil.writeToFile(file, content, 0, size);
        addToIndex(file, path);
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  /**
   * path relative to repository root
   */
  protected abstract void addToIndex(@NotNull File file, @NotNull String path) throws Exception;

  @Override
  public final void delete(@NotNull String path) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Remove " + path);
    }

    try {
      synchronized (lock) {
        File file = new File(dir, path);
        boolean isFile = file.isFile();
        FileUtil.delete(file);
        if (isFile) {
          // remove empty directories
          File parent = file.getParentFile();
          //noinspection FileEqualsUsage
          while (parent != null && !parent.equals(dir) && parent.delete()) {
            parent = parent.getParentFile();
          }
        }

        deleteFromIndex(path, isFile);
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  protected abstract void deleteFromIndex(@NotNull String path, boolean isFile) throws Exception;

  @Override
  public final void updateRepository(@NotNull ProgressIndicator indicator) throws Exception {
    if (hasUpstream()) {
      pull(indicator);
    }
  }

  @Override
  public boolean has(@NotNull String path) {
    return new File(dir, path).exists();
  }
}
