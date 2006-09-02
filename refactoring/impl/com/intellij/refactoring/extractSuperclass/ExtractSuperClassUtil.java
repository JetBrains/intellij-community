package com.intellij.refactoring.extractSuperclass;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.MethodSignature;
import com.intellij.refactoring.memberPullUp.PullUpHelper;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.NonNls;

/**
 * @author dsl
 */
public class ExtractSuperClassUtil {
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
    PsiJavaCodeReferenceElement ref = factory.createClassReferenceElement(superclass);
    subclass.getExtendsList().add(ref);

    PullUpHelper pullUpHelper = new PullUpHelper(subclass, superclass, selectedMemberInfos,
                                                 javaDocPolicy
    );

    pullUpHelper.moveMembersToBase();
    pullUpHelper.moveFieldInitializations();

    MethodSignature[] toImplement = OverrideImplementUtil.getMethodSignaturesToImplement(superclass);
    if (toImplement.length > 0) {
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
      @NonNls StringBuffer superCallText = new StringBuffer();
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
}
