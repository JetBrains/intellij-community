/*
 * User: anna
 * Date: 26-Aug-2009
 */
package com.intellij.refactoring.typeMigration.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.AllowedApiFilterExtension;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeMigrationReplacementUtil;
import com.intellij.refactoring.typeMigration.rules.AtomicConversionRule;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.*;

public class ConvertFieldToAtomicIntention extends PsiElementBaseIntentionAction implements LowPriorityAction{
  private static final Logger LOG = Logger.getInstance("#" + ConvertFieldToAtomicIntention.class.getName());

  public ConvertFieldToAtomicIntention() {
    System.out.println("ConvertFieldToAtomicIntention.ConvertFieldToAtomicIntention");
  }

  private final Map<PsiType, String> myFromToMap = new HashMap<PsiType, String>();
  {
    myFromToMap.put(PsiType.INT, AtomicInteger.class.getName());
    myFromToMap.put(PsiType.LONG, AtomicLong.class.getName());
    myFromToMap.put(PsiType.BOOLEAN, AtomicBoolean.class.getName());
    myFromToMap.put(PsiType.INT.createArrayType(), AtomicIntegerArray.class.getName());
    myFromToMap.put(PsiType.LONG.createArrayType(), AtomicLongArray.class.getName());
  }

  @NotNull
  public String getText() {
    return "Convert to atomic";
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiVariable psiVariable = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (psiVariable == null) psiVariable = PsiTreeUtil.getParentOfType(element, PsiLocalVariable.class);
    if (psiVariable == null || psiVariable instanceof PsiResourceVariable) return false;
    if (psiVariable.getTypeElement() == null) return false;
    if (!PsiUtil.isLanguageLevel5OrHigher(psiVariable)) return false;
    final PsiType psiType = psiVariable.getType();
    final PsiClass psiTypeClass = PsiUtil.resolveClassInType(psiType);
    if (psiTypeClass != null) {
      final String qualifiedName = psiTypeClass.getQualifiedName();
      if (qualifiedName != null) { //is already atomic
        if (myFromToMap.values().contains(qualifiedName) ||
            qualifiedName.equals(AtomicReference.class.getName()) ||
            qualifiedName.equals(AtomicReferenceArray.class.getName())) {
          return false;
        }
      }
    } else if (!myFromToMap.containsKey(psiType)) {
      return false;
    }
    return AllowedApiFilterExtension.isClassAllowed(AtomicReference.class.getName(), element);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {


    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiVariable psiVariable = PsiTreeUtil.getParentOfType(element, PsiVariable.class);
    LOG.assertTrue(psiVariable != null);

    final Query<PsiReference> refs = ReferencesSearch.search(psiVariable);

    final Set<PsiElement> elements = new HashSet<PsiElement>();
    elements.add(file);
    for (PsiReference reference : refs) {
      elements.add(reference.getElement());
    }
    if (!CodeInsightUtilBase.preparePsiElementsForWrite(elements)) return;

    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final PsiType fromType = psiVariable.getType();
    PsiClassType toType;
    final String atomicQualifiedName = myFromToMap.get(fromType);
    if (atomicQualifiedName != null) {
      final PsiClass atomicClass = psiFacade.findClass(atomicQualifiedName, GlobalSearchScope.allScope(project));
      if (atomicClass == null) {//show warning
        return;
      }
      toType = elementFactory.createType(atomicClass);
    } else if (fromType instanceof PsiArrayType) {
      final PsiClass atomicReferenceArrayClass = psiFacade.findClass(AtomicReferenceArray.class.getName(), GlobalSearchScope.allScope(project));
      if (atomicReferenceArrayClass == null) {//show warning
        return;
      }
      final HashMap<PsiTypeParameter, PsiType> substitutor = new HashMap<PsiTypeParameter, PsiType>();
      final PsiTypeParameter[] typeParameters = atomicReferenceArrayClass.getTypeParameters();
      if (typeParameters.length == 1) {
        final PsiType componentType = ((PsiArrayType)fromType).getComponentType();
        substitutor.put(typeParameters[0], componentType instanceof PsiPrimitiveType ? ((PsiPrimitiveType)componentType).getBoxedType(file): componentType);
      }
      toType = elementFactory.createType(atomicReferenceArrayClass, elementFactory.createSubstitutor(substitutor));
    } else {
      final PsiClass atomicReferenceClass = psiFacade.findClass(AtomicReference.class.getName(), GlobalSearchScope.allScope(project));
      if (atomicReferenceClass == null) {//show warning
        return;
      }
      final HashMap<PsiTypeParameter, PsiType> substitutor = new HashMap<PsiTypeParameter, PsiType>();
      final PsiTypeParameter[] typeParameters = atomicReferenceClass.getTypeParameters();
      if (typeParameters.length == 1) {
        substitutor.put(typeParameters[0], fromType instanceof PsiPrimitiveType ? ((PsiPrimitiveType)fromType).getBoxedType(file): fromType);
      }
      toType = elementFactory.createType(atomicReferenceClass, elementFactory.createSubstitutor(substitutor));
    }

    try {
      psiVariable.getTypeElement().replace(elementFactory.createTypeElement(toType));
      final PsiExpression initializer = psiVariable.getInitializer();
      if (initializer != null) {
        final TypeConversionDescriptor directConversion = AtomicConversionRule.wrapWithNewExpression(toType, fromType, null);
        if (directConversion != null) {
          TypeMigrationReplacementUtil.replaceExpression(initializer, project, directConversion);
        }
      } else if (!psiVariable.getModifierList().hasModifierProperty(PsiModifier.FINAL)){
        final PsiExpression defaultInitializer =
          elementFactory.createExpressionFromText("new " + toType.getPresentableText() + "()", psiVariable);
        if (psiVariable instanceof PsiLocalVariable) {
          ((PsiLocalVariable)psiVariable).setInitializer(defaultInitializer);
        } else if (psiVariable instanceof PsiField) {
          ((PsiField)psiVariable).setInitializer(defaultInitializer);
        }
      }
      for (PsiReference reference : refs) {
        PsiElement psiElement = reference.getElement();
        if (psiElement instanceof PsiExpression) {
          final PsiElement parent = psiElement.getParent();
          if (parent instanceof PsiExpression && !(parent instanceof PsiReferenceExpression)) {
            psiElement = parent;
          }
          if (psiElement instanceof PsiBinaryExpression) {
            PsiBinaryExpression binaryExpression = (PsiBinaryExpression)psiElement;
            if (TypeConversionUtil.isBinaryOperatorApplicable(binaryExpression.getOperationTokenType(), binaryExpression.getLOperand(), binaryExpression.getROperand(), true)) {
              continue;
            }
          } else if (psiElement instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)psiElement;
            IElementType opSign = TypeConversionUtil.convertEQtoOperation(assignmentExpression.getOperationTokenType());
            if (opSign != null && TypeConversionUtil.isBinaryOperatorApplicable(opSign, assignmentExpression.getLExpression(), assignmentExpression.getRExpression(), true)) {
              continue;
            }
          }
          final TypeConversionDescriptor directConversion = AtomicConversionRule.findDirectConversion(psiElement, toType, fromType);
          if (directConversion != null) {
            TypeMigrationReplacementUtil.replaceExpression((PsiExpression)psiElement, project, directConversion);
          }
        }
      }
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }
}
