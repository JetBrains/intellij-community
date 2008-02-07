package com.intellij.refactoring.rename;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.MoveRenameUsageInfo;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RenameJavaVariableProcessor extends RenamePsiElementProcessor {
  public boolean canProcessElement(final PsiElement element) {
    return element instanceof PsiVariable;
  }

  public void renameElement(final PsiElement psiElement,
                            final String newName,
                            final UsageInfo[] usages, final RefactoringElementListener listener) throws IncorrectOperationException {
    PsiVariable variable = (PsiVariable) psiElement;
    List<FieldHidesOuterFieldUsageInfo> outerHides = new ArrayList<FieldHidesOuterFieldUsageInfo>();
    // rename all references
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element == null) continue;

      if (usage instanceof LocalHidesFieldUsageInfo) {
        PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)element;
        PsiElement resolved = collidingRef.resolve();

        if (resolved instanceof PsiField) {
          qualifyField((PsiField)resolved, collidingRef, newName);
        }
        else {
          // do nothing
        }
      }
      else if (usage instanceof FieldHidesOuterFieldUsageInfo) {
        PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)element;
        PsiField resolved = (PsiField)collidingRef.resolve();
        outerHides.add(new FieldHidesOuterFieldUsageInfo(element, resolved));
      }
      else {
        final PsiReference ref;
        if (usage instanceof MoveRenameUsageInfo) {
          ref = usage.getReference();
        }
        else {
          ref = element.getReference();
        }
        if (ref != null) {
          PsiElement newElem = ref.handleElementRename(newName);
          if (variable instanceof PsiField) {
            fixPossibleNameCollisionsForFieldRenaming((PsiField)variable, newName, newElem);
          }
        }
      }
      }
    // do actual rename
    variable.setName(newName);
    listener.elementRenamed(variable);

    for (FieldHidesOuterFieldUsageInfo usage : outerHides) {
      final PsiElement element = usage.getElement();
      PsiJavaCodeReferenceElement collidingRef = (PsiJavaCodeReferenceElement)element;
      PsiField field = (PsiField)usage.getReferencedElement();
      PsiReferenceExpression ref = createFieldReference(field, collidingRef);
      collidingRef.replace(ref);
    }
  }

  private static void fixPossibleNameCollisionsForFieldRenaming(PsiField field, String newName, PsiElement replacedOccurence) throws IncorrectOperationException {
    if (!(replacedOccurence instanceof PsiReferenceExpression)) return;
    PsiElement elem = ((PsiReferenceExpression)replacedOccurence).resolve();

    if (elem == null || elem == field) {
      // If reference is unresolved, then field is not hidden by anyone...
      return;
    }

    if (elem instanceof PsiLocalVariable || elem instanceof PsiParameter) {
      qualifyField(field, replacedOccurence, newName);
    }
  }

  private static void qualifyField(PsiField field, PsiElement occurence, String newName) throws IncorrectOperationException {
    PsiManager psiManager = occurence.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory();
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      PsiReferenceExpression qualified = (PsiReferenceExpression)factory.createExpressionFromText("a." + newName, null);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      qualified.getQualifierExpression().replace(factory.createReferenceExpression(field.getContainingClass()));
      occurence.replace(qualified);
    }
    else {
      PsiReferenceExpression qualified = (PsiReferenceExpression)factory.createExpressionFromText("this." + newName, null);
      qualified = (PsiReferenceExpression)CodeStyleManager.getInstance(psiManager.getProject()).reformat(qualified);
      occurence.replace(qualified);
    }
  }

  public static PsiReferenceExpression createFieldReference(PsiField field, PsiElement context) throws IncorrectOperationException {
    final PsiManager manager = field.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    final String name = field.getName();
    PsiReferenceExpression ref = (PsiReferenceExpression) factory.createExpressionFromText(name, context);
    PsiElement resolved = ref.resolve();
    if (manager.areElementsEquivalent(resolved, field)) return ref;
    final PsiJavaCodeReferenceElement qualifier;
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      ref = (PsiReferenceExpression)factory.createExpressionFromText("A." + name, context);
      qualifier = (PsiReferenceExpression)ref.getQualifierExpression();
      final PsiClass containingClass = field.getContainingClass();
      final PsiReferenceExpression classReference = factory.createReferenceExpression(containingClass);
      qualifier.replace(classReference);
    }
    else {
      ref = (PsiReferenceExpression)factory.createExpressionFromText("this." + name, context);
      resolved = ref.resolve();
      if (manager.areElementsEquivalent(resolved, field)) return ref;
      ref = (PsiReferenceExpression) factory.createExpressionFromText("A.this." + name, null);
      qualifier = ((PsiThisExpression)ref.getQualifierExpression()).getQualifier();
      final PsiClass containingClass = field.getContainingClass();
      final PsiJavaCodeReferenceElement classReference = factory.createClassReferenceElement(containingClass);
      qualifier.replace(classReference);
    }
    return ref;
  }

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    if (element instanceof PsiField) {
      prepareFieldRenaming((PsiField)element, newName, allRenames);
    }
  }

  private static void prepareFieldRenaming(PsiField field, String newName, final Map<PsiElement, String> allRenames) {
    // search for getters/setters
    PsiClass aClass = field.getContainingClass();

    final JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(field.getProject());

    final String propertyName =
        manager.variableNameToPropertyName(field.getName(), VariableKind.FIELD);
    String newPropertyName = manager.variableNameToPropertyName(newName, VariableKind.FIELD);

    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    PsiMethod getter = PropertyUtil.findPropertyGetter(aClass, propertyName, isStatic, false);
    PsiMethod setter = PropertyUtil.findPropertySetter(aClass, propertyName, isStatic, false);

    boolean shouldRenameSetterParameter = false;

    if (setter != null) {
      String parameterName = manager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
      PsiParameter setterParameter = setter.getParameterList().getParameters()[0];
      shouldRenameSetterParameter = parameterName.equals(setterParameter.getName());
    }

    String newGetterName = "";

    if (getter != null) {
      String getterId = getter.getName();
      newGetterName = PropertyUtil.suggestGetterName(newPropertyName, field.getType(), getterId);
      if (newGetterName.equals(getterId)) {
        getter = null;
        newGetterName = null;
      }
    }

    String newSetterName = "";
    if (setter != null) {
      newSetterName = PropertyUtil.suggestSetterName(newPropertyName);
      final String newSetterParameterName = manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER);
      if (newSetterName.equals(setter.getName())) {
        setter = null;
        newSetterName = null;
        shouldRenameSetterParameter = false;
      } else if (newSetterParameterName.equals(setter.getParameterList().getParameters()[0].getName())) {
        shouldRenameSetterParameter = false;
      }
    }

    if (getter != null || setter != null) {
      if (askToRenameAccesors(getter, setter, newName)) {
        getter = null;
        setter = null;
        shouldRenameSetterParameter = false;
      }
    }

    if (getter != null) {
      addOverriddenAndImplemented(aClass, getter, newGetterName, allRenames);
    }

    if (setter != null) {
      addOverriddenAndImplemented(aClass, setter, newSetterName, allRenames);
    }

    if (shouldRenameSetterParameter) {
      PsiParameter parameter = setter.getParameterList().getParameters()[0];
      allRenames.put(parameter, manager.propertyNameToVariableName(newPropertyName, VariableKind.PARAMETER));
    }
  }

  private static boolean askToRenameAccesors(PsiMethod getter, PsiMethod setter, String newName) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return false;
    String text = RefactoringMessageUtil.getGetterSetterMessage(newName, RefactoringBundle.message("rename.title"), getter, setter);
    return Messages.showYesNoDialog(getter.getProject(), text, RefactoringBundle.message("rename.title"), Messages.getQuestionIcon()) != 0;
  }

  private static void addOverriddenAndImplemented(PsiClass aClass, PsiMethod methodPrototype, String newName,
                                           final Map<PsiElement, String> allRenames) {
    final HashSet<PsiClass> superClasses = new HashSet<PsiClass>();
    RefactoringHierarchyUtil.getSuperClasses(aClass, superClasses, true);
    superClasses.add(aClass);

    for (PsiClass superClass : superClasses) {
      PsiMethod method = superClass.findMethodBySignature(methodPrototype, false);

      if (method != null) {
        allRenames.put(method, newName);
      }
    }
  }

  public void findExistingNameConflicts(final PsiElement element, final String newName, final Collection<String> conflicts) {
    if (element instanceof PsiCompiledElement) return;
    if (element instanceof PsiField) {
      PsiField refactoredField = (PsiField)element;
      if (newName.equals(refactoredField.getName())) return;
      ConflictsUtil.checkFieldConflicts(
        refactoredField.getContainingClass(),
        newName,
        conflicts
      );
    }
  }

  @Nullable
  @NonNls
  public String getHelpID(final PsiElement element) {
    if (element instanceof PsiField){
      return HelpID.RENAME_FIELD;
    }
    else if (element instanceof PsiLocalVariable){
      return HelpID.RENAME_VARIABLE;
    }
    else if (element instanceof PsiParameter){
      return HelpID.RENAME_PARAMETER;
    }
    return null;
  }

  public boolean isToSearchInComments(final PsiElement element) {
    if (element instanceof PsiField){
      return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD;
    }
    return JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE;
  }

  public void setToSearchInComments(final PsiElement element, final boolean enabled) {
    if (element instanceof PsiField){
      JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FIELD = enabled;
    }
    JavaRefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_VARIABLE = enabled;
  }
}
