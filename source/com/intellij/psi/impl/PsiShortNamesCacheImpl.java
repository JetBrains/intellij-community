package com.intellij.psi.impl;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaFieldNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaMethodNameIndex;
import com.intellij.psi.impl.java.stubs.index.JavaShortClassNameIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

class PsiShortNamesCacheImpl extends PsiShortNamesCache {
  private final PsiManagerEx myManager;

  public PsiShortNamesCacheImpl(PsiManagerEx manager) {
    myManager = manager;
  }

  @NotNull
  public PsiFile[] getFilesByName(@NotNull String name) {
    return FilenameIndex.getFilesByName(myManager.getProject(), name, GlobalSearchScope.projectScope(myManager.getProject()));
  }

  @NotNull
  public String[] getAllFileNames() {
    return FilenameIndex.getAllFilenames(myManager.getProject());
  }

  @NotNull
  public PsiClass[] getClassesByName(@NotNull String name, @NotNull final GlobalSearchScope scope) {
    final Collection<PsiClass> classes = JavaShortClassNameIndex.getInstance().get(name, myManager.getProject(), scope);

    if (classes.isEmpty()) return PsiClass.EMPTY_ARRAY;
    ArrayList<PsiClass> list = new ArrayList<PsiClass>(classes.size());

    OuterLoop:
    for (PsiClass aClass : classes) {
      VirtualFile vFile = aClass.getContainingFile().getVirtualFile();
      if (!scope.contains(vFile)) continue;

      for (int j = 0; j < list.size(); j++) {
        PsiClass aClass1 = list.get(j);

        String qName = aClass.getQualifiedName();
        String qName1 = aClass1.getQualifiedName();
        if (qName != null && qName1 != null && qName.equals(qName1)) {
          VirtualFile vFile1 = aClass1.getContainingFile().getVirtualFile();
          int res = scope.compare(vFile1, vFile);
          if (res > 0) {
            continue OuterLoop; // aClass1 hides aClass
          }
          else if (res < 0) {
            list.remove(j);
            //noinspection AssignmentToForLoopParameter
            j--;      // aClass hides aClass1
          }
        }
      }

      list.add(aClass);
    }
    return list.toArray(new PsiClass[list.size()]);
  }

  @NotNull
  public String[] getAllClassNames() {
    final Collection<String> names = JavaShortClassNameIndex.getInstance().getAllKeys(myManager.getProject());
    return ArrayUtil.toStringArray(names);
  }

  public void getAllClassNames(@NotNull HashSet<String> set) {
    set.addAll(JavaShortClassNameIndex.getInstance().getAllKeys(myManager.getProject()));
  }

  @NotNull
  public PsiMethod[] getMethodsByName(@NotNull String name, @NotNull final GlobalSearchScope scope) {
    final Collection<PsiMethod> methods =
        StubIndex.getInstance().get(JavaMethodNameIndex.KEY, name, myManager.getProject(), scope);
    if (methods.isEmpty()) return PsiMethod.EMPTY_ARRAY;

    List<PsiMethod> list = filterMembers(methods, scope);
    return list.toArray(new PsiMethod[list.size()]);
  }


  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull final String name, @NotNull final GlobalSearchScope scope, final int maxCount) {
    return getMethodsByName(name, scope); // TODO!!!
  }

  @NotNull
  public String[] getAllMethodNames() {
    final Collection<String> names = JavaMethodNameIndex.getInstance().getAllKeys(myManager.getProject());
    return ArrayUtil.toStringArray(names);
  }

  public void getAllMethodNames(@NotNull HashSet<String> set) {
    set.addAll(JavaMethodNameIndex.getInstance().getAllKeys(myManager.getProject()));
  }

  @NotNull
  public PsiField[] getFieldsByName(@NotNull String name, @NotNull final GlobalSearchScope scope) {
    final Collection<PsiField> fields = JavaFieldNameIndex.getInstance().get(name, myManager.getProject(), scope);

    if (fields.isEmpty()) return PsiField.EMPTY_ARRAY;

    List<PsiField> list = filterMembers(fields, scope);
    return list.toArray(new PsiField[list.size()]);
  }

  @NotNull
  public String[] getAllFieldNames() {
    final Collection<String> names = JavaFieldNameIndex.getInstance().getAllKeys(myManager.getProject());
    return ArrayUtil.toStringArray(names);
  }

  public void getAllFieldNames(@NotNull HashSet<String> set) {
    set.addAll(JavaFieldNameIndex.getInstance().getAllKeys(myManager.getProject()));
  }

  private <T extends PsiMember> List<T> filterMembers(Collection<T> members, final GlobalSearchScope scope) {
    List<T> result = new ArrayList<T>();
    Set<PsiMember> set = new THashSet<PsiMember>(members.size(), new TObjectHashingStrategy<PsiMember>() {
      public int computeHashCode(PsiMember member) {
        int code = 0;
        final PsiClass clazz = member.getContainingClass();
        if (clazz != null) {
          String name = clazz.getName();
          if (name != null) {
            code += name.hashCode();
          } else {
            //anonymous classes are not equivalent
            code += clazz.hashCode();
          }
        }
        if (member instanceof PsiMethod) {
          code += 37 * ((PsiMethod)member).getParameterList().getParametersCount();
        }
        return code;
      }

      public boolean equals(PsiMember object, PsiMember object1) {
        return myManager.areElementsEquivalent(object, object1);
      }
    });

    for (T member : members) {
      ProgressManager.getInstance().checkCanceled();

      if (!scope.contains(member.getContainingFile().getVirtualFile())) continue;
      if (!set.add(member)) continue;
      result.add(member);
    }

    return result;
  }
}
