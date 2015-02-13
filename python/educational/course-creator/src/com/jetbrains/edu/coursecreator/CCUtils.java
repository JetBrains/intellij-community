package com.jetbrains.edu.coursecreator;

import com.intellij.ide.projectView.actions.MarkRootActionBase;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.coursecreator.format.Course;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  public static void enableAction(@NotNull final AnActionEvent event, boolean isEnable) {
    final Presentation presentation = event.getPresentation();
    presentation.setVisible(isEnable);
    presentation.setEnabled(isEnable);
  }

  public static int getIndex(@NotNull final String fullName, @NotNull final String logicalName) {
    if (!fullName.startsWith(logicalName)) {
      throw new IllegalArgumentException();
    }
    try {
      return Integer.parseInt(fullName.substring(logicalName.length()));
    }
    catch (NumberFormatException e) {
      return -1;
    }
  }

  @Nullable
  public static CCLanguageManager getStudyLanguageManager(@NotNull final Course course) {
    Language language = Language.findLanguageByID(course.getLanguage());
    return language == null ? null :  CCLanguageManager.INSTANCE.forLanguage(language);
  }
}
