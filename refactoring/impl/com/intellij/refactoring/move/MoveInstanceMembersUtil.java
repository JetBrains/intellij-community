package com.intellij.refactoring.move;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.openapi.util.Pair;

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
        final Pair<PsiMember, PsiClass> pair = getMemberAndClassReferencedByThis(expression);
        if (pair != null) {
          PsiClass refClass = pair.getSecond();
          PsiMember member = pair.getFirst();
          if (refClass != null && !PsiTreeUtil.isAncestor(refMember, member, false)) {
            addReferencedMember(map, refClass, member);
          }
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

  private static Pair<PsiMember, PsiClass> getMemberAndClassReferencedByThis(final PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiExpression qualifier = ((PsiReferenceExpression)expression).getQualifierExpression();
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        final PsiElement resolved = ((PsiReferenceExpression)expression).resolve();
        if (resolved instanceof PsiMember && !((PsiMember)resolved).hasModifierProperty(PsiModifier.STATIC)) {
          PsiClass referencedClass = getReferencedClass((PsiMember)resolved, qualifier, expression);
          return new Pair<PsiMember, PsiClass>((PsiMember)resolved, referencedClass);
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
            PsiClass referencedClass = getReferencedClass(resolved, qualifier, expression);
            return new Pair<PsiMember, PsiClass>(resolved, referencedClass);
          }
        }
      }
    }

    return null;
  }

  private static PsiClass getReferencedClass(final PsiMember member, final PsiExpression exprQualifier, final PsiExpression expression) {
    PsiClass referencedClass = member.getContainingClass();
    if (exprQualifier != null) {
      final PsiType type = exprQualifier.getType();
      if (type instanceof PsiClassType) {
        referencedClass = ((PsiClassType)type).resolve();
      }
    }
    else {
      final PsiClass parentClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
      if (!PsiTreeUtil.isAncestor(referencedClass, parentClass, false)) {
        referencedClass = parentClass;
      }
    }
    return referencedClass;
  }

  public static PsiClass getClassReferencedByThis(final PsiExpression expression) {
    PsiClass enclosingClass = PsiTreeUtil.getParentOfType(expression, PsiClass.class);
    if (enclosingClass == null) return null;
    final Pair<PsiMember, PsiClass> pair = getMemberAndClassReferencedByThis(expression);
    if (pair != null) return pair.getSecond();

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
