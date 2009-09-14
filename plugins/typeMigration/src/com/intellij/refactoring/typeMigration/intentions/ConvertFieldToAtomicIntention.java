/*
 * User: anna
 * Date: 26-Aug-2009
 */
package com.intellij.refactoring.typeMigration.intentions;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.refactoring.typeMigration.TypeMigrationReplacementUtil;
import com.intellij.refactoring.typeMigration.rules.AtomicConversionRule;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.*;

public class ConvertFieldToAtomicIntention extends PsiElementBaseIntentionAction{
  private static final Logger LOG = Logger.getInstance("#" + ConvertFieldToAtomicIntention.class.getName());

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
  public boolean isAvailable(@NotNull Project project, Editor editor, @Nullable PsiElement element) {
    final PsiField psiField = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (psiField == null) return false;
    if (psiField.getTypeElement() == null) return false;
    if (!PsiUtil.isLanguageLevel5OrHigher(psiField)) return false;
    final PsiType psiType = psiField.getType();
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
    }
    return true;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiField psiField = PsiTreeUtil.getParentOfType(element, PsiField.class);
    LOG.assertTrue(psiField != null);
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final PsiType fromType = psiField.getType();
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
      psiField.getTypeElement().replace(elementFactory.createTypeElement(toType));
      final PsiExpression initializer = psiField.getInitializer();
      if (initializer != null) {
        final TypeConversionDescriptor directConversion = AtomicConversionRule.findDirectConversion(initializer, toType, initializer.getType());
        if (directConversion != null) {
          TypeMigrationReplacementUtil.replaceExpression(initializer, project, directConversion);
        }
      }
      for (PsiReference reference : ReferencesSearch.search(psiField)) {
        PsiElement psiElement = reference.getElement();
        if (psiElement instanceof PsiExpression) {
          if (psiElement.getParent() instanceof PsiExpression) {
            psiElement = psiElement.getParent();
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