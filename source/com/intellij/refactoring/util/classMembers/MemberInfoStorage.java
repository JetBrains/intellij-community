package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.containers.HashMap;

import java.util.*;


public class MemberInfoStorage {
  private PsiClass myClass;
  private HashMap<PsiClass,List<MemberInfo>> myClassToMemberInfoMap = new HashMap<PsiClass, List<MemberInfo>>();
  private HashMap<PsiClass,LinkedHashSet<PsiClass>> myClassToSubclassesMap = new HashMap<PsiClass, LinkedHashSet<PsiClass>>();
  private HashMap<PsiClass,Set<PsiClass>> myTargetClassToExtendingMap = new HashMap<PsiClass, Set<PsiClass>>();
  private HashMap<PsiClass,MemberInfo[]> myTargetClassToMemberInfosMap = new HashMap<PsiClass, MemberInfo[]>();
  private HashMap<PsiClass,LinkedHashSet<MemberInfo>> myTargetClassToMemberInfosListMap = new HashMap<PsiClass, LinkedHashSet<MemberInfo>>();
  private HashMap<PsiClass,HashSet<MemberInfo>> myTargetClassToDuplicatedMemberInfosMap = new HashMap<PsiClass, HashSet<MemberInfo>>();
  private final MemberInfo.Filter myFilter;

  public MemberInfoStorage(PsiClass aClass, MemberInfo.Filter memberInfoFilter) {
    myClass = aClass;
    buildSubClassesMap(aClass);
    myFilter = memberInfoFilter;
  }

  private Set<PsiClass> getAllClasses() {
    return myClassToSubclassesMap.keySet();
  }

  public Set<PsiClass> getExtending(PsiClass baseClass) {
    Set<PsiClass> result = myTargetClassToExtendingMap.get(baseClass);
    if(result == null) {
      result = new HashSet<PsiClass>();
      result.add(baseClass);
      final Set<PsiClass> allClasses = getAllClasses();
      for (Iterator<PsiClass> iterator = allClasses.iterator(); iterator.hasNext();) {
        PsiClass aClass = iterator.next();
        if(aClass.isInheritor(baseClass, true)) {
          result.add(aClass);
        }
      }
      myTargetClassToExtendingMap.put(baseClass, result);
    }

    return result;
  }

  public List<MemberInfo> getClassMemberInfos(PsiClass aClass) {
    List<MemberInfo> result = myClassToMemberInfoMap.get(aClass);
    if(result == null) {
      ArrayList<MemberInfo> temp = new ArrayList<MemberInfo>();
      MemberInfo.extractClassMembers(aClass, temp, myFilter);
      result = Collections.unmodifiableList(temp);
      myClassToMemberInfoMap.put(aClass, result);
    }
    return result;
  }

  public MemberInfo[] getMemberInfosList(PsiClass baseClass) {
    MemberInfo[] result = myTargetClassToMemberInfosMap.get(baseClass);

    if (result == null) {
      LinkedHashSet<MemberInfo> list = getIntermediateClassesMemberInfosList(baseClass);
      result = list.toArray(new MemberInfo[list.size()]);
      myTargetClassToMemberInfosMap.put(baseClass, result);
    }

    return result;
  }

  public Set<MemberInfo> getDuplicatedMemberInfos(PsiClass baseClass) {
    HashSet<MemberInfo> result = myTargetClassToDuplicatedMemberInfosMap.get(baseClass);

    if(result == null) {
      result = buildDuplicatedMemberInfos(baseClass);
      myTargetClassToDuplicatedMemberInfosMap.put(baseClass, result);
    }
    return result;
  }

  private HashSet<MemberInfo> buildDuplicatedMemberInfos(PsiClass baseClass) {
    HashSet<MemberInfo> result = new HashSet<MemberInfo>();
    MemberInfo[] memberInfos = getMemberInfosList(baseClass);

    for (int i = 0; i < memberInfos.length; i++) {
      final MemberInfo memberInfo = memberInfos[i];
      final PsiElement member = memberInfo.getMember();

      for(int j = 0; j < i; j++) {
        final MemberInfo memberInfo1 = memberInfos[j];
        final PsiElement member1 = memberInfo1.getMember();
        if(memberConflict(member1,  member)) {
          result.add(memberInfo);
//        We let the first one be...
//          result.add(memberInfo1);
        }
      }
    }
    return result;
  }

  private boolean memberConflict(PsiElement member1, PsiElement member) {
    if(member instanceof PsiMethod && member1 instanceof PsiMethod) {
      return MethodSignatureUtil.areSignaturesEqual((PsiMethod) member, (PsiMethod) member1);
    }
    else if(member instanceof PsiField && member1 instanceof PsiField
            || member instanceof PsiClass && member1 instanceof PsiClass) {
      return ((PsiNamedElement) member).getName().equals(((PsiNamedElement) member1).getName());
    }
    return false;
  }


  private LinkedHashSet<MemberInfo> getIntermediateClassesMemberInfosList(PsiClass targetClass) {
    LinkedHashSet<MemberInfo> result = myTargetClassToMemberInfosListMap.get(targetClass);
    if(result == null) {
      result = new LinkedHashSet<MemberInfo>();
      LinkedHashSet<PsiClass> subclasses = getSubclasses(targetClass);
      for (Iterator<PsiClass> iterator = subclasses.iterator(); iterator.hasNext();) {
        PsiClass subclass = iterator.next();
        List<MemberInfo> memberInfos = getClassMemberInfos(subclass);
        result.addAll(memberInfos);
      }
      for (Iterator<PsiClass> iterator = subclasses.iterator(); iterator.hasNext();) {
        PsiClass subclass = iterator.next();
        result.addAll(getIntermediateClassesMemberInfosList(subclass));
      }
      myTargetClassToMemberInfosListMap.put(targetClass, result);
    }
    return result;
  }

  private LinkedHashSet<PsiClass> getSubclasses(PsiClass aClass) {
    LinkedHashSet<PsiClass> result = myClassToSubclassesMap.get(aClass);
    if(result == null) {
      result = new LinkedHashSet<PsiClass>();
      myClassToSubclassesMap.put(aClass, result);
    }
    return result;
  }

  private void buildSubClassesMap(PsiClass aClass) {
    final PsiReferenceList extendsList = aClass.getExtendsList();
    if (extendsList != null) {
      buildSubClassesMapForList(extendsList.getReferencedTypes(), aClass);
    }
    final PsiReferenceList implementsList = aClass.getImplementsList();
    if (implementsList != null) {
      buildSubClassesMapForList(implementsList.getReferencedTypes(), aClass);
    }
  }

  private void buildSubClassesMapForList(final PsiClassType[] classesList, PsiClass aClass) {
    for (int i = 0; i < classesList.length; i++) {
      PsiClassType element = classesList[i];
      PsiClass resolved = element.resolve();
      if(resolved != null) {
        PsiClass superClass = resolved;
        getSubclasses(superClass).add(aClass);
        buildSubClassesMap(superClass);
      }
    }
  }
}
