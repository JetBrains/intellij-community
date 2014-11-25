package org.jetbrains.plugins.coursecreator;

import com.intellij.ide.projectView.actions.MarkRootActionBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class CCUtils {
  private static final Logger LOG = Logger.getInstance(CCUtils.class.getName());

  public static void markDirAsSourceRoot(@NotNull final VirtualFile dir, @NotNull final Project project) {
    final Module module = ModuleUtilCore.findModuleForFile(dir, project);
    if (module == null) {
      LOG.info("Module for " + dir.getPath() + " was not found");
      return;
    }
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    ContentEntry entry = MarkRootActionBase.findContentEntry(model, dir);
    if (entry == null) {
      LOG.info("Content entry for " + dir.getPath() + " was not found");
      return;
    }
    entry.addSourceFolder(dir, false);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        model.commit();
        module.getProject().save();
      }
    });
  }
}
