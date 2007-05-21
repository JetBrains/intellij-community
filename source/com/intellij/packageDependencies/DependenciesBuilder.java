package com.intellij.packageDependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

  public int getTotalFileCount() {
    return myTotalFileCount;
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
    for (PsiFile file : myDependencies.keySet()) {
      Set<PsiFile> deps = myDependencies.get(file);
      Map<DependencyRule, Set<PsiFile>> illegal = null;
      for (PsiFile dependency : deps) {
        final DependencyRule rule = isBackward() ?
                                    validator.getViolatorDependencyRule(dependency, file) :
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

  public static void analyzeFileDependencies(PsiFile file, DependencyProcessor processor) {
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
      for (PsiReference ref : refs) {
        PsiElement resolved = ref.resolve();
        if (resolved != null) {
          myProcessor.process(ref.getElement(), resolved);
        }
      }
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      // empty
      // TODO: thus we'll skip property references and references to file resources. We can't validate them anyway now since
      // TODO: rule syntax does not allow this.
    }

    public void visitDocComment(PsiDocComment comment) {
      //empty
    }

    public void visitImportStatement(PsiImportStatement statement) {
      if (!DependencyValidationManager.getInstance(statement.getProject()).skipImportStatements()) {
        visitElement(statement);
      }
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
