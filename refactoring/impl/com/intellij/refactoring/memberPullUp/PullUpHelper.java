/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 14.06.2002
 * Time: 22:35:19
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.memberPullUp;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.refactoring.util.JavaDocPolicy;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;

import java.util.*;

public class PullUpHelper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.memberPullUp.PullUpHelper");
  private final PsiClass mySourceClass;
  private final PsiClass myTargetSuperClass;
  private final boolean myIsTargetInterface;
  private final MemberInfo[] myMembersToMove;
  private final JavaDocPolicy myJavaDocPolicy;
  private HashSet<PsiMember> myMembersAfterMove = null;
  private final PsiManager myManager;


  public PullUpHelper(PsiClass sourceClass, PsiClass targetSuperClass, MemberInfo[] membersToMove,
                      JavaDocPolicy javaDocPolicy) {
    mySourceClass = sourceClass;
    myTargetSuperClass = targetSuperClass;
    myMembersToMove = membersToMove;
    myJavaDocPolicy = javaDocPolicy;
    myIsTargetInterface = targetSuperClass.isInterface();
    myManager = mySourceClass.getManager();
  }

  public void moveMembersToBase()
          throws IncorrectOperationException {
    final HashSet<PsiMember> movedMembers = new HashSet<PsiMember>();
    myMembersAfterMove = new HashSet<PsiMember>();

    // build aux sets
    for (MemberInfo info : myMembersToMove) {
      movedMembers.add(info.getMember());
    }

    // correct private member visibility
    for (MemberInfo info : myMembersToMove) {
      if (info.getMember() instanceof PsiClass && info.getOverrides() != null) continue;
      PsiModifierListOwner modifierListOwner = info.getMember();
      if (myIsTargetInterface) {
        modifierListOwner.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
      }
      else if (modifierListOwner.hasModifierProperty(PsiModifier.PRIVATE)) {
        if (info.isToAbstract() || willBeUsedInSubclass(modifierListOwner, movedMembers, myTargetSuperClass, mySourceClass)) {
          modifierListOwner.getModifierList().setModifierProperty(PsiModifier.PROTECTED, true);
        }
      }
      ChangeContextUtil.encodeContextInfo(info.getMember(), true);
    }

    // do actual move
    for (MemberInfo info : myMembersToMove) {
      if (info.getMember() instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)info.getMember();
        final boolean isOriginalMethodAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT);
        if (myIsTargetInterface || info.isToAbstract()) {
          PsiMethod methodCopy = (PsiMethod)method.copy();
          ChangeContextUtil.clearContextInfo(method);
          RefactoringUtil.abstractizeMethod(myTargetSuperClass, methodCopy);

          myJavaDocPolicy.processCopiedJavaDoc(methodCopy.getDocComment(), method.getDocComment(), isOriginalMethodAbstract);

          final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(methodCopy);
          myMembersAfterMove.add(movedElement);
          if (isOriginalMethodAbstract) {
            method.delete();
          }
        }
        else {
          if (isOriginalMethodAbstract) {
            myTargetSuperClass.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, true);
          }
          fixReferencesToStatic(method, movedMembers);
          final PsiMethod superClassMethod = myTargetSuperClass.findMethodBySignature(method, false);
          if (superClassMethod != null && superClassMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            superClassMethod.replace(method);
          }
          else {
            final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(method);
            myMembersAfterMove.add(movedElement);
          }
          method.delete();
        }
      }
      else if (info.getMember() instanceof PsiField) {
        PsiField field = (PsiField)info.getMember();
        field.normalizeDeclaration();
        fixReferencesToStatic(field, movedMembers);
        if (myIsTargetInterface) {
          field.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        }
        final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(field);
        myMembersAfterMove.add(movedElement);
        field.delete();
      }
      else if (info.getMember() instanceof PsiClass) {
        PsiClass aClass = (PsiClass)info.getMember();
        if (Boolean.FALSE.equals(info.getOverrides())) {
          final PsiReferenceList sourceReferenceList = info.getSourceReferenceList();
          LOG.assertTrue(sourceReferenceList != null);
          PsiJavaCodeReferenceElement ref = mySourceClass.equals(sourceReferenceList.getParent()) ?
                                            RefactoringUtil.removeFromReferenceList(sourceReferenceList, aClass) :
                                            RefactoringUtil.findReferenceToClass(sourceReferenceList, aClass);
          if (ref != null) {
            final PsiReferenceList referenceList;
            if (!myTargetSuperClass.isInterface()) {
              referenceList = myTargetSuperClass.getImplementsList();
            }
            else {
              referenceList = myTargetSuperClass.getExtendsList();
            }
            assert referenceList != null;
            referenceList.add(ref);
          }
        }
        else {
          fixReferencesToStatic(aClass, movedMembers);
          final PsiMember movedElement = (PsiMember)myTargetSuperClass.add(aClass);
          myMembersAfterMove.add(movedElement);
          aClass.delete();
        }
      }
    }

    ExplicitSuperDeleter explicitSuperDeleter = new ExplicitSuperDeleter();
    for (PsiMember member : myMembersAfterMove) {
      member.accept(explicitSuperDeleter);
    }
    explicitSuperDeleter.fixSupers();

    final QualifiedThisSuperAdjuster qualifiedThisSuperAdjuster = new QualifiedThisSuperAdjuster();
    for (PsiMember member : myMembersAfterMove) {
      member.accept(qualifiedThisSuperAdjuster);
    }

    ChangeContextUtil.decodeContextInfo(myTargetSuperClass, null, null);
  }

  public void moveFieldInitializations() throws IncorrectOperationException {
    LOG.assertTrue(myMembersAfterMove != null);

    final HashSet<PsiField> movedFields = new HashSet<PsiField>();
    for (PsiMember member : myMembersAfterMove) {
      if (member instanceof PsiField) {
        movedFields.add((PsiField)member);
      }
    }

    if (movedFields.isEmpty()) return;
    PsiMethod[] constructors = myTargetSuperClass.getConstructors();

    if (constructors.length == 0) {
      constructors = new PsiMethod[]{null};
    }

    HashMap<PsiMethod,HashSet<PsiMethod>> constructorsToSubConstructors = buildConstructorsToSubConstructorsMap(constructors);
    for (PsiMethod constructor : constructors) {
      HashSet<PsiMethod> subConstructors = constructorsToSubConstructors.get(constructor);
      tryToMoveInitializers(constructor, subConstructors, movedFields);
    }
  }

  private static class Initializer {
    public final PsiExpression initializer;
    public final HashSet<PsiField> movedFieldsUsed;
    public final ArrayList<PsiElement> statementsToRemove;

    public Initializer(PsiExpression initializer, HashSet<PsiField> movedFieldsUsed, ArrayList<PsiElement> statementsToRemove) {
      this.initializer = initializer;
      this.movedFieldsUsed = movedFieldsUsed;
      this.statementsToRemove = statementsToRemove;
    }

  }

  private void tryToMoveInitializers(PsiMethod constructor, HashSet<PsiMethod> subConstructors, HashSet<PsiField> movedFields) throws IncorrectOperationException {
    final HashMap<PsiField, Initializer> fieldsToInitializers = new HashMap<PsiField, Initializer>();
    boolean anyFound = false;

    for (PsiField field : movedFields) {
      PsiExpression commonInitializer = null;
      final ArrayList<PsiElement> fieldInitializersToRemove = new ArrayList<PsiElement>();
      for (PsiMethod subConstructor : subConstructors) {
        commonInitializer = hasCommonInitializer(commonInitializer, subConstructor, field, fieldInitializersToRemove);
        if (commonInitializer == null) break;
      }
      if (commonInitializer != null) {
        final MovedFieldsUsed visitor = new MovedFieldsUsed(movedFields);
        commonInitializer.accept(visitor);
        fieldsToInitializers.put(field, new Initializer(commonInitializer,
                                                        visitor.getUsedFields(), fieldInitializersToRemove));
        anyFound = true;
      }
    }

    if (!anyFound) return;



    {
      final Set<PsiField> initializedFields = fieldsToInitializers.keySet();
      Set<PsiField> unmovable = RefactoringUtil.transitiveClosure(
              new RefactoringUtil.Graph<PsiField>() {
                public Set<PsiField> getVertices() {
                  return initializedFields;
                }

                public Set<PsiField> getTargets(PsiField source) {
                  return fieldsToInitializers.get(source).movedFieldsUsed;
                }
              },
              new Condition<PsiField>() {
                public boolean value(PsiField object) {
                  return !initializedFields.contains(object);
                }
              }
      );

      for (PsiField psiField : unmovable) {
        fieldsToInitializers.remove(psiField);
      }
    }

    final PsiElementFactory factory = myManager.getElementFactory();

    if (constructor == null) {
      constructor = (PsiMethod) myTargetSuperClass.add(factory.createConstructor());
      final String visibilityModifier = VisibilityUtil.getVisibilityModifier(myTargetSuperClass.getModifierList());
      constructor.getModifierList().setModifierProperty(visibilityModifier, true);
    }


    ArrayList<PsiField> initializedFields = new ArrayList<PsiField>(fieldsToInitializers.keySet());

    Collections.sort(initializedFields, new Comparator<PsiField>() {
      public int compare(PsiField field1, PsiField field2) {
        Initializer i1 = fieldsToInitializers.get(field1);
        Initializer i2 = fieldsToInitializers.get(field2);
        if(i1.movedFieldsUsed.contains(field2)) return 1;
        if(i2.movedFieldsUsed.contains(field1)) return -1;
        return 0;
      }
    });

    for (final PsiField initializedField : initializedFields) {
      Initializer initializer = fieldsToInitializers.get(initializedField);

      // create assignment statement
      PsiExpressionStatement assignmentStatement =
        (PsiExpressionStatement)factory.createStatementFromText(initializedField.getName() + "=0;", constructor.getBody());
      assignmentStatement = (PsiExpressionStatement)CodeStyleManager.getInstance(myManager.getProject()).reformat(assignmentStatement);
      assignmentStatement = (PsiExpressionStatement)constructor.getBody().add(assignmentStatement);
      PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)assignmentStatement.getExpression();

      // check whether we really assign a new field
      PsiReferenceExpression fieldRef = (PsiReferenceExpression)assignmentExpression.getLExpression();
      PsiElement resolved = fieldRef.resolve();
      if (resolved != initializedField) {
        PsiElement qualifiedRef = factory.createExpressionFromText("this." + initializedField.getName(), fieldRef);
        qualifiedRef = CodeStyleManager.getInstance(myManager.getProject()).reformat(qualifiedRef);
        fieldRef.replace(qualifiedRef);
      }

      // add initializer
      final PsiElement newInitializer = assignmentExpression.getRExpression().replace(initializer.initializer);
      ChangeContextUtil.decodeContextInfo(newInitializer,
                                          myTargetSuperClass, RefactoringUtil.createThisExpression(myManager, null));
      for (PsiElement psiElement : initializer.statementsToRemove) {
        psiElement.delete();
      }
    }
  }

  private PsiExpression hasCommonInitializer(PsiExpression commonInitializer, PsiMethod subConstructor, PsiField field, ArrayList<PsiElement> statementsToRemove) {
    PsiExpression commonInitializerCandidate = null;
    final PsiCodeBlock body = subConstructor.getBody();
    if (body == null) return null;
    final PsiStatement[] statements = body.getStatements();

    // Algorithm: there should be only one write usage of field in a subConstructor,
    // and in that usage field must be a target of top-level assignment, and RHS of assignment
    // should be the same as commonInitializer if latter is non-null.
    //
    // There should be no usages before that initializer, and there should be
    // no write usages afterwards.
    for (PsiStatement statement : statements) {
      boolean doLookup = true;
      if (statement instanceof PsiExpressionStatement) {
        final PsiExpression expression = ((PsiExpressionStatement)statement).getExpression();
        if (expression instanceof PsiAssignmentExpression) {
          final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
          final PsiExpression lExpression = assignmentExpression.getLExpression();
          if (lExpression instanceof PsiReferenceExpression) {
            final PsiReferenceExpression lRef = ((PsiReferenceExpression)lExpression);
            if (lRef.getQualifierExpression() == null || lRef.getQualifierExpression() instanceof PsiThisExpression) {
              final PsiElement resolved = lRef.resolve();
              if (resolved == field) {
                doLookup = false;
                if (commonInitializerCandidate == null) {
                  final PsiExpression initializer = assignmentExpression.getRExpression();
                  if (commonInitializer == null) {
                    final IsMovableInitializerVisitor visitor = new IsMovableInitializerVisitor();
                    initializer.accept(visitor);
                    if (visitor.isMovable()) {
                      ChangeContextUtil.encodeContextInfo(initializer, true);
                      PsiExpression initializerCopy = (PsiExpression)initializer.copy();
                      ChangeContextUtil.clearContextInfo(initializer);
                      statementsToRemove.add(statement);
                      commonInitializerCandidate = initializerCopy;
                    }
                    else {
                      return null;
                    }
                  }
                  else {
                    if (CodeInsightUtil.areExpressionsEquivalent(commonInitializer, initializer)) {
                      statementsToRemove.add(statement);
                      commonInitializerCandidate = commonInitializer;
                    }
                    else {
                      return null;
                    }
                  }
                }
                else {
                  return null;
                }
              }
            }
          }
        }
      }

      if (doLookup) {
        final PsiReference[] references =
          myManager.getSearchHelper().findReferences(field, new LocalSearchScope(statement), false);
        if (commonInitializerCandidate == null && references.length > 0) {
          return null;
        }

        for (PsiReference reference : references) {
          if (RefactoringUtil.isAssignmentLHS(reference.getElement())) return null;
        }
      }
    }
    return commonInitializerCandidate;
  }

  private static class MovedFieldsUsed extends PsiRecursiveElementVisitor {
    private final HashSet<PsiField> myMovedFields;
    private final HashSet<PsiField> myUsedFields;

    public MovedFieldsUsed(HashSet<PsiField> movedFields) {
      myMovedFields = movedFields;
      myUsedFields = new HashSet<PsiField>();
    }

    public HashSet<PsiField> getUsedFields() {
      return myUsedFields;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      final PsiExpression qualifierExpression = expression.getQualifierExpression();
      if (qualifierExpression != null
              && !(qualifierExpression instanceof PsiThisExpression)) {
        return;
      }
      final PsiElement resolved = expression.resolve();
      if (myMovedFields.contains(resolved)) {
        myUsedFields.add((PsiField)resolved);
      }
    }
  }

  private class IsMovableInitializerVisitor extends PsiRecursiveElementVisitor {
    private boolean myIsMovable = true;

    public boolean isMovable() {
      return myIsMovable;
    }

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement referenceElement) {
      if (!myIsMovable) return;
      final PsiExpression qualifier;
      if (referenceElement instanceof PsiReferenceExpression) {
        qualifier = ((PsiReferenceExpression) referenceElement).getQualifierExpression();
      } else {
        qualifier = null;
      }
      if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
        final PsiElement resolved = referenceElement.resolve();
        PsiClass containingClass = null;
        if (resolved instanceof PsiMember && !((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) {
          containingClass = ((PsiMember) resolved).getContainingClass();
        }
        myIsMovable = containingClass != null && InheritanceUtil.isInheritorOrSelf(myTargetSuperClass, containingClass, true);
      } else {
        qualifier.accept(this);
      }
    }

    public void visitElement(PsiElement element) {
      if (myIsMovable) {
        super.visitElement(element);
      }
    }
  }

  private HashMap<PsiMethod,HashSet<PsiMethod>> buildConstructorsToSubConstructorsMap(final PsiMethod[] constructors) {
    final com.intellij.util.containers.HashMap<PsiMethod,HashSet<PsiMethod>> constructorsToSubConstructors = new com.intellij.util.containers.HashMap<PsiMethod, HashSet<PsiMethod>>();
    for (PsiMethod constructor : constructors) {
      final HashSet<PsiMethod> referencingSubConstructors = new HashSet<PsiMethod>();
      constructorsToSubConstructors.put(constructor, referencingSubConstructors);
      if (constructor != null) {
        final PsiReference[] references
          = myManager.getSearchHelper().findReferences(constructor, new LocalSearchScope(mySourceClass), false);
        // find references
        for (PsiReference reference : references) {
          final PsiElement element = reference.getElement();
          if (element != null && "super".equals(element.getText())) {
            PsiMethod parentMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
            if (parentMethod != null && parentMethod.isConstructor()) {
              referencingSubConstructors.add(parentMethod);
            }
          }
        }
      }

      // check default constructor
      if (constructor == null || constructor.getParameterList().getParameters().length == 0) {
        RefactoringUtil.visitImplicitSuperConstructorUsages(mySourceClass, new RefactoringUtil.ImplicitConstructorUsageVisitor() {
          public void visitConstructor(PsiMethod constructor, PsiMethod baseConstructor) {
            referencingSubConstructors.add(constructor);
          }

          public void visitClassWithoutConstructors(PsiClass aClass) {
          }
        }, myTargetSuperClass);

      }
    }
    return constructorsToSubConstructors;
  }

  private void fixReferencesToStatic(PsiElement classMember, Set<PsiMember> movedMembers) throws IncorrectOperationException {
    StaticReferencesCollector collector = new StaticReferencesCollector(movedMembers);
    classMember.accept(collector);
    ArrayList<PsiJavaCodeReferenceElement> refs = collector.getReferences();
    ArrayList<PsiElement> members = collector.getReferees();
    ArrayList<PsiClass> classes = collector.getRefereeClasses();
    PsiElementFactory factory = classMember.getManager().getElementFactory();

    for (int i = 0; i < refs.size(); i++) {
      PsiJavaCodeReferenceElement ref = refs.get(i);
      PsiElement namedElement = members.get(i);
      PsiClass aClass = classes.get(i);

      if (namedElement instanceof PsiNamedElement) {
        PsiReferenceExpression newRef =
                (PsiReferenceExpression) factory.createExpressionFromText
                ("a." + ((PsiNamedElement) namedElement).getName(),
                        null);
        final PsiExpression qualifierExpression = newRef.getQualifierExpression();
        assert qualifierExpression != null;
        qualifierExpression.replace(factory.createReferenceExpression(aClass));
        ref.replace(newRef);
      }
    }
  }

  private class StaticReferencesCollector extends ClassMemberReferencesVisitor {
    ArrayList<PsiJavaCodeReferenceElement> myReferences;
    ArrayList<PsiElement> myReferees;
    ArrayList<PsiClass> myRefereeClasses;
    private final Set<PsiMember> myMovedMembers;

    public StaticReferencesCollector(Set<PsiMember> movedMembers) {
      super(mySourceClass);
      myMovedMembers = movedMembers;
      myReferees = new ArrayList<PsiElement>();
      myRefereeClasses = new ArrayList<PsiClass>();
      myReferences = new ArrayList<PsiJavaCodeReferenceElement>();
    }

    public ArrayList<PsiElement> getReferees() {
      return myReferees;
    }

    public ArrayList<PsiClass> getRefereeClasses() {
      return myRefereeClasses;
    }

    public ArrayList<PsiJavaCodeReferenceElement> getReferences() {
      return myReferences;
    }

    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if (classMember instanceof PsiClass) return;
      if (classMember.hasModifierProperty(PsiModifier.STATIC)
          && !myMovedMembers.contains(classMember)
          && RefactoringHierarchyUtil.isMemberBetween(myTargetSuperClass, mySourceClass, classMember)) {
        myReferences.add(classMemberReference);
        myReferees.add(classMember);
        myRefereeClasses.add(classMember.getContainingClass());
      }
      }
  }

  private class QualifiedThisSuperAdjuster extends PsiRecursiveElementVisitor {
    public void visitThisExpression(PsiThisExpression expression) {
      super.visitThisExpression(expression);
      final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
      if (qualifier != null && qualifier.isReferenceTo(mySourceClass)) {
        try {
          qualifier.bindToElement(myTargetSuperClass);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }

    public void visitSuperExpression(PsiSuperExpression expression) {
      super.visitSuperExpression(expression);
      final PsiJavaCodeReferenceElement qualifier = expression.getQualifier();
      if (qualifier != null && qualifier.isReferenceTo(mySourceClass)) {
        try {
          expression.replace(myManager.getElementFactory().createExpressionFromText(myTargetSuperClass.getName() + ".this", null));
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
  }

  private class ExplicitSuperDeleter extends PsiRecursiveElementVisitor {
    private ArrayList<PsiExpression> mySupersToDelete = new ArrayList<PsiExpression>();
    private ArrayList<PsiSuperExpression> mySupersToChangeToThis = new ArrayList<PsiSuperExpression>();

    public void visitReferenceExpression(PsiReferenceExpression expression) {
      if(expression.getQualifierExpression() instanceof PsiSuperExpression) {
        PsiElement resolved = expression.resolve();
        if (resolved == null || (resolved instanceof PsiMethod && shouldFixSuper((PsiMethod) resolved))) {
          mySupersToDelete.add(expression.getQualifierExpression());
        }
      }
    }

    public void visitSuperExpression(PsiSuperExpression expression) {
      mySupersToChangeToThis.add(expression);
    }

    public void visitClass(PsiClass aClass) {
      // do nothing
    }

    private boolean shouldFixSuper(PsiMethod method) {
      for (PsiMember element : myMembersAfterMove) {
        if (element instanceof PsiMethod) {
          PsiMethod member = ((PsiMethod)element);
          // if there is such member among moved members, super qualifier
          // should not be removed
          final PsiManager manager = method.getManager();
          if (manager.areElementsEquivalent(member.getContainingClass(), method.getContainingClass()) &&
              MethodSignatureUtil.areSignaturesEqual(member, method)) {
            return false;
          }
        }
      }

      final PsiMethod methodFromSuper = myTargetSuperClass.findMethodBySignature(method, false);
      if(methodFromSuper != null) {
        return false;
      }
      return true;
    }

    public void fixSupers() throws IncorrectOperationException {
      final PsiElementFactory factory = myManager.getElementFactory();
      PsiThisExpression thisExpression = (PsiThisExpression) factory.createExpressionFromText("this", null);
      for (PsiExpression psiExpression : mySupersToDelete) {
        psiExpression.delete();
      }

      for (PsiSuperExpression psiSuperExpression : mySupersToChangeToThis) {
        psiSuperExpression.replace(thisExpression);
      }
    }
  }

  private static boolean willBeUsedInSubclass(PsiElement member, Set<PsiMember> movedMembers, PsiClass superclass, PsiClass subclass) {
    PsiSearchHelper helper = member.getManager().getSearchHelper();
    PsiReference[] refs = helper.findReferences(member, new LocalSearchScope(subclass), false);
    for (PsiReference ref : refs) {
      PsiElement element = ref.getElement();
      if (!RefactoringHierarchyUtil.willBeInTargetClass(element, movedMembers, superclass, false)) {
        return true;
      }
    }
    return false;
  }

  public static boolean checkedInterfacesContain(MemberInfo[] memberInfos, PsiMethod psiMethod) {
    for (MemberInfo memberInfo : memberInfos) {
      if (memberInfo.isChecked() &&
          memberInfo.getMember() instanceof PsiClass &&
          Boolean.FALSE.equals(memberInfo.getOverrides())) {
        if (((PsiClass)memberInfo.getMember()).findMethodBySignature(psiMethod, true) != null) {
          return true;
        }
      }
    }
    return false;
  }

}
