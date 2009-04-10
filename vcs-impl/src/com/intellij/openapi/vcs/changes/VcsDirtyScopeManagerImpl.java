package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.*;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * @author max
 */
public class VcsDirtyScopeManagerImpl extends VcsDirtyScopeManager implements ProjectComponent {
  private final VcsDirtyScopeManagerImpl.MyVfsListener myVfsListener;
  private final Project myProject;
  private final ChangeListManager myChangeListManager;
  private final ProjectLevelVcsManager myVcsManager;

  private final DirtBuilder myDirtBuilder;
  private final VcsGuess myGuess;
  private final SynchronizedLife myLife;

  public VcsDirtyScopeManagerImpl(Project project, ChangeListManager changeListManager, ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myChangeListManager = changeListManager;
    myVcsManager = vcsManager;
    myVfsListener = new MyVfsListener();

    myLife = new SynchronizedLife();
    myGuess = new VcsGuess(myProject);
    myDirtBuilder = new DirtBuilder(myGuess);
  }

  public void projectOpened() {
    VirtualFileManager.getInstance().addVirtualFileListener(myVfsListener);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myLife.born();
      final AbstractVcs[] vcss = myVcsManager.getAllActiveVcss();
      if (vcss.length > 0) {
        markEverythingDirty();
      }
    }
    else {
      StartupManager.getInstance(myProject).registerPostStartupActivity(new DumbAwareRunnable() {
        public void run() {
          myLife.born();
          markEverythingDirty();
        }
      });
    }
  }

  public void suspendMe() {
    myLife.suspendMe();
  }

  public void reanimate() {
    final Ref<Boolean> wasNotEmptyRef = new Ref<Boolean>();
    myLife.releaseMe(new Runnable() {
      public void run() {
        wasNotEmptyRef.set(! myDirtBuilder.isEmpty());
      }
    });
    if (Boolean.TRUE.equals(wasNotEmptyRef.get())) {
      myChangeListManager.scheduleUpdate();
    }
  }

  public void markEverythingDirty() {
    if (myProject.isDisposed()) return;

    final LifeDrop lifeDrop = myLife.doIfAlive(new Runnable() {
      public void run() {
        myDirtBuilder.everythingDirty();
      }
    });

    if (lifeDrop.isDone() && (! lifeDrop.isSuspened())) {
      myChangeListManager.scheduleUpdate();
    }
  }

  public void projectClosed() {
    myLife.kill();
    
    VirtualFileManager.getInstance().removeVirtualFileListener(myVfsListener);
  }

  @NotNull @NonNls
  public String getComponentName() {
    return "VcsDirtyScopeManager";
  }

  public void initComponent() {}

  public void disposeComponent() {
    myLife.kill();
  }

  public boolean filePathsDirty(@Nullable final Collection<FilePath> filesDirty, @Nullable final Collection<FilePath> dirsRecursivelyDirty) {
    return takeDirt(new Consumer<DirtBuilder>() {
      public void consume(final DirtBuilder dirt) {
        if (filesDirty != null) {
          for (FilePath path : filesDirty) {
            dirt.addDirtyFile(path);
          }
        }
        if (dirsRecursivelyDirty != null) {
          for (FilePath path : dirsRecursivelyDirty) {
            dirt.addDirtyDirRecursively(path);
          }
        }
      }
    });
  }

  private boolean takeDirt(final Consumer<DirtBuilder> filler) {
    final Ref<Boolean> wasNotEmptyRef = new Ref<Boolean>();
    final Runnable runnable = new Runnable() {
      public void run() {
        filler.consume(myDirtBuilder);
        wasNotEmptyRef.set(!myDirtBuilder.isEmpty());
      }
    };
    final LifeDrop lifeDrop = myLife.doIfAlive(runnable);

    if (lifeDrop.isDone() && (! lifeDrop.isSuspened()) && (Boolean.TRUE.equals(wasNotEmptyRef.get()))) {
      myChangeListManager.scheduleUpdate();
    }
    // no sence in checking correct here any more: vcs is searched for asynchronously
    return (! lifeDrop.isDone());
  }

  public boolean filesDirty(@Nullable final Collection<VirtualFile> filesDirty, @Nullable final Collection<VirtualFile> dirsRecursivelyDirty) {
    return takeDirt(new Consumer<DirtBuilder>() {
      public void consume(final DirtBuilder dirt) {
        if (filesDirty != null) {
          for (VirtualFile path : filesDirty) {
            dirt.addDirtyFile(new FilePathImpl(path));
          }
        }
        if (dirsRecursivelyDirty != null) {
          for (VirtualFile path : dirsRecursivelyDirty) {
            dirt.addDirtyDirRecursively(new FilePathImpl(path));
          }
        }
      }
    });
  }

  public void fileDirty(final VirtualFile file) {
    takeDirt(new Consumer<DirtBuilder>() {
      public void consume(DirtBuilder dirtBuilder) {
        dirtBuilder.addDirtyFile(new FilePathImpl(file));
      }
    });
  }

  public void fileDirty(final FilePath file) {
    takeDirt(new Consumer<DirtBuilder>() {
      public void consume(DirtBuilder dirtBuilder) {
        dirtBuilder.addDirtyFile(file);
      }
    });
  }

  public void dirDirtyRecursively(final VirtualFile dir, final boolean scheduleUpdate) {
    dirDirtyRecursively(dir);
  }

  public void dirDirtyRecursively(final VirtualFile dir) {
    takeDirt(new Consumer<DirtBuilder>() {
      public void consume(DirtBuilder dirtBuilder) {
        dirtBuilder.addDirtyDirRecursively(new FilePathImpl(dir));
      }
    });
  }

  public void dirDirtyRecursively(final FilePath path) {
    takeDirt(new Consumer<DirtBuilder>() {
      public void consume(DirtBuilder dirtBuilder) {
        dirtBuilder.addDirtyDirRecursively(path);
      }
    });
  }

  @Nullable
  public VcsInvalidated retrieveScopes() {
    final Ref<DirtBuilder> dirtCopyRef = new Ref<DirtBuilder>();

    final LifeDrop lifeDrop = myLife.doIfAlive(new Runnable() {
      public void run() {
        dirtCopyRef.set(new DirtBuilder(myDirtBuilder));
        myDirtBuilder.reset();
      }
    });

    if (lifeDrop.isDone() && (! dirtCopyRef.isNull())) {
      return ApplicationManager.getApplication().runReadAction(new Computable<VcsInvalidated>() {
        public VcsInvalidated compute() {
          final Scopes scopes = new Scopes(myProject, myGuess);
          scopes.takeDirt(dirtCopyRef.get());
          return scopes.retrieveAndClear();
        }
      });
    }
    return null;
  }

  private String toStringScopes(final VcsInvalidated vcsInvalidated) {
    final StringBuilder sb = new StringBuilder();
    sb.append("is everything dirty: ").append(vcsInvalidated.isEverythingDirty()).append(";\n");
    for (VcsDirtyScope scope : vcsInvalidated.getScopes()) {
      sb.append("|\nFiles: ");
      for (FilePath path : scope.getDirtyFiles()) {
        sb.append(path).append('\n');
      }
      sb.append("\nDirs: ");
      for (FilePath filePath : scope.getRecursivelyDirtyDirectories()) {
        sb.append(filePath).append('\n');
      }
    }
    sb.append("-------------");
    return sb.toString();
  }

  private class MyVfsListener extends VirtualFileAdapter {
    @Override
    public void contentsChanged(VirtualFileEvent event) {
      fileDirty(event.getFile());
    }

    @Override
    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        VirtualFile renamed = event.getFile();
        if (renamed.getParent() != null) {
          renamed = renamed.getParent();
        }
        dirtyFileOrDir(renamed);
      }
      else {
        fileDirty(event.getFile());
      }
    }

    private void dirtyFileOrDir(final VirtualFile file) {
      if (file.isDirectory()) {
          dirDirtyRecursively(file, true);
        }
        else {
          fileDirty(file);
        }
    }

    @Override
    public void fileCreated(VirtualFileEvent event) {
      fileDirty(event.getFile());
    }

    @Override
    public void beforePropertyChange(VirtualFilePropertyEvent event) {
      fileDirty(event.getFile());
    }

    @Override
    public void beforeFileDeletion(VirtualFileEvent event) {
      if (!event.getFile().isInLocalFileSystem()) return;
      // need to keep track of whether the deleted file was a directory
      final boolean directory = event.getFile().isDirectory();
      final FilePathImpl path = new FilePathImpl(new File(event.getFile().getPath()), directory);
      if (directory) {
        dirDirtyRecursively(path);   // IDEADEV-12752
      }
      else {
        fileDirty(path);
      }
    }

    @Override
    public void beforeFileMovement(VirtualFileMoveEvent event) {
      // need to create FilePath explicitly without referring to VirtualFile because otherwise the FilePath
      // will reference the path after the move
      fileDirty(new FilePathImpl(new File(event.getFile().getPath()), event.getFile().isDirectory()));
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
      dirtyFileOrDir(event.getFile());
    }
  }

  private static class LifeDrop {
    private final boolean myDone;
    private final boolean mySuspened;

    private LifeDrop(boolean done, boolean suspened) {
      myDone = done;
      mySuspened = suspened;
    }

    public boolean isDone() {
      return myDone;
    }

    public boolean isSuspened() {
      return mySuspened;
    }
  }

  private static class SynchronizedLife {
    private LifeStages myStage;
    private final Object myLock;
    private boolean mySuspended;

    private SynchronizedLife() {
      myStage = LifeStages.NOT_BORN;
      myLock = new Object();
    }

    public void born() {
      synchronized (myLock) {
        myStage = LifeStages.ALIVE;
      }
    }

    public void kill() {
      synchronized (myLock) {
        myStage = LifeStages.DEAD;
      }
    }

    public void suspendMe() {
      synchronized (myLock) {
        if (LifeStages.ALIVE.equals(myStage)) {
          mySuspended = true;
        }
      }
    }

    public void releaseMe(final Runnable runnable) {
      synchronized (myLock) {
        if (LifeStages.ALIVE.equals(myStage)) {
          mySuspended = false;
          runnable.run();
        }
      }
    }

    public LifeDrop doIfAliveAndNotSuspended(final Runnable runnable) {
      synchronized (myLock) {
        synchronized (myLock) {
          if (LifeStages.ALIVE.equals(myStage) && (! mySuspended)) {
            runnable.run();
            return new LifeDrop(true, mySuspended);
          }
          return new LifeDrop(false, mySuspended);
        }
      }
    }

    // allow work under inner lock: inner class, not wide scope
    public LifeDrop doIfAlive(final Runnable runnable) {
      synchronized (myLock) {
        if (LifeStages.ALIVE.equals(myStage)) {
          runnable.run();
          return new LifeDrop(true, mySuspended);
        }
        return new LifeDrop(false, mySuspended);
      }
    }

    private static enum LifeStages {
      NOT_BORN,
      ALIVE,
      DEAD
    }
  }
}
