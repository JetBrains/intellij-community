/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 17.06.2002
 * Time: 15:40:16
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.memberPullUp;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.classMembers.InterfaceContainmentVerifier;
import com.intellij.refactoring.util.classMembers.MemberInfo;

import java.util.*;

public class PullUpConflictsUtil {
  public static String[] checkConflicts(final MemberInfo[] infos,
                                        PsiClass subclass,
                                        PsiClass superClass,
                                        PsiPackage targetPackage,
                                        PsiDirectory targetDirectory,
                                        final InterfaceContainmentVerifier interfaceContainmentVerifier) {
    final Set<PsiElement> movedMembers = new HashSet<PsiElement>();
    final Set<PsiMethod> abstractMethods = new HashSet<PsiMethod>();
    final boolean isInterfaceTarget;
    final PsiElement targetRepresentativeElement;
    if (superClass != null) {
      isInterfaceTarget = superClass.isInterface();
      targetRepresentativeElement = superClass;
    }
    else {
      isInterfaceTarget = false;
      targetRepresentativeElement = targetDirectory;
    }
    for (int idx = 0; idx < infos.length; idx++) {
      PsiElement member = infos[idx].getMember();
      if (member instanceof PsiMethod) {
        if (!infos[idx].isToAbstract() && !isInterfaceTarget) {
          movedMembers.add(member);
        }
        else {
          abstractMethods.add((PsiMethod)member);
        }
      }
      else {
        movedMembers.add(member);
      }
    }
    final LinkedHashSet<String> conflictsList = new LinkedHashSet<String>();
    if (superClass != null) {
      checkSuperclassMembers(superClass, infos, conflictsList);
      if (isInterfaceTarget) {
        checkInterfaceTarget(infos, conflictsList);
      }
    }
    // check if moved methods use other members in the classes between Subclass and Superclass
    List<PsiElement> checkModuleConflictsList = new ArrayList<PsiElement>();
    for (Iterator<PsiElement> it = movedMembers.iterator(); it.hasNext();) {
      PsiElement member = it.next();
      if (member instanceof PsiMethod || member instanceof PsiClass) {
        ConflictingUsagesOfSubClassMembers visitor =
          new ConflictingUsagesOfSubClassMembers(member, movedMembers, abstractMethods, subclass, superClass,
                                                 superClass != null ? null : targetPackage, conflictsList,
                                                 interfaceContainmentVerifier);
        member.accept(visitor);
      }
      checkModuleConflictsList.add(member);
    }
    for (Iterator<PsiMethod> iterator = abstractMethods.iterator(); iterator.hasNext();) {
      final PsiMethod method = iterator.next();
      checkModuleConflictsList.add(method.getParameterList());
      checkModuleConflictsList.add(method.getReturnTypeElement());
    }
    RefactoringUtil.analyzeModuleConflicts(subclass.getProject(), checkModuleConflictsList,
                                           targetRepresentativeElement, conflictsList);
    String[] conflicts = conflictsList.toArray(new String[conflictsList.size()]);
    return conflicts;
  }

  private static void checkInterfaceTarget(MemberInfo[] infos, LinkedHashSet<String> conflictsList) {
    for (int i = 0; i < infos.length; i++) {
      PsiElement member = infos[i].getMember();

      if (member instanceof PsiField || member instanceof PsiClass) {

        if (!((PsiModifierListOwner)member).hasModifierProperty(PsiModifier.STATIC)
            && !(member instanceof PsiClass && ((PsiClass)member).isInterface())) {
          String message =
            ConflictsUtil.getDescription(member, false) + " is not static. "
            + " It cannot be moved to the interface";
          message = ConflictsUtil.capitalize(message);
          conflictsList.add(message);
        }
      }

      if (member instanceof PsiField && ((PsiField)member).getInitializer() == null) {
        String message =
          ConflictsUtil.getDescription(member, false) + " is not initialized in declaration. " +
          "Such fields are not allowed in interfaces.";
        conflictsList.add(ConflictsUtil.capitalize(message));
      }
    }
  }

  private static void checkSuperclassMembers(PsiClass superClass,
                                             MemberInfo[] infos,
                                             LinkedHashSet<String> conflictsList) {
    for (int i = 0; i < infos.length; i++) {
      PsiElement member = infos[i].getMember();
      boolean isConflict = false;
      if (member instanceof PsiField) {
        String name = ((PsiField)member).getName();

        isConflict = superClass.findFieldByName(name, false) != null;
      }
      else if (member instanceof PsiMethod) {
        final PsiMethod superClassMethod = superClass.findMethodBySignature((PsiMethod)member, false);
        isConflict = superClassMethod != null
                     && !superClassMethod.hasModifierProperty(PsiModifier.ABSTRACT);
      }

      if (isConflict) {
        String message =
          ConflictsUtil.getDescription(superClass, false) + " already contains a "
          + ConflictsUtil.getDescription(member, false);
        message = ConflictsUtil.capitalize(message);
        conflictsList.add(message);
      }
    }

  }

  private static class ConflictingUsagesOfSubClassMembers extends ClassMemberReferencesVisitor {
    private final PsiElement myScope;
    private final Set<PsiElement> myMovedMembers;
    private final Set<PsiMethod> myAbstractMethods;
    private final PsiClass mySubclass;
    private final PsiClass mySuperClass;
    private final PsiPackage myTargetPackage;
    private final Set<String> myConflictsList;
    private final InterfaceContainmentVerifier myInterfaceContainmentVerifier;

    ConflictingUsagesOfSubClassMembers(PsiElement scope,
                                       Set<PsiElement> movedMembers, Set<PsiMethod> abstractMethods,
                                       PsiClass subclass, PsiClass superClass,
                                       PsiPackage targetPackage, Set<String> conflictsList,
                                       InterfaceContainmentVerifier interfaceContainmentVerifier) {
      super(subclass);
      myScope = scope;
      myMovedMembers = movedMembers;
      myAbstractMethods = abstractMethods;
      mySubclass = subclass;
      mySuperClass = superClass;
      myTargetPackage = targetPackage;
      myConflictsList = conflictsList;
      myInterfaceContainmentVerifier = interfaceContainmentVerifier;
    }

    protected void visitClassMemberReferenceElement(PsiMember classMember,
                                                    PsiJavaCodeReferenceElement classMemberReference) {
      if (classMember != null
          && RefactoringHierarchyUtil.isMemberBetween(mySuperClass, mySubclass, classMember)) {
        if (classMember.hasModifierProperty(PsiModifier.STATIC)
            && !willBeMoved(classMember)) {
          final boolean isAccessible;
          if (mySuperClass != null) {
            isAccessible = PsiUtil.isAccessible(classMember, mySuperClass, null);
          }
          else if (myTargetPackage != null) {
            isAccessible = PsiUtil.isAccessibleFromPackage(classMember, myTargetPackage);
          }
          else {
            isAccessible = classMember.hasModifierProperty(PsiModifier.PUBLIC);
          }
          if (!isAccessible) {
            String message =
              ConflictsUtil.getDescription(myScope, false) + " uses " +
              ConflictsUtil.getDescription(classMember, true) + ", which is not accessible from the superclass";
            message = ConflictsUtil.capitalize(message);
            myConflictsList.add(message);

          }
          return;
        }
        if (!myAbstractMethods.contains(classMember) && !willBeMoved(classMember)) {
          if (!existsInSuperClass(classMember)) {
            String message =
              ConflictsUtil.getDescription(myScope, false) + " uses " +
              ConflictsUtil.getDescription(classMember, true) + ", which is not moved to the superclass";
            message = ConflictsUtil.capitalize(message);
            myConflictsList.add(message);
          }
        }
      }
    }

    private boolean willBeMoved(PsiElement element) {
      PsiElement parent = element;
      while (parent != null) {
        if (myMovedMembers.contains(parent)) return true;
        parent = parent.getParent();
      }
      return false;
    }

    private boolean existsInSuperClass(PsiElement classMember) {
      if (!(classMember instanceof PsiMethod)) return false;
      final PsiMethod method = ((PsiMethod)classMember);
      if (myInterfaceContainmentVerifier.checkedInterfacesContain(method)) return true;
      if (mySuperClass == null) return false;
      final PsiMethod methodBySignature = mySuperClass.findMethodBySignature(method, true);
      return methodBySignature != null;
    }
  }


}
