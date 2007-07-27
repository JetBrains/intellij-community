package com.intellij.refactoring.extractSuperclass;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author dsl
 */
public class ExtractSuperClassUtil {
  private static final Logger LOG = Logger.getInstance("com.intellij.refactoring.extractSuperclass.ExtractSuperClassUtil");
  private ExtractSuperClassUtil() {}

  public static PsiClass extractSuperClass(final Project project,
                                           final PsiDirectory targetDirectory,
                                           final String superclassName,
                                           final PsiClass subclass,
                                           final MemberInfo[] selectedMemberInfos,
                                           final JavaDocPolicy javaDocPolicy)
    throws IncorrectOperationException {
    PsiClass superclass;
    PsiElementFactory factory = PsiManager.getInstance(project).getElementFactory();
    superclass = targetDirectory.createClass(superclassName);
    final PsiModifierList superClassModifierList = superclass.getModifierList();
    assert superClassModifierList != null;
    superClassModifierList.setModifierProperty(PsiModifier.FINAL, false);
    copyPsiReferenceList(subclass.getExtendsList(), superclass.getExtendsList());

    // create constructors if neccesary
    PsiMethod[] constructors = getCalledBaseConstructors(subclass);
    if (constructors.length > 0) {
      createConstructorsByPattern(project, superclass, constructors);
    }

    // clear original class' "extends" list
    clearPsiReferenceList(subclass.getExtendsList());

    // make original class extend extracted superclass
    PsiJavaCodeReferenceElement ref = createExtendingReference(superclass, subclass, selectedMemberInfos); 
    subclass.getExtendsList().add(ref);

    PullUpHelper pullUpHelper = new PullUpHelper(subclass, superclass, selectedMemberInfos,
                                                 javaDocPolicy
    );

    pullUpHelper.moveMembersToBase();
    pullUpHelper.moveFieldInitializations();

    Collection<MethodSignature> toImplement = OverrideImplementUtil.getMethodSignaturesToImplement(superclass);
    if (!toImplement.isEmpty()) {
      superClassModifierList.setModifierProperty(PsiModifier.ABSTRACT, true);
    }
    return superclass;
  }

  private static void createConstructorsByPattern(Project project, final PsiClass superclass, PsiMethod[] patternConstructors) throws IncorrectOperationException {
    PsiElementFactory factory = PsiManager.getInstance(project).getElementFactory();
    CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    for (PsiMethod baseConstructor : patternConstructors) {
      PsiMethod constructor = (PsiMethod)superclass.add(factory.createConstructor());
      PsiParameterList paramList = constructor.getParameterList();
      PsiParameter[] baseParams = baseConstructor.getParameterList().getParameters();
      @NonNls StringBuilder superCallText = new StringBuilder();
      superCallText.append("super(");
      for (int i = 0; i < baseParams.length; i++) {
        PsiParameter baseParam = baseParams[i];
        paramList.add(baseParam);
        if (i > 0) {
          superCallText.append(",");
        }
        superCallText.append(baseParam.getName());
      }
      superCallText.append(");");
      PsiStatement statement = factory.createStatementFromText(superCallText.toString(), null);
      statement = (PsiStatement)styleManager.reformat(statement);
      final PsiCodeBlock body = constructor.getBody();
      assert body != null;
      body.add(statement);
      constructor.getThrowsList().replace(baseConstructor.getThrowsList());
    }
  }

  private static PsiMethod[] getCalledBaseConstructors(final PsiClass subclass) {
    Set<PsiMethod> baseConstructors = new HashSet<PsiMethod>();
    PsiMethod[] constructors = subclass.getConstructors();
    for (PsiMethod constructor : constructors) {
      PsiCodeBlock body = constructor.getBody();
      if (body == null) continue;
      PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        PsiStatement first = statements[0];
        if (first instanceof PsiExpressionStatement) {
          PsiExpression expression = ((PsiExpressionStatement)first).getExpression();
          if (expression instanceof PsiMethodCallExpression) {
            PsiReferenceExpression calledMethod = ((PsiMethodCallExpression)expression).getMethodExpression();
            @NonNls String text = calledMethod.getText();
            if ("super".equals(text)) {
              PsiMethod baseConstructor = (PsiMethod)calledMethod.resolve();
              if (baseConstructor != null) {
                baseConstructors.add(baseConstructor);
              }
            }
          }
        }
      }
    }
    return baseConstructors.toArray(new PsiMethod[baseConstructors.size()]);
  }

  private static void clearPsiReferenceList(PsiReferenceList refList) throws IncorrectOperationException {
    PsiJavaCodeReferenceElement[] refs = refList.getReferenceElements();
    for (PsiJavaCodeReferenceElement ref : refs) {
      ref.delete();
    }
  }

  private static void copyPsiReferenceList(PsiReferenceList sourceList, PsiReferenceList destinationList) throws IncorrectOperationException {
    clearPsiReferenceList(destinationList);
    PsiJavaCodeReferenceElement[] refs = sourceList.getReferenceElements();
    for (PsiJavaCodeReferenceElement ref : refs) {
      destinationList.add(ref);
    }
  }

  public static PsiJavaCodeReferenceElement createExtendingReference(final PsiClass superClass,
                                                                      final PsiClass derivedClass,
                                                                      final MemberInfo[] selectedMembers) throws IncorrectOperationException {
    final PsiManager manager = derivedClass.getManager();
    Set<PsiElement> movedElements = new com.intellij.util.containers.HashSet<PsiElement>();
    for (final MemberInfo info : selectedMembers) {
      movedElements.add(info.getMember());
    }
    final PsiTypeParameterList typeParameterList = RefactoringUtil.createTypeParameterListWithUsedTypeParameters(
      movedElements.toArray(new PsiElement[movedElements.size()]));
    final PsiTypeParameterList originalTypeParameterList = superClass.getTypeParameterList();
    assert originalTypeParameterList != null;
    final PsiTypeParameterList newList = typeParameterList != null ? (PsiTypeParameterList)originalTypeParameterList.replace(typeParameterList) : originalTypeParameterList;
    final PsiElementFactory factory = manager.getElementFactory();
    Map<PsiTypeParameter, PsiType> substitutionMap = new HashMap<PsiTypeParameter, PsiType>();
    for (final PsiTypeParameter parameter : newList.getTypeParameters()) {
      substitutionMap.put(parameter, factory.createType(findTypeParameterInDerived(derivedClass, parameter.getName())));
    }

    final PsiClassType type = factory.createType(superClass, factory.createSubstitutor(substitutionMap));
    return factory.createReferenceElementByType(type);
  }

  public static PsiTypeParameter findTypeParameterInDerived(final PsiClass aClass, final String name) {
    final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(aClass);
    while(iterator.hasNext()) {
      final PsiTypeParameter typeParameter = iterator.next();
      if (name.equals(typeParameter.getName())) return typeParameter;
    }

    LOG.assertTrue(false, "Cannot find type parameter");
    return null;
  }
}
