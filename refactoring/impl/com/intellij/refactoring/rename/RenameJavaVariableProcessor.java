package com.intellij.refactoring.rename;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class RenameJavaVariableProcessor extends RenamePsiElementProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.RenameJavaVariableProcessor");

  public boolean canProcessElement(final PsiElement element) {
    return element instanceof PsiVariable;
  }

  public void renameElement(final PsiElement psiElement,
                            final String newName,
                            final UsageInfo[] usages, final RefactoringElementListener listener) throws IncorrectOperationException {
    PsiVariable variable = (PsiVariable) psiElement;
    List<FieldHidesOuterFieldUsageInfo> outerHides = new ArrayList<FieldHidesOuterFieldUsageInfo>();
    List<PsiElement> occurrencesToCheckForConflict = new ArrayList<PsiElement>();
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
            occurrencesToCheckForConflict.add(newElem);
          }
        }
      }
      }
    // do actual rename
    variable.setName(newName);
    listener.elementRenamed(variable);

    if (variable instanceof PsiField) {
      for (PsiElement occurrence : occurrencesToCheckForConflict) {
        fixPossibleNameCollisionsForFieldRenaming((PsiField) variable, newName, occurrence);
      }
    }

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

    if (elem instanceof PsiLocalVariable || elem instanceof PsiParameter || (elem instanceof PsiField && elem != replacedOccurence))  {
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
      PsiReferenceExpression qualified = createQualifiedFieldReference(field, occurence, newName);
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
    return createQualifiedFieldReference(field, context, name);
  }

  private static PsiReferenceExpression createQualifiedFieldReference(final PsiField field, final PsiElement context,
                                                                      final String name) throws IncorrectOperationException {
    PsiReferenceExpression ref;
    final PsiJavaCodeReferenceElement qualifier;

    final PsiManager manager = field.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      ref = (PsiReferenceExpression)factory.createExpressionFromText("A." + name, context);
      qualifier = (PsiReferenceExpression)ref.getQualifierExpression();
      final PsiClass containingClass = field.getContainingClass();
      final PsiReferenceExpression classReference = factory.createReferenceExpression(containingClass);
      qualifier.replace(classReference);
    }
    else {
      PsiClass contextClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
      if (InheritanceUtil.isInheritorOrSelf(contextClass, field.getContainingClass(), true)) {
        ref = (PsiReferenceExpression)factory.createExpressionFromText("this." + name, context);
        return ref;
      }
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

    Project project = field.getProject();
    final JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(project);

    final String propertyName = manager.variableNameToPropertyName(field.getName(), VariableKind.FIELD);
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
      }
      else if (newSetterParameterName.equals(setter.getParameterList().getParameters()[0].getName())) {
        shouldRenameSetterParameter = false;
      }
    }

    if ((getter != null || setter != null) && askToRenameAccesors(getter, setter, newName, project)) {
      getter = null;
      setter = null;
      shouldRenameSetterParameter = false;
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

  private static boolean askToRenameAccesors(PsiMethod getter, PsiMethod setter, String newName, final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return false;
    String text = RefactoringMessageUtil.getGetterSetterMessage(newName, RefactoringBundle.message("rename.title"), getter, setter);
    return Messages.showYesNoDialog(project, text, RefactoringBundle.message("rename.title"), Messages.getQuestionIcon()) != 0;
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

  public void findCollisions(final PsiElement element, final String newName, final Map<? extends PsiElement, String> allRenames,
                             final List<UsageInfo> result) {
    if (element instanceof PsiField) {
      PsiField field = (PsiField) element;
      findFieldHidesOuterFieldCollisions(field, newName, result);
      findSubmemberHidesFieldCollisions(field, newName, result);
    }
    else if (element instanceof PsiLocalVariable || element instanceof PsiParameter) {
      JavaUnresolvableLocalCollisionDetector.findCollisions(element, newName, result);
      findLocalHidesFieldCollisions(element, newName, allRenames, result);
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

  private static void findFieldHidesOuterFieldCollisions(final PsiField field, final String newName, final List<UsageInfo> result) {
    final PsiClass fieldClass = field.getContainingClass();
    for (PsiClass aClass = fieldClass.getContainingClass(); aClass != null; aClass = aClass.getContainingClass()) {
      final PsiField conflict = aClass.findFieldByName(newName, false);
      if (conflict == null) continue;
      ReferencesSearch.search(conflict).forEach(new Processor<PsiReference>() {
        public boolean process(final PsiReference reference) {
          PsiElement refElement = reference.getElement();
          if (refElement instanceof PsiReferenceExpression && ((PsiReferenceExpression)refElement).isQualified()) return true;
          if (PsiTreeUtil.isAncestor(fieldClass, refElement, false)) {
            FieldHidesOuterFieldUsageInfo info = new FieldHidesOuterFieldUsageInfo(refElement, field);
            result.add(info);
          }
          return true;
        }
      });
    }
  }

  private static void findSubmemberHidesFieldCollisions(final PsiField field, final String newName, final List<UsageInfo> result) {
    if (field.getContainingClass() == null) return;
    if (field.hasModifierProperty(PsiModifier.PRIVATE)) return;
    final PsiClass containingClass = field.getContainingClass();
    Collection<PsiClass> inheritors = ClassInheritorsSearch.search(containingClass, containingClass.getUseScope(), true).findAll();
    for (PsiClass inheritor : inheritors) {
      PsiField conflictingField = inheritor.findFieldByName(newName, false);
      if (conflictingField != null) {
        result.add(new SubmemberHidesMemberUsageInfo(conflictingField, field));
      }
    }
  }

  private static void findLocalHidesFieldCollisions(final PsiElement element, final String newName, final Map<? extends PsiElement, String> allRenames, final List<UsageInfo> result) {
    if (!(element instanceof PsiLocalVariable) && !(element instanceof PsiParameter)) return;

    PsiClass toplevel = PsiUtil.getTopLevelClass(element);
    if (toplevel == null) return;

    PsiElement scopeElement;
    if (element instanceof PsiLocalVariable) {
      scopeElement = RefactoringUtil.getVariableScope((PsiLocalVariable)element);
    }
    else { // Parameter
      scopeElement = ((PsiParameter) element).getDeclarationScope();
    }

    LOG.assertTrue(scopeElement != null);
    scopeElement.accept(new JavaRecursiveElementVisitor() {
      @Override public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (!expression.isQualified()) {
          PsiElement resolved = expression.resolve();
          if (resolved instanceof PsiField) {
            final PsiField field = (PsiField)resolved;
            String fieldNewName = allRenames.containsKey(field) ? allRenames.get(field) : field.getName();
            if (newName.equals(fieldNewName)) {
              result.add(new LocalHidesFieldUsageInfo(expression, element));
            }
          }
        }
      }
    });
  }
}
