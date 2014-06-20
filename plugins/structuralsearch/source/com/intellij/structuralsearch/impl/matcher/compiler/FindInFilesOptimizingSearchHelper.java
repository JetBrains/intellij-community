package com.intellij.structuralsearch.impl.matcher.compiler;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Processor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

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

  private final boolean findMatchingFiles;

  FindInFilesOptimizingSearchHelper(CompileContext _context, boolean _findMatchngFiles, Project project) {
    super(_context);
    findMatchingFiles = _findMatchngFiles;

    if (findMatchingFiles) {
      helper = PsiSearchHelper.SERVICE.getInstance(project);

      if (filesToScan == null) {
        filesToScan = new THashMap<PsiFile,PsiFile>();
        filesToScan2 = new THashMap<PsiFile,PsiFile>();
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
    helper.processAllFilesWithWordInText(refname, (GlobalSearchScope)context.getOptions().getScope(), new MyFileProcessor(), true);
  }

  protected void doAddSearchWordInText(final String refname) {
    helper.processAllFilesWithWordInText(refname, (GlobalSearchScope)context.getOptions().getScope(), new MyFileProcessor(), true);
  }

  protected void doAddSearchWordInCode(final String refname) {
    helper.processAllFilesWithWord(refname, (GlobalSearchScope)context.getOptions().getScope(), new MyFileProcessor(), true);
  }

  protected void doAddSearchWordInComments(final String refname) {
    helper.processAllFilesWithWordInComments(refname, (GlobalSearchScope)context.getOptions().getScope(), new MyFileProcessor());
  }

  protected void doAddSearchWordInLiterals(final String refname) {
    helper.processAllFilesWithWordInLiterals(refname, (GlobalSearchScope)context.getOptions().getScope(), new MyFileProcessor());
  }

  public void endTransaction() {
    super.endTransaction();
    THashMap<PsiFile,PsiFile> map = filesToScan;
    if (map.size() > 0) map.clear();
    filesToScan = filesToScan2;
    filesToScan2 = map;
  }

  public boolean addDescendantsOf(final String refname, final boolean subtype) {
    final List<PsiClass> classes = buildDescendants(refname,subtype);

    for (final PsiClass aClass : classes) {
      if (aClass instanceof PsiAnonymousClass) {
        addWordToSearchInCode(((PsiAnonymousClass)aClass).getBaseClassReference().getReferenceName());
      }
      else {
        addWordToSearchInCode(aClass.getName());
      }
    }

    return classes.size() > 0;
  }

  private List<PsiClass> buildDescendants(String className, boolean includeSelf) {
    if (!doOptimizing()) return Collections.emptyList();
    final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(context.getProject());
    final SearchScope scope = context.getOptions().getScope();
    final PsiClass[] classes = cache.getClassesByName(className,(GlobalSearchScope)scope);
    final List<PsiClass> results = new ArrayList<PsiClass>();

    final PsiElementProcessor<PsiClass> processor = new PsiElementProcessor<PsiClass>() {
      public boolean execute(@NotNull PsiClass element) {
        results.add(element);
        return true;
      }

    };

    for (PsiClass aClass : classes) {
      ClassInheritorsSearch.search(aClass, scope, true).forEach(new PsiElementProcessorAdapter<PsiClass>(processor));
    }

    if (includeSelf) {
      Collections.addAll(results, classes);
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
