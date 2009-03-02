package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
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
      StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          myLife.born();
          markEverythingDirty();
        }
      });
    }
  }

  public void markEverythingDirty() {
    if (myProject.isDisposed()) return;

    final boolean done = myLife.doIfAlive(new Runnable() {
      public void run() {
        myDirtBuilder.everythingDirty();
      }
    });

    if (done) {
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
    final boolean done = myLife.doIfAlive(new Runnable() {
      public void run() {
        filler.consume(myDirtBuilder);
      }
    });

    if (done && (! myDirtBuilder.isEmpty())) {
      myChangeListManager.scheduleUpdate();
    }
    // no sence in checking correct here any more: vcs is searched for asynchronously
    return (! done);
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

    final boolean done = myLife.doIfAlive(new Runnable() {
      public void run() {
        dirtCopyRef.set(new DirtBuilder(myDirtBuilder));
        myDirtBuilder.reset();
      }
    });
    
    if (done && (! dirtCopyRef.isNull())) {
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

  private static class SynchronizedLife {
    private LifeStages myStage;
    private final Object myLock;

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

    // allow work under inner lock: inner class, not wide scope
    public boolean doIfAlive(final Runnable runnable) {
      synchronized (myLock) {
        if (LifeStages.ALIVE.equals(myStage)) {
          runnable.run();
          return true;
        }
      }
      return false;
    }

    private static enum LifeStages {
      NOT_BORN,
      ALIVE,
      DEAD
    }
  }
}
