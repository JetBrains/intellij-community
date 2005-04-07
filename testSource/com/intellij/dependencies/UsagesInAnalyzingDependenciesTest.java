package com.intellij.dependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.packageDependencies.BackwardDependenciesBuilder;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.packageDependencies.FindDependencyUtil;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.PsiTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageTreeColorsScheme;
import com.intellij.usageView.impl.UTUsageNode;

import java.util.HashSet;
import java.util.Set;

/**
 * User: anna
 * Date: Jan 18, 2005
 */
public class UsagesInAnalyzingDependenciesTest extends PsiTestCase{
  protected void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        public void run() {
          try{
            String root = PathManagerEx.getTestDataPath() + "/dependencies/search/" + getTestName(true);
            PsiTestUtil.removeAllRoots(myModule, JavaSdkImpl.getMockJdk("java 1.4"));
            PsiTestUtil.createTestProjectStructure(myProject, myModule, root, myFilesToDelete);
          }
          catch(Exception e){
            LOG.error(e);
          }
        }
      }
    );
  }

  public void testForwardPackageScope(){
    final PsiPackage bPackage = myPsiManager.findPackage("com.b");
    final DependenciesBuilder builder = new ForwardDependenciesBuilder(myProject, new AnalysisScope(bPackage));
    builder.analyze();
    final Set<PsiFile> searchFor = new HashSet<PsiFile>();
    searchFor.add(myPsiManager.findClass("com.a.A", GlobalSearchScope.allScope(myProject)).getContainingFile());
    final Set<PsiFile> searchIn = new HashSet<PsiFile>();
    final PsiClass bClass = myPsiManager.findClass("com.b.B", GlobalSearchScope.allScope(myProject));
    searchIn.add(bClass.getContainingFile());
    final PsiClass cClass = myPsiManager.findClass("com.b.C", GlobalSearchScope.allScope(myProject));
    searchIn.add(cClass.getContainingFile());
    final UsageInfo[] usagesInfos = FindDependencyUtil.findDependencies(builder, searchIn, searchFor);
    final String [] psiUsages = new String [usagesInfos.length];
    for (int i = 0; i < usagesInfos.length; i++) {
      final PsiFile psiFile = usagesInfos[i].getElement().getContainingFile();
      final Document document = getDocument(psiFile);
      final LexerEditorHighlighter highlighter = getHighlighter(psiFile, document);
      psiUsages[i] = new UTUsageNode(usagesInfos[i], psiFile, highlighter, document, false, false).toString();
    }
    checkResult(new String []{"(4, 3) A myA = new A();", "(4, 15) A myA = new A();", "(6, 9) myA.aa();",
                              "(4, 3) A myA = new A();", "(4, 15) A myA = new A();", "(6, 9) myA.aa();"}, psiUsages);
  }

   public void testBackwardPackageScope(){
    final PsiPackage bPackage = myPsiManager.findPackage("com.a");
    final DependenciesBuilder builder = new BackwardDependenciesBuilder(myProject, new AnalysisScope(bPackage));
    builder.analyze();
    final Set<PsiFile> searchFor = new HashSet<PsiFile>();
    searchFor.add(myPsiManager.findClass("com.a.A", GlobalSearchScope.allScope(myProject)).getContainingFile());
    final Set<PsiFile> searchIn = new HashSet<PsiFile>();
    final PsiClass bClass = myPsiManager.findClass("com.b.B", GlobalSearchScope.allScope(myProject));
    searchIn.add(bClass.getContainingFile());
    final PsiClass cClass = myPsiManager.findClass("com.a.C", GlobalSearchScope.allScope(myProject));
    searchFor.add(cClass.getContainingFile());
    final UsageInfo[] usagesInfos = FindDependencyUtil.findBackwardDependencies(builder, searchIn, searchFor);
    final String [] psiUsages = new String [usagesInfos.length];
    for (int i = 0; i < usagesInfos.length; i++) {
      final PsiFile psiFile = usagesInfos[i].getElement().getContainingFile();
      final Document document = getDocument(psiFile);
      final LexerEditorHighlighter highlighter = getHighlighter(psiFile, document);
      psiUsages[i] = new UTUsageNode(usagesInfos[i], psiFile, highlighter, document, false, false).toString();
    }
    checkResult(new String []{"(4, 3) A myA = new A();", "(4, 15) A myA = new A();", "(5, 3) C myC = new C();", "(5, 15) C myC = new C();", "(7, 9) myA.aa();", "(8, 9) myC.cc();"}, psiUsages);
  }

  public void testForwardSimple(){
    final DependenciesBuilder builder = new ForwardDependenciesBuilder(myProject, new AnalysisScope(myProject));
    builder.analyze();

    final Set<PsiFile> searchIn = new HashSet<PsiFile>();
    final PsiClass aClass = myPsiManager.findClass("A", GlobalSearchScope.allScope(myProject));
    searchIn.add(aClass.getContainingFile());
    final Set<PsiFile> searchFor = new HashSet<PsiFile>();
    final PsiClass bClass = myPsiManager.findClass("B", GlobalSearchScope.allScope(myProject));
    searchFor.add(bClass.getContainingFile());

    final UsageInfo[] usagesInfos = FindDependencyUtil.findDependencies(builder, searchIn, searchFor);

    final PsiFile psiFile = aClass.getContainingFile();
    final Document document = getDocument(psiFile);
    final LexerEditorHighlighter highlighter = getHighlighter(psiFile, document);
    final String [] psiUsages = new String [usagesInfos.length];
    for (int i = 0; i < usagesInfos.length; i++) {
      psiUsages[i] = new UTUsageNode(usagesInfos[i], psiFile, highlighter, document, false, false).toString();
    }
    checkResult(new String []{"(2, 3) B myB = new B();", "(2, 15) B myB = new B();", "(4, 9) myB.bb();"}, psiUsages);
  }

  private LexerEditorHighlighter getHighlighter(final PsiFile psiFile, final Document document) {
    final LexerEditorHighlighter highlighter = HighlighterFactory.createHighlighter(UsageTreeColorsScheme.getInstance().getScheme(),
                                                                                    psiFile.getName(), myProject);
    highlighter.setText(document.getCharsSequence());
    return highlighter;
  }

  private Document getDocument(PsiFile psiFile){
    return PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
  }

  private void checkResult(final String[] usages, final String [] psiUsages) {
    assertEquals(usages.length , psiUsages.length);
    for (int i = 0; i < psiUsages.length; i++) {
      assertEquals(usages[i], psiUsages[i]);
    }
  }
}
