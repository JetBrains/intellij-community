// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.binding;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author max
 */
public class FormReferencesSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final ReferencesSearch.SearchParameters p, @NotNull final Processor<? super PsiReference> consumer) {
    SearchScope userScope = p.getScopeDeterminedByUser();
    if (!scopeCanContainForms(userScope)) return true;
    final PsiElement refElement = p.getElementToSearch();
    final PsiFile psiFile = ReadAction.compute(() -> {
      if (!refElement.isValid()) return null;
      return refElement.getContainingFile();
    });
    if (psiFile == null) return true;
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return true;
    final GlobalSearchScope[] scope = new GlobalSearchScope[1];
    Project project = ReadAction.compute(() -> {
      Project project1 = psiFile.getProject();
      Module module = ProjectRootManager.getInstance(project1).getFileIndex().getModuleForFile(virtualFile);
      if (module != null) {
        scope[0] = GlobalSearchScope.moduleWithDependenciesScope(module);
      }
      return project1;
    });
    if (scope[0] == null) {
      return true;
    }
    final LocalSearchScope filterScope = userScope instanceof LocalSearchScope ? (LocalSearchScope)userScope : null;

    PsiManager psiManager = PsiManager.getInstance(project);
    if (refElement instanceof PsiPackage) {
      //no need to do anything
      //if (!UIFormUtil.processReferencesInUIForms(consumer, (PsiPackage)refElement, scope)) return false;
    }
    else if (refElement instanceof PsiClass) {
      if (!processReferencesInUIForms(consumer, psiManager,(PsiClass)refElement, scope[0], filterScope)) return false;
    }
    else if (refElement instanceof PsiEnumConstant) {
      if (!processEnumReferencesInUIForms(consumer, psiManager, (PsiEnumConstant)refElement, scope[0], filterScope)) return false;
    }
    else if (refElement instanceof PsiField) {
      if (!processReferencesInUIForms(consumer, psiManager, (PsiField)refElement, scope[0], filterScope)) return false;
    }
    else if (refElement instanceof IProperty) {
      if (!processReferencesInUIForms(consumer, psiManager, (Property)refElement, scope[0], filterScope)) return false;
    }
    else if (refElement instanceof PropertiesFile) {
      if (!processReferencesInUIForms(consumer, psiManager, (PropertiesFile)refElement, scope[0], filterScope)) return false;
    }

    return true;
  }

  private static boolean scopeCanContainForms(SearchScope scope) {
    if (!(scope instanceof LocalSearchScope)) return true;
    LocalSearchScope localSearchScope = (LocalSearchScope) scope;
    final PsiElement[] elements = localSearchScope.getScope();
    for (final PsiElement element : elements) {
      if (element instanceof PsiDirectory) return true;
      boolean isForm = ReadAction.compute(() -> {
        PsiFile file;
        if (element instanceof PsiFile) {
          file = (PsiFile)element;
        }
        else {
          if (!element.isValid()) return false;
          file = element.getContainingFile();
        }
        return file.getFileType() == StdFileTypes.GUI_DESIGNER_FORM;
      });
      if (isForm) return true;
    }
    return false;
  }

  private static boolean processReferencesInUIForms(Processor<? super PsiReference> processor,
                                                    PsiManager psiManager, final PsiClass aClass,
                                                    GlobalSearchScope scope, final LocalSearchScope filterScope) {
    String className = getQualifiedName(aClass);
    return className == null || processReferencesInUIFormsInner(className, aClass, processor, scope, psiManager, filterScope);
  }

  public static String getQualifiedName(final PsiClass aClass) {
    return ReadAction.compute(() -> {
      if (!aClass.isValid()) return null;
      return aClass.getQualifiedName();
    });
  }

  private static boolean processEnumReferencesInUIForms(Processor<? super PsiReference> processor,
                                                        PsiManager psiManager, final PsiEnumConstant enumConstant,
                                                        GlobalSearchScope scope, final LocalSearchScope filterScope) {
    String className = ReadAction.compute(() -> enumConstant.getName());
    return className == null || processReferencesInUIFormsInner(className, enumConstant, processor, scope, psiManager, filterScope);
  }

  private static boolean processReferencesInUIFormsInner(String name,
                                                         PsiElement element,
                                                         Processor<? super PsiReference> processor,
                                                         GlobalSearchScope scope1,
                                                         PsiManager manager,
                                                         final LocalSearchScope filterScope) {
    GlobalSearchScope scope = GlobalSearchScope.projectScope(manager.getProject()).intersectWith(scope1);
    List<PsiFile> files = FormClassIndex.findFormsBoundToClass(manager.getProject(), name, scope);

    return processReferencesInFiles(files, manager, name, element, filterScope, processor);
  }

  private static boolean processReferencesInUIForms(Processor<? super PsiReference> processor,
                                                    PsiManager psiManager,
                                                    PsiField field,
                                                    GlobalSearchScope scope1,
                                                    LocalSearchScope filterScope) {
    GlobalSearchScope scope = GlobalSearchScope.projectScope(psiManager.getProject()).intersectWith(scope1);
    PsiClass containingClass = ReadAction.compute(() -> field.getContainingClass());
    if (containingClass == null) return true;
    String fieldName = ReadAction.compute(() -> field.getName());
    final List<PsiFile> files = FormClassIndex.findFormsBoundToClass(psiManager.getProject(), containingClass, scope);
    return processReferencesInFiles(files, psiManager, fieldName, field, filterScope, processor);
  }

  private static boolean processReferences(final Processor<? super PsiReference> processor,
                                           final PsiFile file,
                                           String name,
                                           final PsiElement element,
                                           final LocalSearchScope filterScope) {
    CharSequence chars = ApplicationManager.getApplication().runReadAction((NullableComputable<CharSequence>)() -> {
      if (filterScope != null) {
        boolean isInScope = false;
        for(PsiElement filterElement: filterScope.getScope()) {
          if (PsiTreeUtil.isAncestor(filterElement, file, false)) {
            isInScope = true;
            break;
          }
        }
        if (!isInScope) return null;
      }
      return file.getViewProvider().getContents();
  });
    if (chars == null) return true;
    int index = 0;
    final int offset = name.lastIndexOf('.');
    while(true){
      index = CharArrayUtil.indexOf(chars, name, index);

      if (index < 0) break;
      final int finalIndex = index;
      final Boolean searchDone = ApplicationManager.getApplication().runReadAction((NullableComputable<Boolean>)() -> {
        final PsiReference ref = file.findReferenceAt(finalIndex + offset + 1);
        if (ref != null && ref.isReferenceTo(element)) {
          return processor.process(ref);
        }
        return true;
      });
      if (!searchDone.booleanValue()) return false;
      index++;
    }

    return true;
  }

  private static boolean processReferencesInUIForms(final Processor<? super PsiReference> processor,
                                                    PsiManager psiManager,
                                                    final Property property,
                                                    final GlobalSearchScope globalSearchScope,
                                                    final LocalSearchScope filterScope) {
    final Project project = psiManager.getProject();

    final GlobalSearchScope scope = GlobalSearchScope.projectScope(project).intersectWith(globalSearchScope);
    String name = ReadAction.compute(() -> property.getName());
    if (name == null) return true;

    psiManager.startBatchFilesProcessingMode();

    try {
      CommonProcessors.CollectProcessor<VirtualFile> collector = new CommonProcessors.CollectProcessor<VirtualFile>() {
        @Override
        protected boolean accept(VirtualFile virtualFile) {
          return virtualFile.getFileType() == StdFileTypes.GUI_DESIGNER_FORM;
        }
      };
      ((PsiSearchHelperImpl)PsiSearchHelper.getInstance(project)).processFilesWithText(
        scope, UsageSearchContext.IN_PLAIN_TEXT, true, name, collector
      );
      
      for (final VirtualFile vfile:collector.getResults()) {
        ProgressManager.checkCanceled();

        PsiFile file = ReadAction.compute(() -> PsiManager.getInstance(project).findFile(vfile));
        if (!processReferences(processor, file, name, property, filterScope)) return false;
      }
    }
    finally {
      psiManager.finishBatchFilesProcessingMode();
    }

    return true;
  }

  private static boolean processReferencesInUIForms(final Processor<? super PsiReference> processor,
                                                    PsiManager psiManager,
                                                    final PropertiesFile propFile,
                                                    final GlobalSearchScope globalSearchScope,
                                                    final LocalSearchScope filterScope) {
    final Project project = psiManager.getProject();
    GlobalSearchScope scope = GlobalSearchScope.projectScope(project).intersectWith(globalSearchScope);
    final String baseName = ReadAction.compute(() -> propFile.getResourceBundle().getBaseName());
    PsiFile containingFile = ReadAction.compute(() -> propFile.getContainingFile());

    List<PsiFile> files = Arrays.asList(CacheManager.SERVICE.getInstance(project).getFilesWithWord(baseName, UsageSearchContext.IN_PLAIN_TEXT, scope, true));
    return processReferencesInFiles(files, psiManager, baseName, containingFile, filterScope, processor);
  }

  private static boolean processReferencesInFiles(List<PsiFile> files,
                                                  PsiManager psiManager, String baseName,
                                                  PsiElement element,
                                                  LocalSearchScope filterScope,
                                                  Processor<? super PsiReference> processor) {
    psiManager.startBatchFilesProcessingMode();

    try {
      for (PsiFile file : files) {
        ProgressManager.checkCanceled();

        if (file.getFileType() != StdFileTypes.GUI_DESIGNER_FORM) continue;
        if (!processReferences(processor, file, baseName, element, filterScope)) return false;
      }
    }
    finally {
      psiManager.finishBatchFilesProcessingMode();
    }
    return true;
  }
}
