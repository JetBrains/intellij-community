/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 16.04.2002
 * Time: 15:37:30
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.makeStatic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

import org.jetbrains.annotations.NotNull;

public abstract class MakeMethodOrClassStaticProcessor<T extends PsiTypeParameterListOwner> extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.makeMethodStatic.MakeMethodStaticProcessor");

  protected T myMember;
  protected Settings mySettings;

  public MakeMethodOrClassStaticProcessor(Project project,
                                          T member,
                                          Settings settings) {
    super(project);
    myMember = member;
    mySettings = settings;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new MakeMethodOrClassStaticViewDescriptor(myMember);
  }

  protected final boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    if (myPrepareSuccessfulSwingThreadCallback != null) {
      List<String> conflicts = getConflictDescriptions(usagesIn);
      if (conflicts.size() > 0) {
        ConflictsDialog conflictsDialog = new ConflictsDialog(myProject, conflicts);
        conflictsDialog.show();
        if (!conflictsDialog.isOK()) {
          return false;
        }
      }
      if(!mySettings.isChangeSignature()) {
        refUsages.set(filterInternalUsages(usagesIn));
      }
    }
    refUsages.set(filterOverriding(usagesIn));

    prepareSuccessful();
    return true;
  }

  private static UsageInfo[] filterOverriding(UsageInfo[] usages) {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    for (UsageInfo usage : usages) {
      if (!(usage instanceof OverridingMethodUsageInfo)) {
        result.add(usage);
      }
    }
    return result.toArray(new UsageInfo[result.size()]);
  }

  private static UsageInfo[] filterInternalUsages(UsageInfo[] usages) {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    for (UsageInfo usage : usages) {
      if (!(usage instanceof InternalUsageInfo)) {
        result.add(usage);
      }
    }
    return result.toArray(new UsageInfo[result.size()]);
  }

  protected List<String> getConflictDescriptions(UsageInfo[] usages) {
    ArrayList<String> conflicts = new ArrayList<String>();
    HashSet<PsiElement> processed = new HashSet<PsiElement>();
    String typeString = UsageViewUtil.capitalize(UsageViewUtil.getType(myMember));
    for (UsageInfo usageInfo : usages) {
      if (usageInfo instanceof InternalUsageInfo && !(usageInfo instanceof SelfUsageInfo)) {
        PsiElement referencedElement = ((InternalUsageInfo)usageInfo).getReferencedElement();
        if (!mySettings.isMakeClassParameter()) {
          if (referencedElement instanceof PsiModifierListOwner) {
            if (((PsiModifierListOwner)referencedElement).hasModifierProperty(PsiModifier.STATIC)) {
              continue;
            }
          }

          if (processed.contains(referencedElement)) continue;
          processed.add(referencedElement);
          if (referencedElement instanceof PsiField) {
            PsiField field = (PsiField)referencedElement;

            if (mySettings.getNameForField(field) == null) {
              String message = RefactoringBundle.message("0.uses.non.static.1.which.is.not.passed.as.a.parameter", typeString,
                                                         ConflictsUtil.getDescription(field, true));
              conflicts.add(message);
            }
          }
          else {
            String message = RefactoringBundle.message("0.uses.1.which.needs.class.instance", typeString, ConflictsUtil.getDescription(referencedElement, true));
            conflicts.add(message);
          }
        }
      }
      if (usageInfo instanceof OverridingMethodUsageInfo) {
        LOG.assertTrue(myMember instanceof PsiMethod);
        final PsiMethod overridingMethod = ((PsiMethod)usageInfo.getElement());
        String message = RefactoringBundle.message("method.0.is.overridden.by.1", ConflictsUtil.getDescription(myMember, false),
                                                   ConflictsUtil.getDescription(overridingMethod, true));
        conflicts.add(message);
      }
      else {
        PsiElement element = usageInfo.getElement();
        PsiElement container = ConflictsUtil.getContainer(element);
        if (processed.contains(container)) continue;
        processed.add(container);
        List<Settings.FieldParameter> fieldParameters = mySettings.getParameterOrderList();
        ArrayList<PsiField> inaccessible = new ArrayList<PsiField>();

        for (final Settings.FieldParameter fieldParameter : fieldParameters) {
          if (!PsiUtil.isAccessible(fieldParameter.field, element, null)) {
            inaccessible.add(fieldParameter.field);
          }
        }

        if (inaccessible.isEmpty()) continue;

        conflicts.add(createInaccessibleFieldsConflictDescription(inaccessible, container));
      }
    }
    return conflicts;
  }

  private static String createInaccessibleFieldsConflictDescription(ArrayList<PsiField> inaccessible, PsiElement container) {
    if (inaccessible.size() == 1) {
      final PsiField field = inaccessible.get(0);
      return RefactoringBundle.message("field.0.is.not.accessible",
                                       CommonRefactoringUtil.htmlEmphasize(field.getName()),
                                       ConflictsUtil.getDescription(container, true));
    } else {
      StringBuffer fieldsBuffer = new StringBuffer();
      for (int j = 0; j < inaccessible.size(); j++) {
        PsiField field = inaccessible.get(j);

        if (j > 0) {
          fieldsBuffer.append(", ");
        }
        fieldsBuffer.append(CommonRefactoringUtil.htmlEmphasize(field.getName()));
      }

      return RefactoringBundle.message("fields.0.are.not.accessible",
                                       fieldsBuffer.toString(),
                                       ConflictsUtil.getDescription(container, true));
    }
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    PsiManager manager = myMember.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();

    result.addAll(Arrays.asList(MakeStaticUtil.findClassRefsInMember(myMember, true)));

    if (mySettings.isReplaceUsages()) {
      if (myMember instanceof PsiMethod) {
        findExternalReferences((PsiMethod)myMember, result);
      } else {
        final PsiClass aClass = (PsiClass)myMember;
        PsiMethod[] constructors = aClass.getConstructors();
        if (constructors.length > 0) {
          for (PsiMethod constructor : constructors) {
            findExternalReferences(constructor, result);
          }
        } else {
          findDefaultConstructorReferences(aClass, result);
        }
      }
    }

    if (myMember instanceof PsiMethod) {
      final PsiMethod[] overridingMethods = helper.findOverridingMethods(((PsiMethod)myMember), GlobalSearchScope.allScope(myProject), false);
      for (PsiMethod overridingMethod : overridingMethods) {
        if (overridingMethod != myMember) {
          result.add(new OverridingMethodUsageInfo(overridingMethod));
        }
      }
    }

    return result.toArray(new UsageInfo[result.size()]);
  }

  private void findExternalReferences(final PsiMethod method, final ArrayList<UsageInfo> result) {
    for (PsiReference ref : ReferencesSearch.search(method).findAll()) {
      PsiElement element = ref.getElement();
      PsiElement qualifier = null;
      if (element instanceof PsiReferenceExpression) {
        qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
        if (qualifier instanceof PsiThisExpression) qualifier = null;
      }
      if (!PsiTreeUtil.isAncestor(myMember, element, true) || qualifier != null) {
        result.add(new UsageInfo(element));
      }
    }
  }

  private void findDefaultConstructorReferences(final PsiClass aClass, final ArrayList<UsageInfo> result) {
    for (PsiReference ref : ReferencesSearch.search(aClass).findAll()) {
      PsiElement element = ref.getElement();
      PsiElement qualifier = null;
      if (element.getParent() instanceof PsiNewExpression) {
        PsiNewExpression newExpression = (PsiNewExpression)element.getParent();
        qualifier = newExpression.getQualifier();
        if (qualifier instanceof PsiThisExpression) qualifier = null;
      }
      if (!PsiTreeUtil.isAncestor(myMember, element, true) || qualifier != null) {
        result.add(new UsageInfo(element));
      } else {
        result.add(new InternalUsageInfo(element, aClass));
      }
    }
  }

  protected void refreshElements(PsiElement[] elements) {
  }

  //should be called before setting static modifier
  protected void setupTypeParameterList(Collection<PsiType> addedTypes) throws IncorrectOperationException {
    final PsiTypeParameterList list = myMember.getTypeParameterList();
    assert list != null;
    list.replace(RefactoringUtil.createTypeParameterListWithUsedTypeParameters(myMember));
  }

  protected boolean makeClassParameterFinal(UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      if (usage instanceof InternalUsageInfo) {
        final InternalUsageInfo internalUsageInfo = (InternalUsageInfo)usage;
        PsiElement referencedElement = internalUsageInfo.getReferencedElement();
        if (!(referencedElement instanceof PsiField)
            || mySettings.getNameForField((PsiField)referencedElement) == null) {
          if (internalUsageInfo.isInsideAnonymous()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  protected boolean makeFieldParameterFinal(PsiField field, UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      if (usage instanceof InternalUsageInfo) {
        final InternalUsageInfo internalUsageInfo = (InternalUsageInfo)usage;
        PsiElement referencedElement = internalUsageInfo.getReferencedElement();
        if (referencedElement instanceof PsiField && field.equals(referencedElement)) {
          if (internalUsageInfo.isInsideAnonymous()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  protected String getCommandName() {
    return RefactoringBundle.message("make.static.command", UsageViewUtil.getDescriptiveName(myMember));
  }

  public T getMember() {
    return myMember;
  }

  public Settings getSettings() {
    return mySettings;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    PsiManager manager = myMember.getManager();
    PsiElementFactory factory = manager.getElementFactory();

    try {
      for (UsageInfo usage : usages) {
        if (usage instanceof SelfUsageInfo) {
          changeSelfUsage((SelfUsageInfo)usage);
        }
        else if (usage instanceof InternalUsageInfo) {
          changeInternalUsage((InternalUsageInfo)usage, factory);
        }
        else {
          changeExternalUsage(usage, factory);
        }
      }
      changeSelf(factory, usages);
    }
    catch (IncorrectOperationException ex) {
      LOG.assertTrue(false);
    }
  }

  protected abstract void changeSelf(PsiElementFactory factory, UsageInfo[] usages) throws IncorrectOperationException;

  protected abstract void changeSelfUsage(SelfUsageInfo usageInfo) throws IncorrectOperationException;

  protected abstract void changeInternalUsage(InternalUsageInfo usage, PsiElementFactory factory) throws IncorrectOperationException;

  protected abstract void changeExternalUsage(UsageInfo usage, PsiElementFactory factory) throws IncorrectOperationException;
}
