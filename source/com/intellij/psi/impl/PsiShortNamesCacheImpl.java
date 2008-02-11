package com.intellij.psi.impl;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.RepositoryIndex;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Set;

class PsiShortNamesCacheImpl implements PsiShortNamesCache {
  private final PsiManagerEx myManager;

  private RepositoryIndex myRepositoryIndex = null;

  public PsiShortNamesCacheImpl(PsiManagerEx manager, ProjectRootManager projectRootManager) {
    myManager = manager;
  }

  public void runStartupActivity() {
  }

  @NotNull
  public PsiFile[] getFilesByName(@NotNull String name) {
    return FilenameIndex.getFilesByName(myManager.getProject(), name, GlobalSearchScope.projectScope(myManager.getProject()));
  }

  @NotNull
  public String[] getAllFileNames() {
    return FilenameIndex.getAllFilenames();
  }

  @NotNull
  public PsiClass[] getClassesByName(@NotNull String name, @NotNull final GlobalSearchScope scope) {
    VirtualFileFilter filter = getRepositoryIndex().rootFilterBySearchScope(scope);
    long[] classIds = getRepositoryIndex().getClassesByShortName(name, filter);

    if (classIds.length == 0) return PsiClass.EMPTY_ARRAY;
    RepositoryElementsManager repositoryElementsManager = myManager.getRepositoryElementsManager();
    ArrayList<PsiClass> list = new ArrayList<PsiClass>();
    IdLoop:
    for (long id : classIds) {
      PsiClass aClass = (PsiClass)repositoryElementsManager.findOrCreatePsiElementById(id);
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
            continue IdLoop; // aClass1 hides aClass
          }
          else if (res < 0) {
            list.remove(j);
            j--;      // aClass hides aClass1
          }
        }
      }

      list.add(aClass);
    }
    return list.toArray(new PsiClass[list.size()]);
  }

  @NotNull
  public String[] getAllClassNames(boolean searchInLibraries) {
    VirtualFileFilter filter = createFilter(searchInLibraries);
    return getRepositoryIndex().getAllClassNames(filter);
  }

  public void getAllClassNames(boolean searchInLibraries, @NotNull HashSet<String> set) {
    VirtualFileFilter filter = createFilter(searchInLibraries);
    getRepositoryIndex().getAllClassNames(filter, set);
  }

  @NotNull
  public PsiMethod[] getMethodsByName(@NotNull String name, @NotNull final GlobalSearchScope scope) {
    VirtualFileFilter filter = getRepositoryIndex().rootFilterBySearchScope(scope);
    long[] methodIds = getRepositoryIndex().getMethodsByName(name, filter);

    if (methodIds.length == 0) return PsiMethod.EMPTY_ARRAY;
    ArrayList<PsiMethod> list = new ArrayList<PsiMethod>();
    addMembersByIds(list, methodIds, scope);
    return list.toArray(new PsiMethod[list.size()]);
  }


  @NotNull
  public PsiMethod[] getMethodsByNameIfNotMoreThan(@NonNls @NotNull final String name, @NotNull final GlobalSearchScope scope, final int maxCount) {
    VirtualFileFilter filter = getRepositoryIndex().rootFilterBySearchScope(scope);
    long[] methodIds = getRepositoryIndex().getMethodsByNameIfNotMoreThan(name, filter, maxCount);

    if (methodIds.length == 0) return PsiMethod.EMPTY_ARRAY;
    ArrayList<PsiMethod> list = new ArrayList<PsiMethod>();
    addMembersByIds(list, methodIds, scope);
    return list.toArray(new PsiMethod[list.size()]);
  }

  @NotNull
  public String[] getAllMethodNames(boolean searchInLibraries) {
    VirtualFileFilter filter = createFilter(searchInLibraries);
    return getRepositoryIndex().getAllMethodNames(filter);
  }

  public void getAllMethodNames(boolean searchInLibraries, @NotNull HashSet<String> set) {
    VirtualFileFilter filter = createFilter(searchInLibraries);
    getRepositoryIndex().getAllMethodNames(filter, set);
  }

  @NotNull
  public PsiField[] getFieldsByName(@NotNull String name, @NotNull final GlobalSearchScope scope) {
    VirtualFileFilter filter = getRepositoryIndex().rootFilterBySearchScope(scope);
    long[] fieldIds = getRepositoryIndex().getFieldsByName(name, filter);

    if (fieldIds.length == 0) return PsiField.EMPTY_ARRAY;
    ArrayList<PsiField> list = new ArrayList<PsiField>();
    addMembersByIds(list, fieldIds, scope);
    return list.toArray(new PsiField[list.size()]);
  }

  @NotNull
  public String[] getAllFieldNames(boolean searchInLibraries) {
    VirtualFileFilter filter = createFilter(searchInLibraries);
    return getRepositoryIndex().getAllFieldNames(filter);
  }

  public void getAllFieldNames(boolean searchInLibraries, @NotNull HashSet<String> set) {
    VirtualFileFilter filter = createFilter(searchInLibraries);
    getRepositoryIndex().getAllFieldNames(filter, set);
  }

  private <T extends PsiMember> void addMembersByIds(ArrayList<T> list, long[] ids, final GlobalSearchScope scope) {
    RepositoryElementsManager repositoryElementsManager = myManager.getRepositoryElementsManager();
    Set<PsiMember> set = new THashSet<PsiMember>(ids.length, new TObjectHashingStrategy<PsiMember>() {
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
    for (long id : ids) {
      ProgressManager.getInstance().checkCanceled();
      // this is internal repository contract
      //noinspection unchecked
      T member = (T)repositoryElementsManager.findOrCreatePsiElementById(id);
      if (!scope.contains(member.getContainingFile().getVirtualFile())) continue;
      if (!set.add(member)) continue;
      list.add(member);
    }
  }

  private VirtualFileFilter createFilter(boolean searchInLibraries) {
    if (!searchInLibraries) {
      return getRepositoryIndex().rootFilterBySearchScope(GlobalSearchScope.projectScope(myManager.getProject()));
    }
    else {
      return null;
    }
  }

  private RepositoryIndex getRepositoryIndex() {
    if (myRepositoryIndex == null) {
      myRepositoryIndex = myManager.getRepositoryManager().getIndex();
    }
    return myRepositoryIndex;
  }
}
