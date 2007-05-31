package org.jetbrains.idea.maven.core.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Vladislav.Kaznacheev
 */
public class FileFinder {
  public static Collection<VirtualFile> findFilesByPaths(final Collection<String> paths, final Collection<String> notFound) {
    final ArrayList<VirtualFile> found = new ArrayList<VirtualFile>();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        for (String path : paths) {
          VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
          if (file != null) {
            found.add(file);
          }
          else {
            notFound.add(path);
          }
        }
      }
    });
    return found;
  }

  public static Collection<VirtualFile> findFilesByName(VirtualFile[] files,
                                                        final String fileName,
                                                        Collection<VirtualFile> found,
                                                        final ProjectFileIndex fileIndex,
                                                        final ProgressIndicator indicator,
                                                        final boolean lookForNested) {
    for (VirtualFile file : files) {
      if (indicator != null && indicator.isCanceled()) {
        break;
      }
      if (fileIndex != null && fileIndex.isIgnored(file)) {
        continue;
      }
      if (!file.isDirectory() && file.getName().equalsIgnoreCase(fileName)) {
        found.add(file);
        if (indicator != null) {
          indicator.setText2(file.getPath());
        }
        if (!lookForNested) {
          return found;
        }
      }
    }
    for (VirtualFile file : files) {
      if (indicator != null && indicator.isCanceled()) {
        break;
      }
      if (fileIndex != null && fileIndex.isIgnored(file)) {
        continue;
      }
      if (file.isDirectory()) {
        findFilesByName(file.getChildren(), fileName, found, fileIndex, indicator, lookForNested);
      }
    }
    return found;
  }

  public static VirtualFile refreshRecursively(final String path) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @SuppressWarnings({"ConstantConditions"})
      public VirtualFile compute() {
        final VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
        if (dir != null ) {
          dir.refresh(false, true);
        }
        return dir;
      }
    });
  }
}
