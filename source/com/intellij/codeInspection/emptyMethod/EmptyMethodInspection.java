package com.intellij.codeInspection.emptyMethod;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author max
 */
public class EmptyMethodInspection extends DescriptorProviderInspection {
  public static final String DISPLAY_NAME = InspectionsBundle.message("inspection.empty.method.display.name");
  private QuickFix myQuickFix;
  @NonNls public static final String SHORT_NAME = "EmptyMethod";

  public void runInspection(AnalysisScope scope) {
    getRefManager().findAllDeclarations();

    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (refElement instanceof RefMethod) {
          RefMethod refMethod = (RefMethod)refElement;
          if (!InspectionManagerEx.isToCheckMember((PsiDocCommentOwner) refMethod.getElement(), EmptyMethodInspection.this.getShortName())) return;
          ProblemDescriptor[] descriptors = checkMethod(refMethod);
          if (descriptors != null) {
            addProblemElement(refElement, descriptors);
          }
        }
      }
    });
  }

  @Nullable
  private ProblemDescriptor[] checkMethod(RefMethod refMethod) {
    if (!refMethod.isBodyEmpty()) return null;
    if (refMethod.isConstructor()) return null;
    if (refMethod.isSyntheticJSP()) return null;

    for (RefMethod refSuper : refMethod.getSuperMethods()) {
      if (checkMethod(refSuper) != null) return null;
    }

    String message = null;
    if (refMethod.isOnlyCallsSuper()) {
      RefMethod refSuper = findSuperWithBody(refMethod);
      if (refSuper == null || RefUtil.compareAccess(refMethod.getAccessModifier(), refSuper.getAccessModifier()) <= 0) {
        message = InspectionsBundle.message("inspection.empty.method.problem.descriptor");
      }
    }
    else if (refMethod.hasBody() && hasEmptySuperImplementation(refMethod)) {
      message = InspectionsBundle.message("inspection.empty.method.problem.descriptor1");
    }
    else if (areAllImplementationsEmpty(refMethod)) {
      if (refMethod.hasBody()) {
        if (refMethod.getDerivedMethods().size() == 0) {
          if (refMethod.getSuperMethods().size() == 0) {
            message = InspectionsBundle.message("inspection.empty.method.problem.descriptor2");
          }
        }
        else {
          message = InspectionsBundle.message("inspection.empty.method.problem.descriptor3");
        }
      }
      else {
        if (refMethod.getDerivedMethods().size() > 0) {
          message = InspectionsBundle.message("inspection.empty.method.problem.descriptor4");
        }
      }
    }

    if (message != null) {
      return new ProblemDescriptor[]{
        getManager().createProblemDescriptor(refMethod.getElement(), message, getFix(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};
    }

    return null;
  }

  private static RefMethod findSuperWithBody(RefMethod refMethod) {
    for (RefMethod refSuper : refMethod.getSuperMethods()) {
      if (refSuper.hasBody()) return refSuper;
    }
    return null;
  }

  private boolean areAllImplementationsEmpty(RefMethod refMethod) {
    if (refMethod.hasBody() && !refMethod.isBodyEmpty()) return false;

    for (RefMethod refDerived : refMethod.getDerivedMethods()) {
      if (!areAllImplementationsEmpty(refDerived)) return false;
    }

    return true;
  }

  private static boolean hasEmptySuperImplementation(RefMethod refMethod) {
    for (RefMethod refSuper : refMethod.getSuperMethods()) {
      if (refSuper.hasBody() && refSuper.isBodyEmpty()) return true;
    }

    return false;
  }

  public boolean queryExternalUsagesRequests() {
    getRefManager().iterate(new RefManager.RefIterator() {
      public void accept(RefElement refElement) {
        if (getDescriptions(refElement) != null) {
          refElement.accept(new RefVisitor() {
            public void visitMethod(final RefMethod refMethod) {
              getManager().enqueueDerivedMethodsProcessing(refMethod, new InspectionManagerEx.DerivedMethodsProcessor() {
                public boolean process(PsiMethod derivedMethod) {
                  PsiCodeBlock body = derivedMethod.getBody();
                  if (body == null) return true;
                  if (body.getStatements().length == 0) return true;
                  if (RefUtil.isMethodOnlyCallsSuper(derivedMethod)) return true;

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
    return new JobDescriptor[]{InspectionManagerEx.BUILD_GRAPH, InspectionManagerEx.FIND_EXTERNAL_USAGES};
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public String getGroupDisplayName() {
    return GroupNames.DECLARATION_REDUNDANCY;
  }

  public String getShortName() {
    return SHORT_NAME;
  }

  public EmptyMethodInspection() {
  }

  private LocalQuickFix getFix() {
    if (myQuickFix == null) {
      myQuickFix = new QuickFix();
    }
    return myQuickFix;
  }

  private class QuickFix implements LocalQuickFix {
    public String getName() {
      return InspectionsBundle.message("inspection.empty.method.delete.quickfix");
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      RefElement refElement = getElement(descriptor);
      if (refElement.isValid() && refElement instanceof RefMethod) {
        List<RefElement> refElements = new ArrayList<RefElement>(1);
        RefMethod refMethod = (RefMethod)refElement;
        final List<PsiElement> psiElements = new ArrayList<PsiElement>();
        if (refMethod.isOnlyCallsSuper()) {
          deleteMethod(refMethod, psiElements, refElements);
        }
        else if (refMethod.hasBody() && hasEmptySuperImplementation(refMethod)) {
          deleteMethod(refMethod, psiElements, refElements);
        }
        else if (areAllImplementationsEmpty(refMethod)) {
          if (refMethod.hasBody()) {
            if (refMethod.getDerivedMethods().size() == 0) {
              if (refMethod.getSuperMethods().size() == 0) {
                deleteMethod(refMethod, psiElements, refElements);
              }
            }
            else {
              deleteHierarchy(refMethod, psiElements, refElements);
            }
          }
          else {
            deleteHierarchy(refMethod, psiElements, refElements);
          }
        }

        ArrayList<RefElement> deletedRefs = new ArrayList<RefElement>(1);
        for (int i = 0; i < refElements.size(); i++) {
          RefElement element = refElements.get(i);
          RefUtil.removeRefElement(element, deletedRefs);
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            SafeDeleteHandler.invoke(getManager().getProject(),
                                     psiElements.toArray(new PsiElement[psiElements.size()]), false);
          }
        });
      }
    }

    public String getFamilyName() {
      return getName();
    }

    private void deleteHierarchy(RefMethod refMethod, List<PsiElement> result, List<RefElement> refElements) {
      Collection<RefMethod> derivedMethods = refMethod.getDerivedMethods();
      RefMethod[] refMethods = derivedMethods.toArray(new RefMethod[derivedMethods.size()]);
      for (RefMethod refDerived : refMethods) {
        deleteMethod(refDerived, result, refElements);
      }
      deleteMethod(refMethod, result, refElements);
    }

    private void deleteMethod(RefMethod refMethod, List<PsiElement> result, List<RefElement> refElements) {
      refElements.add(refMethod);
      PsiElement psiElement = refMethod.getElement();
      if (psiElement == null) return;
      if (!result.contains(psiElement)) result.add(psiElement);
    }
  }
}
