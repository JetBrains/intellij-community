package com.intellij.ide.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

public class EditorHelper {
  public static Editor openInEditor(PsiElement element) {
    PsiFile file;
    int offset;
    if (element instanceof PsiFile){
      file = (PsiFile)element;
      offset = -1;
    }
    else{
      file = element.getContainingFile();
      offset = element.getTextOffset();
    }

    OpenFileDescriptor descriptor = new OpenFileDescriptor(element.getProject(), file.getVirtualFile(), offset);
    Project project = element.getProject();
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, false);
  }
}