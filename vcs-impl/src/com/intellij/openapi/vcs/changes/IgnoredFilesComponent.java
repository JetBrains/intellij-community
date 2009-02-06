package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class IgnoredFilesComponent {
  private final Project myProject;
  private final Set<IgnoredFileBean> myFilesToIgnore;

  public IgnoredFilesComponent(final Project project) {
    myProject = project;
    myFilesToIgnore = new HashSet<IgnoredFileBean>();
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

  public static enum IgnoreResult {
    NO,
    SELF,
    SUBTREE
  }

  public boolean isIgnoredFile(@NotNull VirtualFile file) {
    return ! IgnoreResult.NO.equals(isIgnoredFileWithType(file));
  }

  public IgnoreResult isIgnoredFileWithType(@NotNull VirtualFile file) {
    synchronized(myFilesToIgnore) {
      if (myFilesToIgnore.size() == 0) {
        return IgnoreResult.NO;
      }
      // don't use VfsUtil.getRelativePath() here because it can't handle paths where one file is not a direct ancestor of another one
      final VirtualFile baseDir = myProject.getBaseDir();
      final File baseDirIoFile = (baseDir != null) ? new File(baseDir.getPath()) : null;
      String absIoPath = new File(file.getPath()).getAbsolutePath();
      absIoPath = file.isDirectory() ? (absIoPath + File.separatorChar) : absIoPath;
      for(IgnoredFileBean bean: myFilesToIgnore) {
        final String prefix = bean.getPath();
        if ("./".equals(prefix) && baseDir != null) {
          // special case for ignoring the project base dir (IDEADEV-16056)
          final String basePath = FileUtil.toSystemIndependentName(baseDir.getPath());
          final String fileAbsPath = FileUtil.toSystemIndependentName(file.getPath());
          if (StringUtil.startsWithIgnoreCase(fileAbsPath, basePath)) {
            return IgnoreResult.SUBTREE;
          }
        }
        else if (prefix != null && bean.fileIsUnderMe(absIoPath, baseDirIoFile)) {
          return IgnoreSettingsType.UNDER_DIR.equals(bean.getType()) ? IgnoreResult.SUBTREE : IgnoreResult.SELF;
        }
        final Pattern pattern = bean.getPattern();
        if (pattern != null && pattern.matcher(file.getName()).matches()) {
          return IgnoreResult.SELF;
        }
      }
      return IgnoreResult.NO;
    }
  }
}
