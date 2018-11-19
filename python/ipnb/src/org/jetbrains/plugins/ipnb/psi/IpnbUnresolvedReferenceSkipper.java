package org.jetbrains.plugins.ipnb.psi;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferenceSkipperExtPoint;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbEditablePanel;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;

import java.util.List;

public class IpnbUnresolvedReferenceSkipper implements PyUnresolvedReferenceSkipperExtPoint {
  @Override
  public boolean unusedImportShouldBeSkipped(@NotNull final PyImportedNameDefiner importNameDefiner) {
    final PsiFile file = importNameDefiner.getContainingFile();
    if (file instanceof IpnbPyFragment) {
      final IpnbFilePanel panel = ((IpnbPyFragment)file).getFilePanel();
      final List<IpnbEditablePanel> panels = panel.getIpnbPanels();

      for (IpnbEditablePanel editablePanel : panels) {
        if (!(editablePanel instanceof IpnbCodePanel)) continue;
        final Editor editor = ((IpnbCodePanel)editablePanel).getEditor();
        final IpnbPyFragment psiFile = (IpnbPyFragment)PsiDocumentManager.getInstance(importNameDefiner.getProject()).getPsiFile(editor.getDocument());
        if (psiFile == null) continue;
        final MyVisitor visitor = new MyVisitor(importNameDefiner);
        psiFile.accept(visitor);
        if (visitor.used) return true;
      }
    }
    return false;
  }

  private static class MyVisitor extends PyRecursiveElementVisitor {
    private final PyImportedNameDefiner myImportNameDefiner;
    boolean used = false;

    MyVisitor(PyImportedNameDefiner importNameDefiner) {
      myImportNameDefiner = importNameDefiner;
    }

    @Override
    public void visitPyElement(PyElement node) {
      super.visitPyElement(node);
      if (node instanceof PyReferenceOwner) {
        final PyResolveContext resolveContext = PyResolveContext.noImplicits();
        final PsiPolyVariantReference reference = ((PyReferenceOwner)node).getReference(resolveContext);
        PsiElement target = null;
        final ResolveResult[] resolveResults = reference.multiResolve(false);
        for (ResolveResult resolveResult : resolveResults) {
          if (target == null && resolveResult.isValidResult()) {
            target = resolveResult.getElement();
          }
          if (resolveResult instanceof ImportedResolveResult) {
            final PyImportedNameDefiner definer = ((ImportedResolveResult)resolveResult).getDefiner();
            if (myImportNameDefiner.equals(definer)) {
              used = true;
            }
          }
        }
      }
    }
  }
}
