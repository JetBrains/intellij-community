package org.intellij.plugins.xsltDebugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.ui.DebuggerIcons;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.plugins.xsltDebugger.impl.XsltDebuggerEditorsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 03.03.11
*/
public final class XsltBreakpointType extends XLineBreakpointType<XBreakpointProperties> {

  private final XsltDebuggerEditorsProvider myMyEditorsProvider = new XsltDebuggerEditorsProvider();

  public XsltBreakpointType() {
    super("xslt", "XSLT Breakpoints");
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
    if (fileType != StdFileTypes.XML || !XsltSupport.isXsltFile(psiFile)) {
      return false;
    }
    return true;
  }

  @Override
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myMyEditorsProvider;
  }

  @NotNull
  @Override
  public Icon getEnabledIcon() {
    return XsltSupport.createXsltIcon(DebuggerIcons.ENABLED_BREAKPOINT_ICON);
  }

  @NotNull
  @Override
  public Icon getDisabledIcon() {
    return XsltSupport.createXsltIcon(DebuggerIcons.DISABLED_BREAKPOINT_ICON);
  }

  @Override
  public XBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
    return null;
  }
}
