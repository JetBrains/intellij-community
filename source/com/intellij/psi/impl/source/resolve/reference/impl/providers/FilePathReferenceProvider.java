package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ProcessorRegistry;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author cdr
 */
public class FilePathReferenceProvider implements PsiReferenceProvider {
  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, String text, int offset, final boolean soft) {
    return new FileReferenceSet(text, element, offset, ReferenceType.FILE_TYPE, this, true) {
      protected boolean isSoft() {
        return soft;
      }

      @NotNull public Collection<PsiFileSystemItem> computeDefaultContexts() {
        final Module module = ModuleUtil.findModuleForPsiElement(getElement());
        return getRoots(module, true);
      }

      public FileReference createFileReference(final TextRange range, final int index, final String text) {
        return FilePathReferenceProvider.this.createFileReference(this, range, index, text);
      }

      protected PsiScopeProcessor createProcessor(final List<CandidateInfo> result, List<Class> allowedClasses, List<PsiConflictResolver> resolvers)
        throws ProcessorRegistry.IncompatibleReferenceTypeException {
        final PsiScopeProcessor baseProcessor = super.createProcessor(result, allowedClasses, resolvers);
        return new PsiScopeProcessor() {
          public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
            return element instanceof PsiJavaFile && element instanceof PsiCompiledElement
                   || baseProcessor.execute(element, substitutor);
          }

          public <T> T getHint(Class<T> hintClass) {
            return baseProcessor.getHint(hintClass);
          }

          public void handleEvent(Event event, Object associated) {
            baseProcessor.handleEvent(event, associated);
          }
        };
      }
    }.getAllReferences();

  }

  protected FileReference createFileReference(FileReferenceSet referenceSet, final TextRange range, final int index, final String text) {
       return new FileReference(referenceSet, range, index, text);
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element) {
    String text = null;
    if (element instanceof PsiLiteralExpression) {
      Object value = ((PsiLiteralExpression)element).getValue();
      if (value instanceof String) {
        text = (String)value;
      }
    }
    //else if (element instanceof XmlAttributeValue) {
    //  text = ((XmlAttributeValue)element).getValue();
    //}
    if (text == null) return PsiReference.EMPTY_ARRAY;
    return getReferencesByElement(element, text, 1, true);
  }

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }

  @NotNull
  public static Collection<PsiFileSystemItem> getRoots(final Module thisModule, boolean includingClasses) {
    if (thisModule == null) return Collections.emptyList();
    List<Module> modules = new ArrayList<Module>();
    modules.add(thisModule);
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(thisModule);
    modules.addAll(Arrays.asList(moduleRootManager.getDependencies()));

    List<PsiFileSystemItem> result = new ArrayList<PsiFileSystemItem>();
    final PsiManager psiManager = PsiManager.getInstance(thisModule.getProject());
    if (includingClasses) {
      String[] libraryUrls = moduleRootManager.getUrls(OrderRootType.CLASSES);
      for (String libraryUrl : libraryUrls) {
        VirtualFile libFile = VirtualFileManager.getInstance().findFileByUrl(libraryUrl);
        if (libFile != null) {
          PsiDirectory directory = psiManager.findDirectory(libFile);
          if (directory != null) {
            result.add(directory);
          }
        }
      }
    }

    for (Module module : modules) {
      moduleRootManager = ModuleRootManager.getInstance(module);
      VirtualFile[] sourceRoots = moduleRootManager.getSourceRoots();
      for (VirtualFile root : sourceRoots) {
        PsiDirectory directory = psiManager.findDirectory(root);
        if (directory != null) {
          result.add(directory);
        }
      }
    }
    return result;
  }

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position) {
  }

}
