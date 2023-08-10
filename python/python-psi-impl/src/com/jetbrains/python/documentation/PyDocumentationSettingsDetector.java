package com.jetbrains.python.documentation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

final class PyDocumentationSettingsDetector
  implements Function<Pair<Module, Collection<VirtualFile>>, PyDocumentationSettings.ServiceState> {

  private static final Logger LOG = Logger.getInstance(PyDocumentationSettingsDetector.class);

  @Override
  public PyDocumentationSettings.ServiceState fun(@NotNull Pair<Module, Collection<VirtualFile>> pair) {
    Module module = pair.first;
    if (module.isDisposed()) {
      return null;
    }
    ApplicationManager.getApplication().assertIsNonDispatchThread();

    for (VirtualFile file : pair.second) {
      DocStringFormat docFormat = ReadAction.compute(() -> checkDocString(file, module));    // detect docstring type
      if (docFormat != PyDocumentationSettings.DEFAULT_DOC_STRING_FORMAT) {
        LOG.debug("Docstring format '" + docFormat + "' was detected from content of the file '" + file.getPath() + "'");
        return new PyDocumentationSettings.ServiceState(docFormat);
      }
    }
    return null;
  }

  @NotNull
  private static DocStringFormat checkDocString(@NotNull VirtualFile file, @NotNull Module module) {
    final PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(file);
    if (psiFile instanceof PyFile) {
      final DocStringFormat perFileFormat = PyDocumentationSettings.getFormatFromDocformatAttribute(psiFile);
      if (perFileFormat != null) {
        return perFileFormat;
      }
      // Why toplevel docstring owners only
      final PyDocStringOwner[] children = PsiTreeUtil.getChildrenOfType(psiFile, PyDocStringOwner.class);
      if (children != null) {
        for (PyDocStringOwner owner : children) {
          final PyStringLiteralExpression docStringExpression = owner.getDocStringExpression();
          if (docStringExpression != null) {
            final DocStringFormat guessed = DocStringUtil.guessDocStringFormat(docStringExpression.getStringValue());
            if (guessed != PyDocumentationSettings.DEFAULT_DOC_STRING_FORMAT) {
              return guessed;
            }
          }
        }
      }
    }
    return PyDocumentationSettings.DEFAULT_DOC_STRING_FORMAT;
  }
}
