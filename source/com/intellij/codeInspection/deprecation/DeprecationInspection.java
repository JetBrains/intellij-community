package com.intellij.codeInspection.deprecation;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefMethod;

/**
 * @author max
 */
public class DeprecationInspection extends DescriptorProviderInspection {
  public DeprecationInspection() {
  }

  public void runInspection(AnalysisScope scope) {
    getRefManager().findAllDeclarations();

    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (refElement instanceof RefMethod && ((RefMethod) refElement).isOverridesDeprecated()) {
          addProblemElement(refElement, new ProblemDescriptor[]{getManager().createProblemDescriptor(refElement.getElement(), "Overrides deprecated method.", null, ProblemHighlightType.LIKE_DEPRECATED)});
        } else if (refElement.isUsesDeprecatedApi()) {
          if (getDescriptions(refElement) != null) return;
          addProblemElement(refElement, new ProblemDescriptor[] {getManager().createProblemDescriptor(refElement.getElement(), "Uses deprecated API.", null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)});
        }
      }
    });
  }

  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[] {InspectionManagerEx.BUILD_GRAPH};
  }

  public String getDisplayName() {
    return "Deprecated API usage";
  }

  public String getGroupDisplayName() {
    return "";
  }

  public String getShortName() {
    return "Deprecation";
  }
}
