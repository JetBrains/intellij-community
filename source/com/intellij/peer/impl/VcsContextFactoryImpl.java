package com.intellij.peer.impl;

import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vcs.actions.VcsContext;
import com.intellij.openapi.vcs.actions.VcsContextWrapper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.LocalChangeListImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author yole
*/
public class VcsContextFactoryImpl implements VcsContextFactory {
  public VcsContext createCachedContextOn(AnActionEvent event) {
    return VcsContextWrapper.createCachedInstanceOn(event);
  }

  public VcsContext createContextOn(final AnActionEvent event) {
    return new VcsContextWrapper(event.getDataContext(), event.getModifiers(), event.getPlace());
  }

  public FilePath createFilePathOn(@NotNull final VirtualFile virtualFile) {
    return ApplicationManager.getApplication().runReadAction(new Computable<FilePath>() {
      public FilePath compute() {
        return new FilePathImpl(virtualFile);
      }
    });
  }

  public FilePath createFilePathOn(final File file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<FilePath>() {
      public FilePath compute() {
        return FilePathImpl.create(file);
      }
    });
  }

  public FilePath createFilePathOn(final File file, final boolean isDirectory) {
    return ApplicationManager.getApplication().runReadAction(new Computable<FilePath>() {
      public FilePath compute() {
        return FilePathImpl.create(file, isDirectory);
      }
    });
  }

  @NotNull
    public FilePath createFilePathOnNonLocal(final String path, final boolean isDirectory) {
    return FilePathImpl.createNonLocal(path, isDirectory);
  }

  public FilePath createFilePathOnDeleted(final File file, final boolean isDirectory) {
    return ApplicationManager.getApplication().runReadAction(new Computable<FilePath>() {
      public FilePath compute() {
        return FilePathImpl.createForDeletedFile(file, isDirectory);
      }
    });
  }

  public FilePath createFilePathOn(final VirtualFile parent, final String name) {
    return ApplicationManager.getApplication().runReadAction(new Computable<FilePath>() {
      public FilePath compute() {
        return new FilePathImpl(parent, name, false);
      }
    });
  }

  public LocalChangeList createLocalChangeList(Project project, @NotNull final String name) {
    return LocalChangeListImpl.createEmptyChangeListImpl(project, name);
  }
}