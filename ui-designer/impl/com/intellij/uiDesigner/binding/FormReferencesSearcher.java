/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.uiDesigner.binding;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.CharArrayUtil;

import java.util.Set;
import java.util.Arrays;

/**
 * @author max
 */
public class FormReferencesSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(final ReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    final PsiElement refElement = p.getElementToSearch();
    final PsiFile psiFile = refElement.getContainingFile();
    if (psiFile == null) return true;
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    if (virtualFile == null) return true;
    Module module = ProjectRootManager.getInstance(refElement.getProject()).getFileIndex().getModuleForFile(virtualFile);
    if (module == null) return true;
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesScope(module);
    final LocalSearchScope filterScope = p.getScope() instanceof LocalSearchScope
                                         ? (LocalSearchScope) p.getScope()
                                         : null;

    if (refElement instanceof PsiPackage) {
      //no need to do anything
      //if (!UIFormUtil.processReferencesInUIForms(consumer, (PsiPackage)refElement, scope)) return false;
    }
    else if (refElement instanceof PsiClass) {
      if (!processReferencesInUIForms(consumer, (PsiClass)refElement, scope, filterScope)) return false;
    }
    else if (refElement instanceof PsiEnumConstant) {
      if (!processReferencesInUIForms(consumer, (PsiEnumConstant)refElement, scope, filterScope)) return false;
    }
    else if (refElement instanceof PsiField) {
      if (!processReferencesInUIForms(consumer, (PsiField)refElement, scope, filterScope)) return false;
    }
    else if (refElement instanceof Property) {
      if (!processReferencesInUIForms(consumer, (Property)refElement, scope, filterScope)) return false;
    }
    else if (refElement instanceof PropertiesFile) {
      if (!processReferencesInUIForms(consumer, (PropertiesFile)refElement, scope, filterScope)) return false;
    }

    return true;
  }

  private static boolean processReferencesInUIForms(Processor<PsiReference> processor,
                                                   PsiClass aClass,
                                                   GlobalSearchScope scope, final LocalSearchScope filterScope) {
    PsiManagerImpl manager = (PsiManagerImpl)aClass.getManager();
    String className = aClass.getQualifiedName();
    if (className == null) return true;

    return processReferencesInUIFormsInner(className, aClass, processor, scope, manager, filterScope);
  }

  private static boolean processReferencesInUIForms(Processor<PsiReference> processor,
                                                   PsiEnumConstant enumConstant,
                                                   GlobalSearchScope scope, final LocalSearchScope filterScope) {
    PsiManagerImpl manager = (PsiManagerImpl)enumConstant.getManager();
    String className = enumConstant.getName();
    if (className == null) return true;

    return processReferencesInUIFormsInner(className, enumConstant, processor, scope, manager, filterScope);
  }

  public static boolean processReferencesInUIForms(Processor<PsiReference> processor,
                                                         PsiPackage aPackage,
                                                         GlobalSearchScope scope) {
    String packageName = aPackage.getQualifiedName();
    return processReferencesInUIFormsInner(packageName, aPackage, processor, scope, (PsiManagerImpl)aPackage.getManager(), null);
  }

  private static boolean processReferencesInUIFormsInner(String name,
                                                         PsiElement element, Processor<PsiReference> processor,
                                                         GlobalSearchScope scope1,
                                                         PsiManagerImpl manager,
                                                         final LocalSearchScope filterScope) {
    GlobalSearchScope scope = GlobalSearchScope.projectScope(manager.getProject()).intersectWith(scope1);
    manager.startBatchFilesProcessingMode();

    try {
      PsiFile[] files = manager.getCacheManager().getFilesWithWord(name, UsageSearchContext.IN_FOREIGN_LANGUAGES, scope, true);

      for (PsiFile file : files) {
        ProgressManager.getInstance().checkCanceled();

        if (file.getFileType() != StdFileTypes.GUI_DESIGNER_FORM) continue;
        if (!processReferences(processor, file, name, element, filterScope)) return false;
      }
    }
    finally {
      manager.finishBatchFilesProcessingMode();
    }

    return true;
  }

  private static boolean processReferencesInUIForms(Processor<PsiReference> processor,
                                                   PsiField field,
                                                   GlobalSearchScope scope1,
                                                   LocalSearchScope filterScope) {
    GlobalSearchScope scope = GlobalSearchScope.projectScope(field.getProject()).intersectWith(scope1);
    PsiManagerImpl manager = (PsiManagerImpl)field.getManager();
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return true;
    String qClassName = containingClass.getQualifiedName();
    if (qClassName == null) return true;

    String fieldName = field.getName();
    manager.startBatchFilesProcessingMode();

    try {
      PsiFile[] files = manager.getCacheManager().getFilesWithWord(qClassName, UsageSearchContext.IN_FOREIGN_LANGUAGES, scope, true);

      for (PsiFile file : files) {
        ProgressManager.getInstance().checkCanceled();

        if (file.getFileType() != StdFileTypes.GUI_DESIGNER_FORM) continue;
        if (!processReferences(processor, file, fieldName, field, filterScope)) return false;
      }
    }
    finally {
      manager.finishBatchFilesProcessingMode();
    }

    return true;
  }

  private static boolean processReferences(Processor<PsiReference> processor, PsiFile file, String name, PsiElement element,
                                           final LocalSearchScope filterScope) {
    if (filterScope != null) {
      boolean isInScope = false;
      for(PsiElement filterElement: filterScope.getScope()) {
        if (PsiTreeUtil.isAncestor(filterElement, file, false)) {
          isInScope = true;
          break;
        }
      }
      if (!isInScope) return true;
    }
    char[] chars = file.textToCharArray();
    int index = 0;
    final int offset = name.lastIndexOf('.');
    while(true){
      index = CharArrayUtil.indexOf(chars, name, index);

      if (index < 0) break;
      PsiReference ref = file.findReferenceAt(index + offset + 1);
      if (ref != null && ref.isReferenceTo(element)){
        if (!processor.process(ref)) return false;
      }
      index++;
    }

    return true;
  }

  private static boolean processReferencesInUIForms(final Processor<PsiReference> processor,
                                                   final Property property,
                                                   final GlobalSearchScope globalSearchScope,
                                                   final LocalSearchScope filterScope) {

    GlobalSearchScope scope = GlobalSearchScope.projectScope(property.getProject()).intersectWith(globalSearchScope);
    PsiManagerImpl manager = (PsiManagerImpl)property.getManager();
    String name = property.getName();
    if (name == null) return true;

    manager.startBatchFilesProcessingMode();

    try {
      String[] words = StringUtil.getWordsIn(name).toArray(ArrayUtil.EMPTY_STRING_ARRAY);
      if(words.length == 0) return true;

      Set<PsiFile> fileSet = new HashSet<PsiFile>();
      fileSet.addAll(Arrays.asList(manager.getCacheManager().getFilesWithWord(words[0], UsageSearchContext.IN_PLAIN_TEXT, scope, true)));
      for (int i = 1; i < words.length; i++) {
        fileSet.retainAll(Arrays.asList(manager.getCacheManager().getFilesWithWord(words[i], UsageSearchContext.IN_PLAIN_TEXT, scope, true)));
      }
      PsiFile[] files = fileSet.toArray(new PsiFile[fileSet.size()]);

      for (PsiFile file : files) {
        ProgressManager.getInstance().checkCanceled();

        if (file.getFileType() != StdFileTypes.GUI_DESIGNER_FORM) continue;
        if (!processReferences(processor, file, name, property, filterScope)) return false;
      }
    }
    finally {
      manager.finishBatchFilesProcessingMode();
    }

    return true;
  }

  private static boolean processReferencesInUIForms(final Processor<PsiReference> processor, final PropertiesFile propFile, final GlobalSearchScope globalSearchScope,
                                                   final LocalSearchScope filterScope) {
    GlobalSearchScope scope = GlobalSearchScope.projectScope(propFile.getProject()).intersectWith(globalSearchScope);
    PsiManagerImpl manager = (PsiManagerImpl)propFile.getManager();
    final String baseName = propFile.getResourceBundle().getBaseName();
    manager.startBatchFilesProcessingMode();

    try {
      PsiFile[] files = manager.getCacheManager().getFilesWithWord(baseName, UsageSearchContext.IN_PLAIN_TEXT, scope, true);

      for (PsiFile file : files) {
        ProgressManager.getInstance().checkCanceled();

        if (file.getFileType() != StdFileTypes.GUI_DESIGNER_FORM) continue;
        if (!processReferences(processor, file, baseName, propFile, filterScope)) return false;
      }
    }
    finally {
      manager.finishBatchFilesProcessingMode();
    }

    return true;
  }
}
