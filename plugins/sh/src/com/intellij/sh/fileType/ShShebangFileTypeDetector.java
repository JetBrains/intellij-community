// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.fileType;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.ExactFileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.sh.parser.ShShebangParserUtil;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Service
public final class ShShebangFileTypeDetector implements DocumentListener, Disposable {
  private static final Set<@NlsSafe String> KNOWN_SHELLS = Set.of("sh", "zsh", "bash"); //NON-NLS

  @Override
  public void documentChanged(@NotNull DocumentEvent event) {
    Document document = event.getDocument();
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null || virtualFile instanceof LightVirtualFile) return;
    Project project = ProjectUtil.guessProjectForFile(virtualFile);
    if (project == null) return;
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    PsiFile psiFile = psiDocumentManager.getPsiFile(document);
    if (!(psiFile instanceof PsiPlainTextFile)) return;
    TextRange textRange = TextRange.create(document.getLineStartOffset(0), document.getLineEndOffset(0));
    String firstLine = document.getText(textRange);
    String interpreter = ShShebangParserUtil.detectInterpreter(firstLine);
    if (interpreter != null && KNOWN_SHELLS.contains(interpreter)) {
      psiDocumentManager.performLaterWhenAllCommitted(() -> {
        ApplicationManager.getApplication().runWriteAction(() -> {
          FileTypeManager.getInstance().removeAssociation(FileTypes.PLAIN_TEXT, new ExactFileNameMatcher(psiFile.getName()));
          FileDocumentManager.getInstance().saveDocument(document);
        });
      });
    }
  }

  public void subscribe() {
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(this, this);
  }

  @Override
  public void dispose() {
    EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(this);
  }

  public static ShShebangFileTypeDetector getInstance(Project project) {
    return project.getService(ShShebangFileTypeDetector.class);
  }
}
