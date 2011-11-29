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
import org.intellij.lang.xpath.xslt.impl.XsltChecker;
import org.intellij.plugins.xsltDebugger.impl.XsltDebuggerEditorsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 03.03.11
*/
public abstract class XsltBreakpointType extends XLineBreakpointType<XBreakpointProperties> {

  private final XsltDebuggerEditorsProvider myMyEditorsProvider = new XsltDebuggerEditorsProvider(getLanguageLevel());

  protected XsltBreakpointType(final String id) {
    super(id, "XSLT Breakpoints");
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
    return getLanguageLevel() == XsltSupport.getXsltLanguageLevel(psiFile);
  }

  protected abstract XsltChecker.LanguageLevel getLanguageLevel();

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

  public static final class V1 extends XsltBreakpointType {
    public V1() {
      super("xslt");
    }

    @Override
    protected XsltChecker.LanguageLevel getLanguageLevel() {
      return XsltChecker.LanguageLevel.V1;
    }
  }

  public static final class V2 extends XsltBreakpointType {
    public V2() {
      super("xslt2");
    }

    @Override
    protected XsltChecker.LanguageLevel getLanguageLevel() {
      return XsltChecker.LanguageLevel.V2;
    }
  }
}
