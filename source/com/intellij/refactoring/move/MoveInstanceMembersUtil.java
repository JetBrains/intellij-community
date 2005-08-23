package com.intellij.refactoring.move;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;

import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * @author ven
 */
public class MoveInstanceMembersUtil {
  /**
   * @param member  nonstatic class member to search for class references in
   * @return Set<PsiMember> in result map may be null in case no member is needed, but class itself is.
   */
  public static Map<PsiClass, Set<PsiMember>> getThisClassesToMembers(final PsiMember member) {
    Map<PsiClass, Set<PsiMember>> map = new LinkedHashMap<PsiClass, Set<PsiMember>>();
    getThisClassesToMembers (member, map, member);
    return map;
  }

  private static void getThisClassesToMembers(final PsiElement scope, final Map<PsiClass, Set<PsiMember>> map, final PsiMember refMember) {
    if (scope instanceof PsiExpression) {
      final PsiExpression expression = (PsiExpression)scope;
      if (!(scope instanceof PsiReferenceExpression) || !((PsiReferenceExpression)scope).isReferenceTo(refMember)) {
        final PsiMember member = getMemberReferencedByThis(expression);
        if (member != null && member.getContainingClass() != null &&
          !PsiTreeUtil.isAncestor(refMember, member, false)) {
          addReferencedMember(map, member.getContainingClass(), member);
        }

        if (expression instanceof PsiThisExpression) {
          final PsiJavaCodeReferenceElement thisQualifier = ((PsiThisExpression)expression).getQualifier();
          PsiClass thisClass = thisQualifier == null ? PsiTreeUtil.getParentOfType(expression, PsiClass.class, true) : ((PsiClass)thisQualifier.resolve());
          if (thisClass != null) {
            addReferencedMember(map, thisClass, null);
          }
        }
      }
    }

    final PsiElement[] children = scope.getChildren();
    for (PsiElement child : children) {
      getThisClassesToMembers(child, map, refMember);
    }
  }

  private static void addReferencedMember(final Map<PsiClass, Set<PsiMember>> map, final PsiClass classReferenced, final PsiMember member) {
    Set<PsiMember> members = map.get(classReferenced);
    if (members == null) {
      members = new HashSet<PsiMember>();
      map.put(classReferenced, members);
    }
    members.add(member);
  }

  private static PsiMember getMemberReferencedByThis(final PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiExpression qualifier = ((PsiReferenceExpression)expression).getQualifierExpression();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        final PsiElement resolved = ((PsiReferenceExpression)expression).resolve();
        if (resolved instanceof PsiMember && !((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) {
          return (PsiMember)resolved;
        }
      }
    } else if (expression instanceof PsiNewExpression) {
      final PsiNewExpression newExpression = (PsiNewExpression)expression;
      final PsiExpression qualifier = newExpression.getQualifier();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
        if (classReference == null) {
          final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
          if (anonymousClass != null) {
            classReference = anonymousClass.getBaseClassReference();
          }
        }
        if (classReference != null) {
          final PsiClass resolved = (PsiClass)classReference.resolve();
          if (resolved != null && !resolved.hasModifierProperty(PsiModifier.STATIC)) {
            return resolved;
          }
        }
      }
    }

    return null;
  }

  public static PsiClass getClassReferencedByThis(final PsiExpression expression) {
    PsiClass enclosingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    if (enclosingClass == null) return null;
    final PsiMember member = getMemberReferencedByThis(expression);
    if (member != null) return member.getContainingClass();

    if (expression instanceof PsiThisExpression) {
      final PsiJavaCodeReferenceElement thisQualifier = ((PsiThisExpression)expression).getQualifier();
      if (thisQualifier == null) {
        return enclosingClass;
      }
      else {
        return (PsiClass)thisQualifier.resolve();
      }
    }
    return null;
  }
}
