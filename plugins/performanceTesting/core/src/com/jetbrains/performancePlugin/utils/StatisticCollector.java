// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils;

import com.intellij.ide.troubleshooting.CompositeGeneralTroubleInfoCollector;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VFileProperty;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class StatisticCollector {
  private final Project project;

  public StatisticCollector(Project project) {
    this.project = project;
  }

  private @NotNull Holder analyzeFiles() {
    final Holder holder = new Holder();
    ProjectFileIndex.getInstance(project).iterateContent(fileOrDir -> {
      if (fileOrDir.is(VFileProperty.SYMLINK) && !fileOrDir.is(VFileProperty.HIDDEN)) {
        holder.setSymlink();
      }
      if (!fileOrDir.isDirectory()) {
        holder.increaseByOne();
      }
      return true;
    });
    return holder;
  }

  public String collectMetrics(boolean addGeneralInfo) {
    StringBuilder output = new StringBuilder();
    FileEditorManagerImpl fileEditorManager = (FileEditorManagerImpl)FileEditorManager.getInstance(project);

    VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
    if (baseDir != null) {
      Holder holder = analyzeFiles();
      output.append("Filesystem Info:\n");
      output.append("File system is case sensitive: ").append(baseDir.getFileSystem().isCaseSensitive()).append('\n');
      output.append("File is case sensitive: ").append(baseDir.isCaseSensitive()).append('\n');
      output.append("Are there symlinks: ").append(holder.isSymlink()).append('\n');
      output.append("Number of files: ").append(holder.getNumOfFiles()).append('\n');
      output.append('\n');
    }

    output.append("Project Info:\n");
    output.append("Number of opened files: ").append(fileEditorManager.getOpenedFiles().size()).append('\n');
    ReadAction.run(() -> {
      Editor selectedTextEditor = fileEditorManager.getSelectedTextEditor(true);
      if (selectedTextEditor != null) {
        Document document = selectedTextEditor.getDocument();
        output.append("File size (in lines): ").append(document.getLineCount()).append('\n');
        output.append("File size in characters: ").append(document.getTextLength()).append('\n');
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
        if (psiFile != null) {
          output.append("Number of injections: ")
            .append(InjectedLanguageManager.getInstance(project).getCachedInjectedDocumentsInRange(psiFile, psiFile
              .getTextRange()).size()).append('\n');
        }
      }
    });

    if (addGeneralInfo) {
      output.append('\n');
      output.append(new CompositeGeneralTroubleInfoCollector().collectInfo(project));
    }
    return output.toString();
  }

  private static class Holder {
    private boolean isSymlink;
    private int numOfFiles;

    public boolean isSymlink() {
      return isSymlink;
    }

    public void setSymlink() {
      isSymlink = true;
    }

    public int getNumOfFiles() {
      return numOfFiles;
    }

    public void increaseByOne() {
      numOfFiles++;
    }
  }
}
