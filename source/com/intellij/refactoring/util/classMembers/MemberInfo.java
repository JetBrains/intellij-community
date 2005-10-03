/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 14.06.2002
 * Time: 20:31:59
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.HashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MemberInfo {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.extractSuperclass.MemberInfo");
  private PsiMember myMember;
  final private boolean isStatic;
  final private String displayName;
  private boolean isChecked = false;
  private boolean toAbstract = false;
  /**
   * TRUE if is overriden, FALSE if implemented, null if not implemented or overriden
   */
  final private Boolean overrides;
  private final PsiReferenceList mySourceReferenceList;

  public boolean isStatic() {
    return isStatic;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isChecked() {
    return isChecked;
  }

  public void setChecked(boolean checked) {
    isChecked = checked;
  }

  public boolean isToAbstract() {
    return toAbstract;
  }

  public void setToAbstract(boolean toAbstract) {
    this.toAbstract = toAbstract;
  }

  /**
   * Returns Boolean.TRUE if getMember() overrides something, Boolean.FALSE if getMember()
   * implements something, null if neither is the case.
   * If getMember() is a PsiClass, returns Boolean.TRUE if this class comes
   * from 'extends', Boolean.FALSE if it comes from 'implements' list, null
   * if it is an inner class.
   */
  public Boolean getOverrides() {
    return overrides;
  }

  public MemberInfo(PsiMember member) {
    this(member, false, null);
  }
  public MemberInfo(PsiMember member, boolean isSuperClass, PsiReferenceList sourceReferenceList) {
    LOG.assertTrue(member.isValid());
    myMember = member;
    mySourceReferenceList = sourceReferenceList;
    if (member instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) member;
      displayName = PsiFormatUtil.formatMethod(method,
                                               PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER | PsiFormatUtil.SHOW_PARAMETERS,
                                               PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER
      );
      PsiMethod[] superMethods = method.findSuperMethods();
      if (superMethods.length > 0) {
        overrides = !superMethods[0].hasModifierProperty(PsiModifier.ABSTRACT) ? Boolean.TRUE : Boolean.FALSE;
      }
      else {
        overrides = null;
      }
      isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    }
    else if (member instanceof PsiField) {
      PsiField field = (PsiField) member;
      displayName = PsiFormatUtil.formatVariable(
              field,
              PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER,
              PsiSubstitutor.EMPTY);
      isStatic = field.hasModifierProperty(PsiModifier.STATIC);
      overrides = null;
    }
    else if (member instanceof PsiClass) {
      PsiClass aClass = (PsiClass) member;

      if(isSuperClass) {
        if (aClass.isInterface()) {
          displayName = RefactoringBundle.message("member.info.implements.0", aClass.getName());
          overrides = Boolean.FALSE;
        }
        else {
          displayName = RefactoringBundle.message("member.info.extends.0", aClass.getName());
          overrides = Boolean.TRUE;
        }
      }
      else {
        displayName = aClass.getName();
        overrides = null;
      }
      isStatic = aClass.hasModifierProperty(PsiModifier.STATIC);
    }
    else {
      LOG.assertTrue(false);
      isStatic = false;
      displayName = "";
      overrides = null;
    }
  }

  public PsiMember getMember() {
    LOG.assertTrue(myMember.isValid());
    return myMember;
  }

  /**
   * Use this method solely to update element from smart pointer and the likes
   * @param element
   */
  public void updateMember(PsiMember element) {
    myMember = element;
  }

  public PsiReferenceList getSourceReferenceList() {
    return mySourceReferenceList;
  }

  public static interface Filter {
    boolean includeMember(PsiMember member);
  }

  public static MemberInfo[] extractClassMembers(PsiClass subclass, Filter filter, boolean extractInterfacesDeep) {
    List<MemberInfo> members = new ArrayList<MemberInfo>();
    extractClassMembers(subclass, members, filter, extractInterfacesDeep);
    return members.toArray(new MemberInfo[members.size()]);
  }

  public static void extractClassMembers(PsiClass subclass, List<MemberInfo> result, Filter filter, final boolean extractInterfacesDeep) {
    if (extractInterfacesDeep) {
      extractSuperInterfaces(subclass, filter, result, new HashSet<PsiClass>());
    }
    else {
      PsiClass[] interfaces = subclass.getInterfaces();
      PsiReferenceList sourceRefList = subclass.isInterface() ? subclass.getExtendsList() : subclass.getImplementsList();
      for (PsiClass anInterface : interfaces) {
        if (filter.includeMember(anInterface)) {
          result.add(new MemberInfo(anInterface, true, sourceRefList));
        }
      }
    }

    PsiClass[] innerClasses = subclass.getInnerClasses();
    for (PsiClass innerClass : innerClasses) {
      if (filter.includeMember(innerClass)) {
        result.add(new MemberInfo(innerClass));
      }
    }
    PsiMethod[] methods = subclass.getMethods();
    for (PsiMethod method : methods) {
      if (!method.isConstructor() && filter.includeMember(method)) {
        result.add(new MemberInfo(method));
      }
    }
    PsiField[] fields = subclass.getFields();
    for (final PsiField field : fields) {
      if (filter.includeMember(field)) {
        result.add(new MemberInfo(field));
      }
    }
  }

  private static void extractSuperInterfaces(final PsiClass subclass,
                                             final Filter filter,
                                             final List<MemberInfo> result,
                                             Set<PsiClass> processed) {
    if (!processed.contains(subclass)) {
      processed.add(subclass);
      extractSuperInterfacesFromReferenceList(subclass.getExtendsList(), filter, result, processed);
      extractSuperInterfacesFromReferenceList(subclass.getImplementsList(), filter, result, processed);
    }
  }

  private static void extractSuperInterfacesFromReferenceList(final PsiReferenceList referenceList,
                                                              final Filter filter,
                                                              final List<MemberInfo> result,
                                                              final Set<PsiClass> processed) {
    if (referenceList != null) {
      final PsiClassType[] extendsListTypes = referenceList.getReferencedTypes();
      for (PsiClassType extendsListType : extendsListTypes) {
        final PsiClass aSuper = extendsListType.resolve();
        if (aSuper != null) {
          if (aSuper.isInterface()) {
            if (filter.includeMember(aSuper)) {
              result.add(new MemberInfo(aSuper, true, referenceList));
            }
          }
          else {
            extractSuperInterfaces(aSuper, filter, result, processed);
          }
        }
      }
    }
  }
}
