package com.intellij.packageDependencies;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.RefCountHolder;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;

import java.util.*;

/**
 * User: anna
 * Date: Jan 19, 2005
 */
public abstract class DependenciesBuilder {
  private Project myProject;
  private final AnalysisScope myScope;
  private final Map<PsiFile, Set<PsiFile>> myDependencies = new HashMap<PsiFile, Set<PsiFile>>();
  protected int myTotalFileCount;
  protected int myFileCount = 0;

  protected DependenciesBuilder(final Project project, final AnalysisScope scope) {
    myProject = project;
    myScope = scope;
    myTotalFileCount = scope.getFileCount();
  }

  public void setInitialFileCount(final int fileCount) {
    myFileCount = fileCount;
  }

  public void setTotalFileCount(final int totalFileCount) {
    myTotalFileCount = totalFileCount;
  }

  public Map<PsiFile, Set<PsiFile>> getDependencies() {
    return myDependencies;
  }

  public AnalysisScope getScope() {
    return myScope;
  }

  public Project getProject() {
    return myProject;
  }

  public abstract String getRootNodeNameInUsageView();

  public abstract String getInitialUsagesPosition();

  public abstract boolean isBackward();

  public abstract void analyze();

  public Map<PsiFile, Map<DependencyRule, Set<PsiFile>>> getIllegalDependencies(){
    Map<PsiFile, Map<DependencyRule, Set<PsiFile>>> result = new HashMap<PsiFile, Map<DependencyRule, Set<PsiFile>>>();
    DependencyValidationManager validator = DependencyValidationManager.getInstance(myProject);
    for (Iterator<PsiFile> iterator = myDependencies.keySet().iterator(); iterator.hasNext();) {
      PsiFile file = iterator.next();
      Set<PsiFile> deps = myDependencies.get(file);
      Map<DependencyRule, Set<PsiFile>> illegal = null;
      for (Iterator<PsiFile> depsIterator = deps.iterator(); depsIterator.hasNext();) {
        PsiFile dependency = depsIterator.next();
        final DependencyRule rule = isBackward() ? validator.getViolatorDependencyRule(dependency, file) :
                                                   validator.getViolatorDependencyRule(file, dependency);
        if (rule != null) {
          if (illegal == null) {
            illegal = new HashMap<DependencyRule, Set<PsiFile>>();
            result.put(file, illegal);
          }
          Set<PsiFile> illegalFilesByRule = illegal.get(rule);
          if (illegalFilesByRule == null) {
            illegalFilesByRule = new HashSet<PsiFile>();
          }
          illegalFilesByRule.add(dependency);
          illegal.put(rule, illegalFilesByRule);
        }
      }
    }
    return result;
  }

  public void analyzeFileDependencies(PsiFile file, DependencyProcessor processor) {
    file.accept(new DependenciesWalker(processor));
  }

  public interface DependencyProcessor {
    void process(PsiElement place, PsiElement dependency);
  }


  private static class DependenciesWalker extends PsiRecursiveElementVisitor {
    private final DependencyProcessor myProcessor;

    public DependenciesWalker(DependencyProcessor processor) {
      myProcessor = processor;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public void visitElement(PsiElement element) {
      super.visitElement(element);
      PsiReference[] refs = element.getReferences();
      for (int i = 0; i < refs.length; i++) {
        PsiReference ref = refs[i];
        PsiElement resolved = ref.resolve();
        if (resolved != null) {
          myProcessor.process(ref.getElement(), resolved);
        }
      }
    }

    public void visitDocComment(PsiDocComment comment) {
      //empty
    }

    public void visitImportStatement(PsiImportStatement statement) {
      //empty - to exclude imports from dependency analyzing   
    }

    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiMethod psiMethod = expression.resolveMethod();
      if (psiMethod != null) {
        PsiType returnType = psiMethod.getReturnType();
        if (returnType != null) {
          PsiClass psiClass = PsiUtil.resolveClassInType(returnType);
          if (psiClass != null) {
            myProcessor.process(expression, psiClass);
          }
        }
      }
    }
  }
}
