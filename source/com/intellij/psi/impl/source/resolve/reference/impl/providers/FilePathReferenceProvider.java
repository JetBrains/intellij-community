package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ProcessorRegistry;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author cdr
 */
public class FilePathReferenceProvider implements PsiReferenceProvider {
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
    return new FileReferenceSet(text, element, 1, ReferenceType.FILE_TYPE, this, true) {
      protected boolean isSoft() {
        return true;
      }

      @NotNull public Collection<PsiElement> getDefaultContexts(PsiElement position) {
        return getRoots(position);
      }

      protected PsiScopeProcessor createProcessor(final List result, ReferenceType type)
        throws ProcessorRegistry.IncompatibleReferenceTypeException {
        final PsiScopeProcessor baseProcessor = super.createProcessor(result, type);
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

  @NotNull
  public PsiReference[] getReferencesByElement(PsiElement element, ReferenceType type) {
    return getReferencesByElement(element);
  }

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, ReferenceType type, int offsetInPosition) {
    return getReferencesByElement(position);
  }

  @NotNull private static Collection<PsiElement> getRoots(PsiElement element) {
    Module thisModule = ModuleUtil.findModuleForPsiElement(element);
    if (thisModule == null) return Collections.emptyList();
    List<Module> modules = new ArrayList<Module>();
    modules.add(thisModule);
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(thisModule);
    modules.addAll(Arrays.asList(moduleRootManager.getDependencies()));

    List<PsiElement> result = new ArrayList<PsiElement>();

    String[] libraryUrls = moduleRootManager.getUrls(OrderRootType.CLASSES);
    for (String libraryUrl : libraryUrls) {
      VirtualFile libFile = VirtualFileManager.getInstance().findFileByUrl(libraryUrl);
      if (libFile != null) {
        PsiDirectory directory = element.getManager().findDirectory(libFile);
        if (directory != null) {
          result.add(directory);
        }
      }
    }

    for (Module module : modules) {
      moduleRootManager = ModuleRootManager.getInstance(module);
      VirtualFile[] sourceRoots = moduleRootManager.getSourceRoots();
      for (VirtualFile root : sourceRoots) {
        PsiDirectory directory = element.getManager().findDirectory(root);
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
