package com.intellij.codeInspection.unusedReturnValue;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefMethod;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;

/**
 * @author max
 */
public class UnusedReturnValue extends DescriptorProviderInspection {
  private QuickFix myQuickFix;

  public void runInspection(AnalysisScope scope) {
    getRefManager().findAllDeclarations();

    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (refElement instanceof RefMethod) {
          RefMethod refMethod = (RefMethod) refElement;
          ProblemDescriptor[] descriptors = checkMethod(refMethod);
          if (descriptors != null) {
            addProblemElement(refElement, descriptors);
          }
        }
      }
    });
  }

  private ProblemDescriptor[] checkMethod(RefMethod refMethod) {
    if (refMethod.isConstructor()) return null;
    if (refMethod.isLibraryOverride()) return null;
    if (refMethod.getInReferences().size() == 0) return null;
    if (refMethod.getSuperMethods().size() > 0) return null;

    if (!refMethod.isReturnValueUsed()) {
      return new ProblemDescriptor[]{
        getManager().createProblemDescriptor(refMethod.getElement(), "Return value of the method is never used.",
                                             getFix(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
    }

    return null;
  }

  public boolean queryExternalUsagesRequests() {
    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (getDescriptions(refElement) != null) {
          refElement.accept(new RefVisitor() {
            public void visitMethod(final RefMethod refMethod) {
              getManager().enqueueMethodUsagesProcessor(refMethod, new InspectionManagerEx.UsagesProcessor() {
                public boolean process(PsiReference psiReference) {
                  ignoreElement(refMethod);
                  return false;
                }
              });
            }
          });
        }
      }
    });

    return false;
  }

  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[] {InspectionManagerEx.BUILD_GRAPH, InspectionManagerEx.FIND_EXTERNAL_USAGES};
  }

  public String getDisplayName() {
    return "Unused method return value";
  }

  public String getGroupDisplayName() {
    return "Declaration Redundancy";
  }

  public String getShortName() {
    return "UnusedReturnValue";
  }

  public UnusedReturnValue() {
  }

  private LocalQuickFix getFix() {
    if (myQuickFix == null) {
      myQuickFix = new QuickFix();
    }
    return myQuickFix;
  }

  private class QuickFix implements LocalQuickFix {
    public String getName() {
      return "Make Method void";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      RefElement refElement = getElement(descriptor);
      if (refElement.isValid() && refElement instanceof RefMethod) {
        RefMethod refMethod = (RefMethod)refElement;
        makeMethodVoid(refMethod);
      }
    }

    private void makeMethodVoid(RefMethod refMethod) {
      PsiMethod psiMethod = (PsiMethod) refMethod.getElement();
      if (psiMethod == null) return;
      PsiParameter[] params = psiMethod.getParameterList().getParameters();
      ParameterInfo[] infos = new ParameterInfo[params.length];
      for (int i = 0; i < params.length; i++) {
        PsiParameter param = params[i];
        infos[i] = new ParameterInfo(i, param.getName(), param.getType());
      }

      ChangeSignatureProcessor csp = new ChangeSignatureProcessor(getManager().getProject(),
        psiMethod,
        false, null, psiMethod.getName(),
        PsiType.VOID,
        infos,
        false, BaseRefactoringProcessor.EMPTY_CALLBACK);

      csp.run(null);
    }
  }
}
