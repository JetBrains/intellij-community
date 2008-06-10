package com.intellij.openapi.vcs.update;

import java.util.List;

public class UpdateFilesHelper {
  private UpdateFilesHelper() {
  }

  public static void iterateFileGroupFiles(final UpdatedFiles updatedFiles, final Callback callback) {
    final List<FileGroup> groups = updatedFiles.getTopLevelGroups();
    for (FileGroup group : groups) {
      for (String file : group.getFiles()) {
        callback.onFile(file, group.getId());
      }

      // for changed on server
      for (FileGroup childGroup : group.getChildren()) {
        for (String childFile : childGroup.getFiles()) {
          callback.onFile(childFile, group.getId());
        }
      }
    }
  }

  public interface Callback {
    void onFile(final String filePath, final String groupId);
  }
}
