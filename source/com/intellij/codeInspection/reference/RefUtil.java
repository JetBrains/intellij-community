/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 6:03:30 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.codeInspection.ex.EntryPointsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.JspxFileImpl;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.impl.source.jsp.jspJava.JspHolderMethod;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class RefUtil {
  private RefUtil() {
  }

  public static void addReferences(final PsiModifierListOwner psiFrom, final RefElement refFrom, PsiElement findIn) {
    if (findIn != null) {
      findIn.accept(
        new PsiRecursiveElementVisitor() {
          public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
          }

          public void visitReferenceExpression(PsiReferenceExpression expression) {
            visitElement(expression);

            PsiElement psiResolved = expression.resolve();

            if (psiResolved instanceof PsiModifierListOwner) {
              updateCanBeStatic(refFrom, psiFrom, (PsiModifierListOwner)psiResolved);
              if (isDeprecated(psiResolved)) refFrom.setUsesDeprecatedApi(true);
            }

            RefElement refResolved = refFrom.getRefManager().getReference(psiResolved);
            refFrom.addReference(
              refResolved, psiResolved, psiFrom, PsiUtil.isAccessedForWriting(expression),
              PsiUtil.isAccessedForReading(expression), expression
            );

            if (refResolved instanceof RefMethod) {
              updateRefMethod(psiResolved, refResolved, expression, psiFrom, refFrom);
            }
          }

          public void visitThisExpression(PsiThisExpression expression) {
            super.visitThisExpression(expression);
            PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
            if (qualifier != null) {
              PsiClass psiClass = (PsiClass)qualifier.resolve();
              if (psiClass != null) {
                final PsiMethod hashCodeMethod = MethodSignatureUtil.findMethodBySignature(
                  psiClass,
                  MethodSignatureUtil.createMethodSignature("hashCode", (PsiType[])null, null, PsiSubstitutor.EMPTY),
                  true
                );
                if (hashCodeMethod != null) {
                  updateCanBeStatic(refFrom, psiFrom, hashCodeMethod);
                }
              }
            }
            refFrom.setCanBeStatic(false);
          }

          @Override public void visitEnumConstant(PsiEnumConstant enumConstant) {
            super.visitEnumConstant(enumConstant);
            processNewLikeConstruct(enumConstant.resolveConstructor(), enumConstant.getArgumentList());
          }

          public void visitNewExpression(PsiNewExpression newExpr) {
            super.visitNewExpression(newExpr);
            PsiMethod psiConstructor = newExpr.resolveConstructor();
            final PsiExpressionList argumentList = newExpr.getArgumentList();

            RefMethod refConstructor = processNewLikeConstruct(psiConstructor, argumentList);

            if (refConstructor == null) {  // No explicit constructor referenced. Should use default one.
              PsiType newType = newExpr.getType();
              if (newType instanceof PsiClassType) {
                PsiClass psiClass = PsiUtil.resolveClassInType(newType);

                RefClass refClass = (RefClass)refFrom.getRefManager().getReference(psiClass);

                if (psiClass != null) {
                  updateCanBeStatic(refFrom, psiFrom, psiClass);
                }

                if (refClass != null) {
                  RefMethod refDefaultConstructor = refClass.getDefaultConstructor();

                  if (refDefaultConstructor != null) {
                    refDefaultConstructor.addInReference(refFrom);
                    refFrom.addOutReference(refDefaultConstructor);
                  }
                  else {
                    refFrom.addReference(refClass, psiClass, psiFrom, false, true, null);
                  }
                }
              }
            }
          }

          @Nullable
          private RefMethod processNewLikeConstruct(final PsiMethod psiConstructor, final PsiExpressionList argumentList) {
            if (psiConstructor != null) {
              updateCanBeStatic(refFrom, psiFrom, psiConstructor.getContainingClass());
              if (isDeprecated(psiConstructor)) refFrom.setUsesDeprecatedApi(true);
            }

            RefMethod refConstructor = (RefMethod)refFrom.getRefManager().getReference(
              psiConstructor
            );
            refFrom.addReference(refConstructor, psiConstructor, psiFrom, false, true, null);

            if (argumentList != null) {
              PsiExpression[] psiParams = argumentList.getExpressions();
              for (int i = 0; i < psiParams.length; i++) {
                PsiExpression param = psiParams[i];
                param.accept(this);
              }

              if (refConstructor != null) {
                refConstructor.updateParameterValues(psiParams);
              }
            }
            return refConstructor;
          }

          public void visitAnonymousClass(PsiAnonymousClass psiClass) {
            RefClass refClass = (RefClass)refFrom.getRefManager().getReference(psiClass);
            refFrom.addReference(refClass, psiClass, psiFrom, false, true, null);
          }

          public void visitReturnStatement(PsiReturnStatement statement) {
            super.visitReturnStatement(statement);

            if (refFrom instanceof RefMethod) {
              RefMethod refMethod = (RefMethod)refFrom;
              refMethod.updateReturnValueTemplate(statement.getReturnValue());
            }
          }

          public void visitClassObjectAccessExpression(PsiClassObjectAccessExpression expression) {
            super.visitClassObjectAccessExpression(expression);
            final PsiTypeElement operand = expression.getOperand();
            if (operand == null) return;
            final PsiType type = operand.getType();
            if (type instanceof PsiClassType) {
              PsiClassType classType = (PsiClassType)type;
              PsiClass psiClass = classType.resolve();
              if (psiClass != null) {
                RefClass refClass = (RefClass)refFrom.getRefManager().getReference(psiClass);
                if (refClass != null) {
                  RefMethod refDefaultConstructor = refClass.getDefaultConstructor();

                  if (refDefaultConstructor != null) {
                    refDefaultConstructor.addInReference(refFrom);
                    refFrom.addOutReference(refDefaultConstructor);
                  }
                  else {
                    refFrom.addReference(refClass, psiClass, psiFrom, false, true, null);
                  }
                }
              }
            }
          }
        }
      );
    }
  }

  private static void updateRefMethod(PsiElement psiResolved,
                                      RefElement refResolved,
                                      PsiElement refExpression,
                                      final PsiElement psiFrom,
                                      final RefElement refFrom) {
    PsiMethod psiMethod = (PsiMethod)psiResolved;
    RefMethod refMethod = (RefMethod)refResolved;

    PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(
      refExpression,
      PsiMethodCallExpression.class
    );
    if (call != null) {
      PsiType returnType = psiMethod.getReturnType();
      if (!psiMethod.isConstructor() && returnType != PsiType.VOID) {
        if (!(call.getParent() instanceof PsiExpressionStatement)) {
          refMethod.setReturnValueUsed(true);
        }

        addTypeReference(psiFrom, returnType, refFrom.getRefManager());
      }

      PsiExpressionList argumentList = call.getArgumentList();
      if (argumentList != null && argumentList.getExpressions().length > 0) {
        refMethod.updateParameterValues(argumentList.getExpressions());
      }
    }
  }

  public static String getName(PsiElement element) {
    if (element instanceof PsiAnonymousClass) {
      PsiAnonymousClass psiAnonymousClass = (PsiAnonymousClass)element;
      PsiClass psiBaseClass = psiAnonymousClass.getBaseClassType().resolve();
      return "anonymous (" + (psiBaseClass != null ? psiBaseClass.getQualifiedName() : "") + ")";
    }

    if (element instanceof JspClass) {
      final JspClass jspClass = (JspClass)element;
      final JspxFileImpl jspxFile = jspClass.getJspxFile();
      return "<" + jspxFile.getName() + ">";
    }

    if (element instanceof JspHolderMethod) {
      return "<% page content %>";
    }

    String name = null;
    if (element instanceof PsiNamedElement) {
      name = ((PsiNamedElement)element).getName();
    }

    return name == null ? "anonymous" : name;
  }

  public static boolean isDeprecated(PsiElement psiResolved) {
    if (psiResolved instanceof PsiDocCommentOwner) {
      return ((PsiDocCommentOwner)psiResolved).isDeprecated();
    }
    return false;
  }

  public static boolean belongsToScope(PsiElement psiElement, RefManager refManager) {
    if (psiElement instanceof PsiCompiledElement) return false;
    if (psiElement instanceof PsiPackage) return false; //?
    if (psiElement instanceof PsiTypeParameter) return false;
    if (!psiElement.getManager().isInProject(psiElement)) return false;

    return refManager.getScope() != null ? refManager.getScope().contains(psiElement) : true;
  }

  public static boolean isAppMain(PsiMethod psiMethod, RefMethod refMethod) {
    if (!refMethod.isStatic()) return false;
    if (!PsiType.VOID.equals(psiMethod.getReturnType())) return false;

    PsiMethod appMainPattern = refMethod.getRefManager().getAppMainPattern();
    if (MethodSignatureUtil.areSignaturesEqual(psiMethod, appMainPattern)) return true;

    PsiMethod appPremainPattern = refMethod.getRefManager().getAppPremainPattern();
    return MethodSignatureUtil.areSignaturesEqual(psiMethod, appPremainPattern);
  }

  public static RefPackage getPackage(RefEntity refEntity) {
    while (refEntity != null && !(refEntity instanceof RefPackage)) refEntity = refEntity.getOwner();

    return (RefPackage)refEntity;
  }

  public static String getProjectFileName(Project project) {
    VirtualFile projectFile = project.getProjectFile();
    if (projectFile == null) return "<unnamed>";
    return projectFile.getName();
  }


  public static RefClass getTopLevelClass(RefElement refElement) {
    RefEntity refParent = refElement.getOwner();

    while (!(refParent instanceof RefPackage)) {
      refElement = (RefElement)refParent;
      refParent = refParent.getOwner();
    }

    return (RefClass)refElement;
  }

  public static boolean isInheritor(RefClass subClass, RefClass superClass) {
    if (subClass == superClass) return true;

    for (RefClass baseClass : subClass.getBaseClasses()) {
      if (isInheritor(baseClass, superClass)) return true;
    }

    return false;
  }

  public static boolean isInheritor(PsiClass subClass, PsiClass superClass) {
    if (samePsiElement(subClass, superClass)) return true;

    return subClass.isInheritor(superClass, true);
  }

  public static String getPackageName(RefEntity refEntity) {
    RefPackage refPackage = getPackage(refEntity);

    return refPackage == null ? "default package" : refPackage.getQualifiedName();
  }

  public static String getQualifiedName(RefEntity refEntity) {

    if (refEntity == null || refEntity instanceof RefElement && !((RefElement)refEntity).isValid()) return "invalid";

    if (refEntity instanceof RefPackage) {
      return ((RefPackage)refEntity).getQualifiedName();
    }
    else if (refEntity.getOwner() == null) {
      return refEntity.getName();
    }
    else if (refEntity instanceof RefFile){
      return refEntity.getName();
    }
    else if (refEntity instanceof RefClass && ((RefClass)refEntity).isAnonymous()) {
      return refEntity.getName() + " in " + getQualifiedName(refEntity.getOwner());
    }
    else if (refEntity instanceof RefMethod && ((RefMethod)refEntity).getOwnerClass().isAnonymous()) {
      return "anonymous." + refEntity.getName();
    }
    else {
      StringBuffer result = new StringBuffer(refEntity.getName());

      RefEntity refParent = refEntity.getOwner();
      while (refParent != null && !(refParent instanceof RefProject) && !(refParent instanceof RefPackage)) {
        result.insert(0, '.');
        result.insert(0, refParent.getName());
        refParent = refParent.getOwner();
      }

      return result.toString();
    }
  }

  public static String getAccessModifier(PsiModifierListOwner psiElement) {
    if (psiElement instanceof PsiParameter) return PsiModifier.PACKAGE_LOCAL;

    PsiModifierList list = psiElement.getModifierList();
    String result = PsiModifier.PACKAGE_LOCAL;

    if (list != null) {
      if (list.hasModifierProperty(PsiModifier.PRIVATE)) {
        result = PsiModifier.PRIVATE;
      }
      else if (list.hasModifierProperty(PsiModifier.PROTECTED)) {
        result = PsiModifier.PROTECTED;
      }
      else if (list.hasModifierProperty(PsiModifier.PUBLIC)) {
        result = PsiModifier.PUBLIC;
      }
      else if (psiElement.getParent() instanceof PsiClass) {
        PsiClass ownerClass = (PsiClass)psiElement.getParent();
        if (ownerClass.isInterface()) {
          result = PsiModifier.PUBLIC;
        }
      }
    }

    return result;
  }

  @Nullable public static RefClass getOwnerClass(RefManager refManager, PsiElement psiElement) {
    while (psiElement != null && !(psiElement instanceof PsiClass)) {
      psiElement = psiElement.getParent();
    }

    return psiElement != null ? (RefClass)refManager.getReference(psiElement) : null;
  }

  @Nullable public static RefClass getOwnerClass(RefElement refElement) {
    RefEntity parent = refElement.getOwner();

    while (!(parent instanceof RefClass) && parent instanceof RefElement) {
      parent = parent.getOwner();
    }

    if (parent instanceof RefClass) return (RefClass)parent;

    return null;
  }

  @Nullable public static PsiClass getPsiOwnerClass(PsiElement psiElement) {
    PsiElement parent = psiElement.getParent();

    while (!(parent instanceof PsiClass) && !(parent instanceof PsiFile) && parent != null) {
      parent = parent.getParent();
    }

    if (parent != null && parent instanceof PsiClass) return (PsiClass)parent;

    return null;
  }

  static boolean samePsiElement(PsiElement e1, PsiElement e2) {
    PsiManager manager = e1.getManager();

    return manager.areElementsEquivalent(e1, e2);
  }

  public static boolean isAnonymousClass(RefElement element) {
    if (element instanceof RefClass) {
      if (((RefClass)element).isAnonymous()) return true;
    }

    return false;
  }

  public static boolean isMethodOnlyCallsSuper(PsiMethod method) {
    boolean hasStatements = false;
    PsiCodeBlock body = method.getBody();
    if (body != null) {
      PsiStatement[] statements = body.getStatements();
      for (PsiStatement statement : statements) {
        boolean isCallToSameSuper = false;
        if (statement instanceof PsiExpressionStatement) {
          isCallToSameSuper = isCallToSuperMethod(((PsiExpressionStatement)statement).getExpression(), method);
        }
        else if (statement instanceof PsiReturnStatement) {
          PsiExpression expression = ((PsiReturnStatement)statement).getReturnValue();
          isCallToSameSuper = expression == null || isCallToSuperMethod(expression, method);
        }

        hasStatements = true;
        if (isCallToSameSuper) continue;

        return false;
      }
    }

    return hasStatements;
  }

  public static boolean isCallToSuperMethod(PsiExpression expression, PsiMethod method) {
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
      if (methodCall.getMethodExpression().getQualifierExpression() instanceof PsiSuperExpression) {
        PsiMethod superMethod = (PsiMethod)methodCall.getMethodExpression().resolve();
        if (superMethod == null || !MethodSignatureUtil.areSignaturesEqual(method, superMethod)) return false;
        PsiExpression[] args = methodCall.getArgumentList().getExpressions();
        PsiParameter[] parms = method.getParameterList().getParameters();

        for (int i = 0; i < args.length; i++) {
          PsiExpression arg = args[i];
          if (!(arg instanceof PsiReferenceExpression)) return false;
          if (!parms[i].equals(((PsiReferenceExpression)arg).resolve())) return false;
        }

        return true;
      }
    }

    return false;
  }

  public static int compareAccess(String a1, String a2) {
    int i1 = getAccessNumber(a1);
    int i2 = getAccessNumber(a2);

    if (i1 == i2) return 0;
    if (i1 < i2) return -1;
    return 1;
  }

  @SuppressWarnings("StringEquality")
    private static int getAccessNumber(String a) {
    if (a == PsiModifier.PRIVATE) {
      return 0;
    }
    else if (a == PsiModifier.PACKAGE_LOCAL) {
      return 1;
    }
    else if (a == PsiModifier.PROTECTED) {
      return 2;
    }
    else if (a == PsiModifier.PUBLIC) return 3;

    return -1;
  }

  public static void removeRefElement(RefElement refElement, ArrayList<RefElement> deletedRefs) {
    if (refElement.isEntry()) {
      EntryPointsManager.getInstance(refElement.getRefManager().getProject()).removeEntryPoint(refElement);
    }

    ArrayList children = refElement.getChildren();
    if (children != null) {
      RefElement[] refElements = (RefElement[])children.toArray(new RefElement[children.size()]);
      for (RefElement refChild : refElements) {
        removeRefElement(refChild, deletedRefs);
      }
    }

    refElement.getRefManager().removeReference(refElement);
    refElement.referenceRemoved();
    if (!deletedRefs.contains(refElement)) deletedRefs.add(refElement);
  }

  public static void addTypeReference(PsiElement psiElement, PsiType psiType, RefManager refManager) {
    RefClass ownerClass = getOwnerClass(refManager, psiElement);

    if (ownerClass != null) {
      psiType = psiType.getDeepComponentType();

      if (psiType instanceof PsiClassType) {
        PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
        if (psiClass != null && belongsToScope(psiClass, refManager)) {
          RefClass refClass = (RefClass)refManager.getReference(psiClass);
          if (refClass != null) {
            refClass.addTypeReference(ownerClass);
          }
        }
      }
    }
  }

  public static void updateCanBeStatic(RefElement refElement, PsiModifierListOwner psiThis, PsiModifierListOwner psiWhat) {
    if (refElement.getOwner() instanceof RefPackage) return;

    if (psiWhat instanceof PsiLocalVariable || psiWhat instanceof PsiParameter) return;

    if (psiThis.hasModifierProperty(PsiModifier.STATIC)) return;

    if (psiThis instanceof PsiAnonymousClass) {
      RefElement refOwner = (RefElement)refElement.getOwner();
      updateCanBeStatic(refOwner, ((PsiModifierListOwner)refOwner.getElement()), psiWhat);
    }
    else if (!psiWhat.hasModifierProperty(PsiModifier.STATIC)) {
      PsiClass whatClass = getPsiOwnerClass(psiWhat);
      PsiClass myClass = getPsiOwnerClass(psiThis);

      if (whatClass == null || myClass == null) return;

      if (isInheritor(myClass, whatClass)) {
        refElement.setCanBeStatic(false);
        if (refElement instanceof RefClass) return;
      }

      RefElement refMyClass = refElement.getRefManager().getReference(myClass);
      updateCanBeStatic(refMyClass, myClass, psiWhat);
    }
  }
}
