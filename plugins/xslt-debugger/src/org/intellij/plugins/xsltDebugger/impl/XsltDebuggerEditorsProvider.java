package org.intellij.plugins.xsltDebugger.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.LocalTimeCounter;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.xslt.impl.XsltChecker;
import org.intellij.plugins.xsltDebugger.BreakpointContext;
import org.intellij.plugins.xsltDebugger.rt.engine.Debugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XsltDebuggerEditorsProvider extends XDebuggerEditorsProvider {

  private final XPathFileType myFileType;

  public XsltDebuggerEditorsProvider(XsltChecker.LanguageLevel level) {
    myFileType = level == XsltChecker.LanguageLevel.V2 ? XPathFileType.XPATH2 : XPathFileType.XPATH;
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return myFileType;
  }

  @NotNull
  @Override
  public Document createDocument(@NotNull Project project,
                                 @NotNull String text,
                                 @Nullable XSourcePosition sourcePosition,
                                 @NotNull EvaluationMode mode) {
    final PsiFile psiFile = PsiFileFactory.getInstance(project)
      .createFileFromText("XPathExpr." + myFileType.getDefaultExtension(), myFileType, text, LocalTimeCounter.currentTime(), true);

    if (sourcePosition instanceof XsltSourcePosition && ((XsltSourcePosition)sourcePosition).getLocation() instanceof Debugger.StyleFrame) {
      final Debugger.Locatable location = ((XsltSourcePosition)sourcePosition).getLocation();
      final EvalContextProvider context = new EvalContextProvider(((Debugger.StyleFrame)location).getVariables());
      context.attachTo(psiFile);
    } else {
      final PsiElement contextElement = XsltBreakpointHandler.findContextElement(project, sourcePosition);
      if (contextElement != null) {
        final BreakpointContext context = new BreakpointContext(contextElement);
        context.attachTo(psiFile);
      }
    }

    final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    assert document != null;
    return document;
  }
}
