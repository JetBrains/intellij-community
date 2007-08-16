/*
 * @author max
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.RepositoryElementsManager;
import com.intellij.psi.impl.cache.RepositoryIndex;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedPackagesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

public class AnnotatedPackagesSearcher implements QueryExecutor<PsiPackage, AnnotatedPackagesSearch.Parameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.AnnotatedPackagesSearcher");

  public boolean execute(final AnnotatedPackagesSearch.Parameters p, final Processor<PsiPackage> consumer) {
    final PsiClass annClass = p.getAnnotationClass();
    assert annClass.isAnnotationType() : "Annotation type should be passed to annotated packages search";

    final String annotationFQN = annClass.getQualifiedName();
    assert annotationFQN != null;

    final PsiManagerImpl psiManager = (PsiManagerImpl)PsiManager.getInstance(annClass.getProject());

    RepositoryManager repositoryManager = psiManager.getRepositoryManager();
    RepositoryElementsManager repositoryElementsManager = psiManager.getRepositoryElementsManager();

    RepositoryIndex repositoryIndex = repositoryManager.getIndex();
    final SearchScope useScope = p.getScope();

    final VirtualFileFilter rootFilter;
    if (useScope instanceof GlobalSearchScope) {
      rootFilter = repositoryIndex.rootFilterBySearchScope((GlobalSearchScope)useScope);
    }
    else {
      rootFilter = null;
    }

    final String annotationShortName = annClass.getName();
    assert annotationShortName != null;

    long[] candidateIds = repositoryIndex.getAnnotationNameOccurencesInMemberDecls(annotationShortName, rootFilter);
    for (long candidateId : candidateIds) {
      PsiMember candidate = (PsiMember)repositoryElementsManager.findOrCreatePsiElementById(candidateId);
      if (!(candidate instanceof PsiClass)) continue;
      if (!"package-info".equals(candidate.getName())) continue;

      LOG.assertTrue(candidate.isValid());

      final PsiAnnotation ann = candidate.getModifierList().findAnnotation(annotationFQN);
      if (ann == null) continue;

      final PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
      if (ref == null) continue;

      if (!psiManager.areElementsEquivalent(ref.resolve(), annClass)) continue;
      if (useScope instanceof GlobalSearchScope &&
          !((GlobalSearchScope)useScope).contains(candidate.getContainingFile().getVirtualFile())) {
        continue;
      }
      final String qname = ((PsiClass)candidate).getQualifiedName();
      if (qname != null && !consumer.process(psiManager.findPackage(qname.substring(0, qname.lastIndexOf('.'))))) {
        return false;
      }
    }

    PsiSearchHelper helper = psiManager.getSearchHelper();
    final GlobalSearchScope infoFilesFilter = new PackageInfoFilesOnly();

    GlobalSearchScope infoFiles =
      useScope instanceof GlobalSearchScope ? ((GlobalSearchScope)useScope).intersectWith(infoFilesFilter) : infoFilesFilter;

    final boolean[] wantmore = new boolean[]{true};
    helper.processAllFilesWithWord(annotationShortName, infoFiles, new Processor<PsiFile>() {
      public boolean process(final PsiFile psiFile) {
        PsiPackageStatement stmt = PsiTreeUtil.getChildOfType(psiFile, PsiPackageStatement.class);
        if (stmt == null) return true;

        final PsiModifierList annotations = stmt.getAnnotationList();
        final PsiAnnotation ann = annotations.findAnnotation(annotationFQN);
        if (ann == null) return true;

        final PsiJavaCodeReferenceElement ref = ann.getNameReferenceElement();
        if (ref == null) return true;

        if (!psiManager.areElementsEquivalent(ref.resolve(), annClass)) return true;

        wantmore[0] = consumer.process(psiManager.findPackage(stmt.getPackageName()));
        return wantmore[0];
      }
    }, true);

    return wantmore[0];
  }

  private static class PackageInfoFilesOnly extends GlobalSearchScope {
    public int compare(final VirtualFile file1, final VirtualFile file2) {
      return 0;
    }

    public boolean contains(final VirtualFile file) {
      return "package-info.java".equals(file.getName());
    }

    public boolean isSearchInLibraries() {
      return false;
    }

    public boolean isSearchInModuleContent(@NotNull final Module aModule) {
      return true;
    }
  }
}