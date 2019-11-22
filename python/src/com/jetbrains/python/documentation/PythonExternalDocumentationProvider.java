// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.google.common.base.Suppliers;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.pyi.PyiFile;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class PythonExternalDocumentationProvider extends PythonDocumentationProvider implements ExternalDocumentationProvider {
  private static final Logger LOG = Logger.getInstance(PythonExternalDocumentationProvider.class);

  @Override
  public String fetchExternalDocumentation(Project project, PsiElement element, List<String> docUrls) {
    PsiNamedElement namedElement = ApplicationManager.getApplication().runReadAction((Computable<PsiNamedElement>)() -> {
      final Module module = ModuleUtilCore.findModuleForPsiElement(element);
      if (module != null && !PyDocumentationSettings.getInstance(module).isRenderExternalDocumentation()) return null;

      PsiFileSystemItem file = PythonDocumentationProvider.getFile(element);

      if (file == null) return null;

      if (file instanceof PyiFile) {
        return null;
      }

      return PythonDocumentationProvider.getNamedElement(element);
    });


    if (namedElement != null) {
      for (String url : docUrls) {
        Supplier<Document> documentSupplier = Suppliers.memoize(() -> {
          try {
            return Jsoup.parse(new URL(url), 1000);
          }
          catch (IOException e) {
            LOG.error("Can't read external doc URL: " + url, e);
            return null;
          }
        });

        for (final PythonDocumentationLinkProvider documentationLinkProvider :
          PythonDocumentationLinkProvider.EP_NAME.getExtensionList()) {

          Function<Document, String> quickDocExtractor = documentationLinkProvider.quickDocExtractor(namedElement);

          if (quickDocExtractor != null) {
            final Document document = documentSupplier.get();
            if (document != null) {
              String quickDoc = ReadAction.compute(() -> quickDocExtractor.apply(document));
              if (StringUtil.isNotEmpty(quickDoc)) {
                return quickDoc;
              }
            }
          }
        }
      }
    }

    return null;
  }

  @Override
  public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
    return PythonDocumentationProvider.getOnlyUrlFor(element, originalElement) != null;
  }

  @Override
  public boolean canPromptToConfigureDocumentation(@NotNull PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile instanceof PyFile) {
      final Project project = element.getProject();
      final VirtualFile vFile = containingFile.getVirtualFile();
      if (vFile != null && ProjectRootManager.getInstance(project).getFileIndex().isInLibraryClasses(vFile)) {
        final QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(element, element);
        if (qName != null && qName.getComponentCount() > 0) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void promptToConfigureDocumentation(@NotNull PsiElement element) {
    final Project project = element.getProject();
    final QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(element, element);
    if (qName != null && qName.getComponentCount() > 0) {
      showNoExternalDocumentationDialog(project, qName);
    }
  }

  private static void showNoExternalDocumentationDialog(Project project, QualifiedName qName) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final int rc = Messages.showOkCancelDialog(project,
                                                 "No external documentation URL configured for module " + qName.getComponents().get(0) +
                                                 ".\nWould you like to configure it now?",
                                                 "Python External Documentation",
                                                 Messages.getQuestionIcon());
      if (rc == Messages.OK) {
        ShowSettingsUtilImpl.showSettingsDialog(project, DOCUMENTATION_CONFIGURABLE_ID , "");
      }
    }, ModalityState.NON_MODAL);
  }
}
