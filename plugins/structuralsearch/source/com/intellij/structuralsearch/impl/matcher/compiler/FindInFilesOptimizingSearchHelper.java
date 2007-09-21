package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.util.Processor;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Maxim.Mossienko
*/
class FindInFilesOptimizingSearchHelper extends OptimizingSearchHelperBase {
  private PsiSearchHelper helper;
  private THashMap<PsiFile,PsiFile> filesToScan;
  private THashMap<PsiFile,PsiFile> filesToScan2;

  private boolean findMatchingFiles;

  FindInFilesOptimizingSearchHelper(CompileContext _context, boolean _findMatchngFiles, Project project) {
    super(_context);
    findMatchingFiles = _findMatchngFiles;

    if (findMatchingFiles) {
      helper = PsiManager.getInstance(project).getSearchHelper();

      if (filesToScan == null) {
        filesToScan = new THashMap<PsiFile,PsiFile>(TObjectHashingStrategy.CANONICAL);
        filesToScan2 = new THashMap<PsiFile,PsiFile>(TObjectHashingStrategy.CANONICAL);
      }
    }
  }

  public boolean doOptimizing() {
    return findMatchingFiles;
  }

  public void clear() {
    super.clear();

    if (filesToScan != null) {
      filesToScan.clear();
      filesToScan2.clear();

      helper = null;
    }
  }

  protected void doAddSearchJavaReservedWordInCode(final String refname) {
    helper.processAllFilesWithWordInText(refname, (GlobalSearchScope)context.options.getScope(), new MyFileProcessor(), true);
  }

  protected void doAddSearchWordInCode(final String refname) {
    helper.processAllFilesWithWord(refname, (GlobalSearchScope)context.options.getScope(), new MyFileProcessor(), true);
  }

  protected void doAddSearchWordInComments(final String refname) {
    helper.processAllFilesWithWordInComments(refname,
                                                       (GlobalSearchScope)context.options.getScope(),
                                                       new MyFileProcessor()
      );
  }

  protected void doAddSearchWordInLiterals(final String refname) {
    helper.processAllFilesWithWordInLiterals(refname,
                                                       (GlobalSearchScope)context.options.getScope(),
                                                       new MyFileProcessor());
  }

  public void endTransaction() {
    super.endTransaction();
    THashMap<PsiFile,PsiFile> map = filesToScan;
    if (map.size() > 0) map.clear();
    filesToScan = filesToScan2;
    filesToScan2 = map;
  }

  public boolean addDescendantsOf(final String refname, final boolean subtype) {
    List classes = buildDescendants(refname,subtype);

    for (final Object aClass : classes) {
      final PsiClass clazz = (PsiClass)aClass;
      String text;

      if (clazz instanceof PsiAnonymousClass) {
        text = ((PsiAnonymousClass)clazz).getBaseClassReference().getReferenceName();
      }
      else {
        text = clazz.getName();
      }

      addWordToSearchInCode(text);
    }

    return (classes.size()>0);
  }

  private List<PsiElement> buildDescendants(String className, boolean includeSelf) {
    if (!doOptimizing()) return Collections.emptyList();
    PsiShortNamesCache cache = PsiManager.getInstance(context.project).getShortNamesCache();
    SearchScope scope = context.options.getScope();
    PsiClass[] classes = cache.getClassesByName(className,(GlobalSearchScope)scope);
    final List<PsiElement> results = new ArrayList<PsiElement>();

    PsiElementProcessor<PsiClass> processor = new PsiElementProcessor<PsiClass>() {
      public boolean execute(PsiClass element) {
        results.add(element);
        return true;
      }

    };

    for (PsiClass aClass : classes) {
      helper.processInheritors(
        processor,
        aClass,
        scope,
        true
      );
    }

    if (includeSelf) {
      for (PsiClass aClass : classes) {
        results.add(aClass);
      }
    }

    return results;
  }

  public Set<PsiFile> getFilesSetToScan() {
    return filesToScan.keySet();
  }

  private class MyFileProcessor implements Processor<PsiFile> {
    public boolean process(PsiFile file) {
      if (scanRequest == 0 ||
          filesToScan.get(file)!=null) {
        filesToScan2.put(file,file);
      }
      return true;
    }
  }

}