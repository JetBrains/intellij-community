package org.jetbrains.plugins.ideaConfigurationServer;

import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class BaseRepositoryManager implements RepositoryManager {
  protected final File dir;

  // avoid FS recursive scan
  protected final Set<String> filesToAdd = new THashSet<String>();

  protected BaseRepositoryManager(File dir) {
    this.dir = dir;
  }

  @Override
  @Nullable
  public InputStream read(@NotNull String path) throws IOException {
    File file = new File(dir, path);
    //noinspection IOResourceOpenedButNotSafelyClosed
    return file.exists() ? new FileInputStream(file) : null;
  }

  @Override
  public void write(@NotNull String path, @NotNull InputStream content, long size) throws IOException {
    File file = new File(dir, path);
    FileOutputStream out = new FileOutputStream(file);
    try {
      FileUtilRt.copy(content, out);
      synchronized (filesToAdd) {
        filesToAdd.add(path);
      }
    }
    finally {
      out.close();
    }
  }

  @NotNull
  @Override
  public String[] listSubFileNames(@NotNull String path) {
    File[] files = new File(path, path).listFiles();
    if (files == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    List<String> result = new ArrayList<String>(files.length);
    for (File file : files) {
      result.add(file.getName());
    }
    return ArrayUtil.toStringArray(result);
  }

  @Override
  public final void delete(@NotNull String path) throws IOException {
    synchronized (filesToAdd) {
      filesToAdd.remove(path);
    }
    doDelete(path);
  }

  protected abstract void doDelete(@NotNull String path) throws IOException;
}