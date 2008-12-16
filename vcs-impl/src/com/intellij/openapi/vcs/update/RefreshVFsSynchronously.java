package com.intellij.openapi.vcs.update;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class RefreshVFsSynchronously {
  private RefreshVFsSynchronously() {
  }

  public static void updateAllChanged(final UpdatedFiles updatedFiles) {
    // approx so ok
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      progressIndicator.setIndeterminate(false);
    }
    final int num = getFilesNum(updatedFiles);

    UpdateFilesHelper.iterateFileGroupFilesDeletedOnServerFirst(updatedFiles, new MyRefreshCallback(num, progressIndicator));
  }

  private static int getFilesNum(final UpdatedFiles files) {
    int result = 0;
    for (FileGroup group : files.getTopLevelGroups()) {
      result += group.getImmediateFilesSize();
      final List<FileGroup> children = group.getChildren();
      for (FileGroup child : children) {
        result += child.getImmediateFilesSize();
      }
    }
    return result;
  }

  @Nullable
  public static VirtualFile findCreatedFile(final File root) {
    refresh(root);
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    return lfs.findFileByIoFile(root);
  }

  private static void refresh(final File root) {
    final LocalFileSystem lfs = LocalFileSystem.getInstance();
    VirtualFile vFile = lfs.refreshAndFindFileByIoFile(root);
    if (vFile != null) {
      vFile.refresh(false, false);
      return;
    }
  }

  private static void refreshDeletedOrReplaced(final File root) {
    final String path = root.getAbsolutePath();
    @NonNls final String correctedPath = VfsUtil.pathToUrl(path.replace(File.separatorChar, '/'));
    final VirtualFile vf = ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      @Nullable
      public VirtualFile compute() {
        return VirtualFileManager.getInstance().findFileByUrl(correctedPath);
      }
    });
    if (vf != null) {
      VfsUtil.processFilesRecursively(vf, new Processor<VirtualFile>() {
        public boolean process(VirtualFile virtualFile) {
          virtualFile.refresh(false, false);
          return true;
        }
      });
    }
  }

  private static class MyRefreshCallback implements UpdateFilesHelper.Callback {
    private int myCnt;
    private final double myTotal;
    private final ProgressIndicator myProgressIndicator;

    private MyRefreshCallback(final int total, final ProgressIndicator progressIndicator) {
      myTotal = total;
      myProgressIndicator = progressIndicator;
      myCnt = 0;
    }

    public void onFile(String filePath, String groupId) {
      final File file = new File(filePath);
      if (FileGroup.REMOVED_FROM_REPOSITORY_ID.equals(groupId)) {
        refreshDeletedOrReplaced(file);
      } else {
        refresh(file);
      }
      if (myProgressIndicator != null) {
        ++ myCnt;
        myProgressIndicator.setFraction(myCnt/myTotal);
        myProgressIndicator.setText2("Refreshing " + filePath);
      }
    }
  }
}
