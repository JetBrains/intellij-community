package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class IgnoredFilesComponent {
  private final Project myProject;
  private final List<IgnoredFileBean> myFilesToIgnore;

  public IgnoredFilesComponent(final Project project) {
    myProject = project;
    myFilesToIgnore = new ArrayList<IgnoredFileBean>();
  }

  public void add(final IgnoredFileBean... filesToIgnore) {
    synchronized(myFilesToIgnore) {
      Collections.addAll(myFilesToIgnore, filesToIgnore);
    }
  }

  public void clear() {
    synchronized (myFilesToIgnore) {
      myFilesToIgnore.clear();
    }
  }
  public boolean isEmpty() {
    synchronized (myFilesToIgnore) {
      return myFilesToIgnore.isEmpty();
    }
  }

  public void set(final IgnoredFileBean... filesToIgnore) {
    synchronized(myFilesToIgnore) {
      myFilesToIgnore.clear();
      Collections.addAll(myFilesToIgnore, filesToIgnore);
    }
  }

  public IgnoredFileBean[] getFilesToIgnore() {
    synchronized(myFilesToIgnore) {
      return myFilesToIgnore.toArray(new IgnoredFileBean[myFilesToIgnore.size()]);
    }
  }

  public boolean isIgnoredFile(@NotNull VirtualFile file) {
    synchronized(myFilesToIgnore) {
      if (myFilesToIgnore.size() == 0) {
        return false;
      }
      String filePath = null;
      // don't use VfsUtil.getRelativePath() here because it can't handle paths where one file is not a direct ancestor of another one
      final VirtualFile baseDir = myProject.getBaseDir();
      if (baseDir != null) {
        filePath = FileUtil.getRelativePath(new File(baseDir.getPath()), new File(file.getPath()));
        if (filePath != null) {
          filePath = FileUtil.toSystemIndependentName(filePath);
        }
        if (file.isDirectory()) {
          filePath += "/";
        }
      }
      for(IgnoredFileBean bean: myFilesToIgnore) {
        if (filePath != null) {
          final String prefix = bean.getPath();
          if ("./".equals(prefix)) {
            // special case for ignoring the project base dir (IDEADEV-16056)
            final String basePath = FileUtil.toSystemIndependentName(baseDir.getPath());
            final String fileAbsPath = FileUtil.toSystemIndependentName(file.getPath());
            if (StringUtil.startsWithIgnoreCase(fileAbsPath, basePath)) {
              return true;
            }
          }
          else if (prefix != null && StringUtil.startsWithIgnoreCase(filePath, FileUtil.toSystemIndependentName(prefix))) {
            return true;
          }
        }
        final Pattern pattern = bean.getPattern();
        if (pattern != null && pattern.matcher(file.getName()).matches()) {
          return true;
        }
      }
      return false;
    }
  }
}
