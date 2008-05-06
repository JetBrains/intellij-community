/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 15.11.2006
 * Time: 17:53:24
 */
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class FilePatch {
  private String myBeforeName;
  private String myAfterName;
  private String myBeforeVersionId;
  private String myAfterVersionId;

  public String getBeforeName() {
    return myBeforeName;
  }

  public String getAfterName() {
    return myAfterName;
  }

  public String getBeforeFileName() {
    String[] pathNameComponents = myBeforeName.split("/");
    return pathNameComponents [pathNameComponents.length-1];
  }

  public String getAfterFileName() {
    String[] pathNameComponents = myAfterName.split("/");
    return pathNameComponents [pathNameComponents.length-1];
  }

  public void setBeforeName(final String fileName) {
    myBeforeName = fileName;  
  }

  public void setAfterName(final String fileName) {
    myAfterName = fileName;
  }

  public String getBeforeVersionId() {
    return myBeforeVersionId;
  }

  public void setBeforeVersionId(final String beforeVersionId) {
    myBeforeVersionId = beforeVersionId;
  }

  public String getAfterVersionId() {
    return myAfterVersionId;
  }

  public void setAfterVersionId(final String afterVersionId) {
    myAfterVersionId = afterVersionId;
  }

  public String getAfterNameRelative(int skipDirs) {
    String[] components = myAfterName.split("/");
    return StringUtil.join(components, skipDirs, components.length, "/");
  }

  public ApplyPatchStatus apply(ApplyPatchContext context, final Project project) throws ApplyPatchException, IOException {
    VirtualFile fileToPatch = findFileToPatch(context);

    if (fileToPatch == null) {
      throw new ApplyPatchException("Cannot find file to patch: " + myBeforeName);
    }

    return apply(fileToPatch, context, project);
  }

  public FilePath getTarget(final VirtualFile file) {
    if (isNewFile()) {
      return new FilePathImpl(file, getBeforeFileName(), false);
    }
    return new FilePathImpl(file);
  }

  public ApplyPatchStatus apply(final VirtualFile fileToPatch, final ApplyPatchContext context, final Project project) throws IOException, ApplyPatchException {
    context.addAffectedFile(getTarget(fileToPatch));
    if (isNewFile()) {
      if (fileToPatch.findChild(getBeforeFileName()) != null) {
        throw new ApplyPatchException("File " + getBeforeFileName() + " already exists");
      }
      VirtualFile newFile = fileToPatch.createChildData(this, getBeforeFileName());
      applyCreate(newFile);
    }
    else if (isDeletedFile()) {
      FileEditorManagerImpl.getInstance(project).closeFile(fileToPatch);
      fileToPatch.delete(this);
    }
    else {
      return applyChange(fileToPatch);
    }
    return ApplyPatchStatus.SUCCESS;
  }

  protected abstract void applyCreate(VirtualFile newFile) throws IOException, ApplyPatchException;
  protected abstract ApplyPatchStatus applyChange(VirtualFile fileToPatch) throws IOException, ApplyPatchException;

  @Nullable
  public VirtualFile findFileToPatch(@NotNull ApplyPatchContext context) throws IOException {
    return findPatchTarget(context, myBeforeName, myAfterName, isNewFile());
  }

  @Nullable
  public static VirtualFile findPatchTarget(final ApplyPatchContext context, final String beforeName, final String afterName,
                                            final boolean isNewFile) throws IOException {
    VirtualFile file = null;
    if (beforeName != null) {
      file = findFileToPatchByName(context, beforeName, isNewFile);
    }
    if (file == null) {
      file = findFileToPatchByName(context, afterName, isNewFile);
    }
    else if (context.isAllowRename() && afterName != null && !beforeName.equals(afterName)) {
      String[] beforeNameComponents = beforeName.split("/");
      String[] afterNameComponents = afterName.split("/");
      if (!beforeNameComponents [beforeNameComponents.length-1].equals(afterNameComponents [afterNameComponents.length-1])) {
        context.registerBeforeRename(file);
        file.rename(FilePatch.class, afterNameComponents [afterNameComponents.length-1]);
        context.addAffectedFile(file);
      }
      boolean needMove = (beforeNameComponents.length != afterNameComponents.length);
      if (!needMove) {
        needMove = checkPackageRename(context, beforeNameComponents, afterNameComponents);
      }
      if (needMove) {
        VirtualFile moveTarget = findFileToPatchByComponents(context, afterNameComponents, afterNameComponents.length-1);
        if (moveTarget == null) {
          return null;
        }
        context.registerBeforeRename(file);
        file.move(FilePatch.class, moveTarget);
        context.addAffectedFile(file);
      }
    }
    return file;
  }

  private static boolean checkPackageRename(final ApplyPatchContext context,
                                            final String[] beforeNameComponents,
                                            final String[] afterNameComponents) {
    int changedIndex = -1;
    for(int i=context.getSkipTopDirs(); i<afterNameComponents.length-1; i++) {
      if (!beforeNameComponents [i].equals(afterNameComponents [i])) {
        if (changedIndex != -1) {
          return true;
        }
        changedIndex = i;
      }
    }
    if (changedIndex == -1) return false;
    VirtualFile oldDir = findFileToPatchByComponents(context, beforeNameComponents, changedIndex+1);
    VirtualFile newDir = findFileToPatchByComponents(context.getPrepareContext(), afterNameComponents, changedIndex+1);
    if (oldDir != null && newDir == null) {
      context.addPendingRename(oldDir, afterNameComponents [changedIndex]);
      return false;
    }
    return true;
  }

  @Nullable
  private static VirtualFile findFileToPatchByName(@NotNull ApplyPatchContext context, final String fileName,
                                                   boolean isNewFile) {
    String[] pathNameComponents = fileName.split("/");
    int lastComponentToFind = isNewFile ? pathNameComponents.length-1 : pathNameComponents.length;
    return findFileToPatchByComponents(context, pathNameComponents, lastComponentToFind);
  }

  @Nullable
  private static VirtualFile findFileToPatchByComponents(ApplyPatchContext context,
                                                         final String[] pathNameComponents,
                                                         final int lastComponentToFind) {
    VirtualFile patchedDir = context.getBaseDir();
    for(int i=context.getSkipTopDirs(); i<lastComponentToFind; i++) {
      VirtualFile nextChild;
      if (pathNameComponents [i].equals("..")) {
        nextChild = patchedDir.getParent();
      }
      else {
        nextChild = patchedDir.findChild(pathNameComponents [i]);
      }
      if (nextChild == null) {
        if (context.isCreateDirectories()) {
          try {
            nextChild = patchedDir.createChildDirectory(null, pathNameComponents [i]);
          }
          catch (IOException e) {
            return null;
          }
        }
        else {
          context.registerMissingDirectory(patchedDir, pathNameComponents, i);
          return null;
        }
      }
      patchedDir = nextChild;
    }
    return patchedDir;
  }

  public abstract boolean isNewFile();

  public abstract boolean isDeletedFile();
}