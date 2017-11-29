package org.jetbrains.plugins.ipnb.psi;

import com.google.common.collect.Lists;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.ResolveResult;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;

import java.util.Collections;
import java.util.List;

public class IpnbPyReference extends PyReferenceImpl {
  public IpnbPyReference(PyQualifiedExpression element, @NotNull PyResolveContext context) {
    super(element, context);
  }

  @Override
  public HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
    final HighlightSeverity severity = super.getUnresolvedHighlightSeverity(context);
    return severity != null ? HighlightSeverity.WARNING : null;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    final List<Object> variants = Lists.newArrayList();
    Collections.addAll(variants, super.getVariants());
    PsiFile file = myElement.getContainingFile();
    if (file instanceof IpnbPyFragment) {
      final IpnbFilePanel panel = ((IpnbPyFragment)file).getFilePanel();
      final List<IpnbEditablePanel> panels = panel.getIpnbPanels();

      for (IpnbEditablePanel editablePanel : panels) {
        if (!(editablePanel instanceof IpnbCodePanel)) continue;
        final Editor editor = ((IpnbCodePanel)editablePanel).getEditor();
        final IpnbPyFragment psiFile =
          (IpnbPyFragment)PsiDocumentManager.getInstance(myElement.getProject()).getPsiFile(editor.getDocument());
        if (psiFile == null) continue;
        final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(myElement);
        PyResolveUtil.scopeCrawlUp(processor, psiFile, null, null);
        variants.addAll(getOriginalElements(processor));
      }
    }
    return variants.toArray();
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    ResolveResult[] results = super.multiResolve(incompleteCode);
    if (results.length == 0) {
      PsiFile file = myElement.getContainingFile();
      if (file instanceof IpnbPyFragment) {
        final IpnbFilePanel panel = ((IpnbPyFragment)file).getFilePanel();
        final List<IpnbEditablePanel> panels = panel.getIpnbPanels();

        final String referencedName = myElement.getReferencedName();
        if (referencedName == null) return ResolveResult.EMPTY_ARRAY;

        for (IpnbEditablePanel editablePanel : panels) {
          if (!(editablePanel instanceof IpnbCodePanel)) continue;
          final Editor editor = ((IpnbCodePanel)editablePanel).getEditor();
          final IpnbPyFragment psiFile =
            (IpnbPyFragment)PsiDocumentManager.getInstance(myElement.getProject()).getPsiFile(editor.getDocument());
          if (psiFile == null) continue;
          final PyResolveProcessor processor = new PyResolveProcessor(referencedName);

          PyResolveUtil.scopeCrawlUp(processor, psiFile, referencedName, psiFile);
          final List<RatedResolveResult> resultList = getResultsFromProcessor(referencedName, processor, psiFile, psiFile);
          if (resultList.size() > 0) {
            List<RatedResolveResult> ret = RatedResolveResult.sorted(resultList);
            return ret.toArray(new RatedResolveResult[ret.size()]);
          }
        }
      }
    }
    return results;
  }
}
