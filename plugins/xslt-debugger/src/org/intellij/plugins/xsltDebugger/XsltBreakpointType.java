// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.xsltDebugger;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.impl.XsltChecker;
import org.intellij.plugins.xsltDebugger.impl.XsltDebuggerEditorsProvider;
import org.jetbrains.annotations.NotNull;

public class XsltBreakpointType extends XLineBreakpointType<XBreakpointProperties> {
  private final XsltDebuggerEditorsProvider myMyEditorsProvider1 = new XsltDebuggerEditorsProvider(XsltChecker.LanguageLevel.V1);
  private final XsltDebuggerEditorsProvider myMyEditorsProvider2 = new XsltDebuggerEditorsProvider(XsltChecker.LanguageLevel.V2);

  public XsltBreakpointType() {
    super("xslt", XsltDebuggerBundle.message("title.xslt.breakpoints"));
  }

  @Override
  public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    if (document == null) return false;

    final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile == null) {
      return false;
    }
    final FileType fileType = psiFile.getFileType();
    if (fileType != XmlFileType.INSTANCE || !XsltSupport.isXsltFile(psiFile)) {
      return false;
    }
    return true;
  }

  @Override
  public XDebuggerEditorsProvider getEditorsProvider(@NotNull XLineBreakpoint<XBreakpointProperties> breakpoint, @NotNull Project project) {
    final XSourcePosition position = breakpoint.getSourcePosition();
    if (position == null) {
      return null;
    }

    final PsiFile file = PsiManager.getInstance(project).findFile(position.getFile());
    if (file == null) {
      return null;
    }

    final XsltChecker.LanguageLevel level = XsltSupport.getXsltLanguageLevel(file);
    if (level == XsltChecker.LanguageLevel.V1) {
      return myMyEditorsProvider1;
    } else if (level == XsltChecker.LanguageLevel.V2) {
      return myMyEditorsProvider2;
    }

    return null;
  }

  @Override
  public XBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
    return null;
  }
}
