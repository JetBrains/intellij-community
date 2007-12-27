/*
 * @author max
 */
package com.intellij.ide;

import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Icons;
import com.intellij.util.PsiIconUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FileIconPatcherImpl implements ApplicationComponent, FileIconProvider, FileIconPatcher {
  public void disposeComponent() {}

  @NonNls
  @NotNull
  public String getComponentName() {
    return "FileIconProvider";
  }

  public void initComponent() {
  }

  @Nullable
  public Icon getIcon(final VirtualFile file, final int flags, final Project project) {
    if(project == null) return null;

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    return psiFile == null ? null : PsiIconUtil.getProvidersIcon(psiFile, flags);
  }

  public Icon patchIcon(final Icon baseIcon, final VirtualFile file, final int flags, final Project project) {
    Icon icon = baseIcon;
    if (project != null) {
      final boolean isUnderSource = FileIndexUtil.isJavaSourceFile(project, file);
      FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(file);
      if (fileType == StdFileTypes.JAVA) {
        if (!isUnderSource) {
          icon = Icons.JAVA_OUTSIDE_SOURCE_ICON;
        }
        else {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
          if (psiFile instanceof PsiClassOwner) {
            PsiClass[] classes = ((PsiClassOwner)psiFile).getClasses();
            if (classes.length > 0) {
              // prefer icon of the class named after file
              final String fileName = file.getNameWithoutExtension();
              Icon classIcon = null;
              for (PsiClass aClass : classes) {
                if (Comparing.strEqual(aClass.getName(), fileName)) {
                  classIcon = aClass.getIcon(flags);
                  break;
                }
              }
              if (classIcon == null) classIcon = classes[classes.length - 1].getIcon(flags);
              icon = classIcon;
            }
          }
        }
      }
    }

    Icon excludedIcon = null;
    if (project != null) {
      final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      if (projectFileIndex.isInSource(file) && CompilerManager.getInstance(project).isExcludedFromCompilation(file)) {
        excludedIcon = Icons.EXCLUDED_FROM_COMPILE_ICON;
      }
    }

    Icon lockedIcon = null;
    if ((flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !file.isWritable()) {
      lockedIcon = Icons.LOCKED_ICON;
    }

    if (excludedIcon != null || lockedIcon != null) {
      LayeredIcon layeredIcon = new LayeredIcon(1 + (lockedIcon != null ? 1 : 0) + (excludedIcon != null ? 1 : 0));
      int layer = 0;
      layeredIcon.setIcon(icon, layer++);
      if (lockedIcon != null) {
        layeredIcon.setIcon(lockedIcon, layer++);
      }
      if (excludedIcon != null) {
        layeredIcon.setIcon(excludedIcon, layer);
      }
      icon = layeredIcon;
    }

    return icon;
  }
}