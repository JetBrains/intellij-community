package com.intellij.codeInspection.sameReturnValue;

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
import com.intellij.psi.PsiMethod;

/**
 * @author max
 */
public class SameReturnValueInspection extends DescriptorProviderInspection {
  public SameReturnValueInspection() {
  }

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

    if (!refMethod.getSuperMethods().isEmpty()) return null;

    String returnValue = refMethod.getReturnValueIfSame();
    if (returnValue != null) {
      final String messagePrefix;
      if (refMethod.getDerivedMethods().isEmpty()) {
        messagePrefix = "Method always returns <code>";
      } else if (refMethod.hasBody()) {
        messagePrefix = "Method and all its deriveables always return <code>";
      } else {
        messagePrefix = "All implementations of this method always return <code>";
      }

      return new ProblemDescriptor[] {getManager().createProblemDescriptor(refMethod.getElement(), messagePrefix + returnValue + "</code>.", (LocalQuickFix [])null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
    }

    return null;
  }

  public boolean queryExternalUsagesRequests() {
    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (getDescriptions(refElement) != null) {
          refElement.accept(new RefVisitor() {
            public void visitMethod(final RefMethod refMethod) {
              getManager().enqueueDerivedMethodsProcessing(refMethod, new InspectionManagerEx.DerivedMethodsProcessor() {
                public boolean process(PsiMethod derivedMethod) {
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
    return "Method returns the same value";
  }

  public String getGroupDisplayName() {
    return "Declaration Redundancy";
  }

  public String getShortName() {
    return "SameReturnValue";
  }
}
