package com.intellij.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.cache.RepositoryIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.containers.GenericHashSet;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashMap;
import gnu.trove.TObjectHashingStrategy;

import java.util.ArrayList;
import java.util.Arrays;

class PsiShortNamesCacheImpl implements PsiShortNamesCache {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiShortNamesCacheImpl");

  private final PsiManagerImpl myManager;
  private final ProjectFileIndex myProjectFileIndex;

  private THashMap<String,Object> myFileNameToFilesMap = new THashMap<String,Object>(); // short name --> VirtualFile or Pair of VirtualFile or ArrayList of VirtualFile

  private boolean myInitialized = false;
  private RepositoryIndex myRepositoryIndex = null;

  public PsiShortNamesCacheImpl(PsiManagerImpl manager, ProjectRootManager projectRootManager) {
    myManager = manager;
    myProjectFileIndex = projectRootManager.getFileIndex();
  }

  public void runStartupActivity() {
    fillCache();

    myManager.addPsiTreeChangeListener(new MyPsiTreeChangeListener());
  }

  public PsiFile[] getFilesByName(String name) {
    synchronized (PsiLock.LOCK) {
      fillCache();
      VirtualFile[] vFiles = getFiles(myFileNameToFilesMap, name);
      int originalSize = vFiles.length;
      ArrayList<PsiFile> files = new ArrayList<PsiFile>(vFiles.length);
      for (int i = 0; i < vFiles.length; i++) {
        VirtualFile vFile = vFiles[i];
        PsiFile file;
        if (!vFile.isValid() || (file = myManager.findFile(vFile)) == null) {
          VirtualFile[] newFiles = new VirtualFile[vFiles.length - 1];
          System.arraycopy(vFiles, 0, newFiles, 0, i);
          System.arraycopy(vFiles, i + 1, newFiles, i, newFiles.length - i);
          vFiles = newFiles;
          i--;
          continue;
        }
        files.add(file);
      }
      if (vFiles.length < originalSize) {
        putFiles(myFileNameToFilesMap, name, vFiles);
        if (vFiles.length == 0) {
          return PsiFile.EMPTY_ARRAY;
        }
      }
      return files.toArray(new PsiFile[files.size()]);
    }
  }

  public String[] getAllFileNames() {
    fillCache();
    return myFileNameToFilesMap.keySet().toArray(new String[myFileNameToFilesMap.size()]);
  }

  public PsiClass[] getClassesByName(String name, final GlobalSearchScope scope) {
    synchronized (PsiLock.LOCK) {
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
              j--;
              continue; // aClass hides aClass1
            }
          }
        }

        list.add(aClass);
      }
      return list.toArray(new PsiClass[list.size()]);
    }
  }

  public String[] getAllClassNames(boolean searchInLibraries) {
    VirtualFileFilter filter = createFilter(searchInLibraries);
    return getRepositoryIndex().getAllClassNames(filter);
  }

  public void getAllClassNames(boolean searchInLibraries, HashSet<String> set) {
    VirtualFileFilter filter = createFilter(searchInLibraries);
    getRepositoryIndex().getAllClassNames(filter, set);
  }

  public PsiMethod[] getMethodsByName(String name, final GlobalSearchScope scope) {
    synchronized (PsiLock.LOCK) {
      VirtualFileFilter filter = getRepositoryIndex().rootFilterBySearchScope(scope);
      long[] methodIds = getRepositoryIndex().getMethodsByName(name, filter);

      if (methodIds.length == 0) return PsiMethod.EMPTY_ARRAY;
      ArrayList<PsiElement> list = new ArrayList<PsiElement>();
      addElementsByIds(list, methodIds, scope);
      return list.toArray(new PsiMethod[list.size()]);
    }
  }

  public String[] getAllMethodNames(boolean searchInLibraries) {
    VirtualFileFilter filter = createFilter(searchInLibraries);
    return getRepositoryIndex().getAllMethodNames(filter);
  }

  public void getAllMethodNames(boolean searchInLibraries, HashSet<String> set) {
    VirtualFileFilter filter = createFilter(searchInLibraries);
    getRepositoryIndex().getAllMethodNames(filter, set);
  }

  public PsiField[] getFieldsByName(String name, final GlobalSearchScope scope) {
    synchronized (PsiLock.LOCK) {
      VirtualFileFilter filter = getRepositoryIndex().rootFilterBySearchScope(scope);
      long[] fieldIds = getRepositoryIndex().getFieldsByName(name, filter);

      if (fieldIds.length == 0) return PsiField.EMPTY_ARRAY;
      ArrayList<PsiElement> list = new ArrayList<PsiElement>();
      addElementsByIds(list, fieldIds, scope);
      return list.toArray(new PsiField[list.size()]);
    }
  }

  public String[] getAllFieldNames(boolean searchInLibraries) {
    VirtualFileFilter filter = createFilter(searchInLibraries);
    return getRepositoryIndex().getAllFieldNames(filter);
  }

  public void getAllFieldNames(boolean searchInLibraries, HashSet<String> set) {
    VirtualFileFilter filter = createFilter(searchInLibraries);
    getRepositoryIndex().getAllFieldNames(filter, set);
  }

  private void addElementsByIds(ArrayList<PsiElement> list, long[] ids, final GlobalSearchScope scope) {
    RepositoryElementsManager repositoryElementsManager = myManager.getRepositoryElementsManager();
    GenericHashSet<PsiElement> set = new GenericHashSet<PsiElement>(new TObjectHashingStrategy<PsiElement>() {
      public int computeHashCode(PsiElement psiElement) {
        if (psiElement instanceof PsiMember) {
          PsiMember member = (PsiMember)psiElement;
          int code = 0;
          if (member instanceof PsiMethod) {
            code += ((PsiMethod)member).getParameterList().getParameters().length;
          }
          PsiClass aClass = member.getContainingClass();
          if (aClass != null) {
            code += computeHashCode(aClass);
          }
          return code;
        }
        else {
          LOG.error(psiElement.toString());
          return 0;
        }
      }

      public boolean equals(PsiElement object, PsiElement object1) {
        return myManager.areElementsEquivalent(object, object1);
      }
    });
    for (long id : ids) {
      PsiElement element = repositoryElementsManager.findOrCreatePsiElementById(id);
      if (!scope.contains(element.getContainingFile().getVirtualFile())) continue;
      if (!set.add(element)) continue;
      list.add(element);
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

  private void fillCache() {
    if (myInitialized) return;

    _fillCache();

    myInitialized = true;
  }

  private void _fillCache() {
    myFileNameToFilesMap.clear();

    PsiDirectory[] projectRoots = myManager.getRootDirectories(PsiRootPackageType.PROJECT);
    for (PsiDirectory projectRoot : projectRoots) {
      cacheFilesInDirectory(projectRoot);
    }
  }

  private void cacheFilesInDirectory(PsiDirectory dir) {
    ProgressManager progressManager = ProgressManager.getInstance();
    ProgressIndicator progress = progressManager.getProgressIndicator();

    if (progress != null) {
      progress.pushState();
      progress.setText(PsiBundle.message("psi.scanning.files.progress"));
    }

    _cacheFilesInDirectory(dir);

    if (progress != null) {
      progress.popState();
    }
  }

  private void _cacheFilesInDirectory(PsiDirectory dir) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Scanning files in " + dir.getVirtualFile().getPresentableUrl());
    }

    ProgressManager progressManager = ProgressManager.getInstance();
    ProgressIndicator progress = progressManager.getProgressIndicator();
    if (progress != null) {
      progress.setText2(dir.getVirtualFile().getPresentableUrl());
    }

    PsiFile[] files = dir.getFiles();
    for (PsiFile file : files) {
      cacheFile(file);
    }

    PsiDirectory[] subdirs = dir.getSubdirectories();
    for (PsiDirectory subdir : subdirs) {
      _cacheFilesInDirectory(subdir);
    }
  }

  private void cacheFile(PsiFile file) {
    VirtualFile vFile = file.getVirtualFile();
    String fileName = vFile.getName();
    VirtualFile[] files = getFiles(myFileNameToFilesMap, fileName);
    if (!Arrays.asList(files).contains(vFile)) {
      VirtualFile[] newFiles = new VirtualFile[files.length + 1];
      System.arraycopy(files, 0, newFiles, 0, files.length);
      newFiles[files.length] = vFile;
      putFiles(myFileNameToFilesMap, fileName, newFiles);
    }
  }

  private void releaseFile(PsiFile file, String oldName) {
    VirtualFile[] files = getFiles(myFileNameToFilesMap, oldName);
    ArrayList<VirtualFile> list = new ArrayList<VirtualFile>();
    list.addAll(Arrays.asList(files));
    list.remove(file.getVirtualFile());
    putFiles(myFileNameToFilesMap, oldName, list.toArray(new VirtualFile[list.size()]));
  }

  private static VirtualFile[] getFiles(THashMap map, String key) {
    Object o = map.get(key);
    if (o == null) return VirtualFile.EMPTY_ARRAY;
    if (o instanceof VirtualFile) {
      return new VirtualFile[]{(VirtualFile)o};
    }
    else if (o instanceof Pair) {
      Pair pair = (Pair)o;
      return new VirtualFile[]{(VirtualFile)pair.first, (VirtualFile)pair.second};
    }
    else {
      return (VirtualFile[])o;
    }
  }

  private static void putFiles(THashMap<String,Object> map, String key, VirtualFile[] files) {
    if (files.length == 0) {
      map.remove(key);
    }
    else if (files.length == 1) {
      map.put(key, files[0]);
    }
    else if (files.length == 2) {
      map.put(key, new Pair(files[0], files[1]));
    }
    else {
      map.put(key, files);
    }
  }

  private RepositoryIndex getRepositoryIndex() {
    if (myRepositoryIndex == null) {
      myRepositoryIndex = myManager.getRepositoryManager().getIndex();
    }
    return myRepositoryIndex;
  }

  private class MyPsiTreeChangeListener extends PsiTreeChangeAdapter {
    public void childAdded(PsiTreeChangeEvent event) {
      if (!myInitialized) return;
      PsiElement child = event.getChild();
      if (child instanceof PsiDirectory) {
        VirtualFile vFile = ((PsiDirectory)child).getVirtualFile();
        if (myProjectFileIndex.isInContent(vFile)) {
          cacheFilesInDirectory((PsiDirectory)child);
        }
      }
      else if (child instanceof PsiFile) {
        VirtualFile vFile = ((PsiFile)child).getVirtualFile();
        if (myProjectFileIndex.isInContent(vFile)) {
          cacheFile((PsiFile)child);
        }
      }
    }

    public void childMoved(PsiTreeChangeEvent event) {
      if (!myInitialized) return;
      PsiElement child = event.getChild();
      if (child instanceof PsiDirectory) {
        VirtualFile vFile = ((PsiDirectory)child).getVirtualFile();
        if (myProjectFileIndex.isInContent(vFile)) {
          cacheFilesInDirectory((PsiDirectory)child);
        }
      }
      else if (child instanceof PsiFile) {
        VirtualFile vFile = ((PsiFile)child).getVirtualFile();
        if (myProjectFileIndex.isInContent(vFile)) {
          cacheFile((PsiFile)child);
        }
      }
    }

    public void propertyChanged(PsiTreeChangeEvent event) {
      if (!myInitialized) return;

      String propertyName = event.getPropertyName();
      if (PsiTreeChangeEvent.PROP_FILE_NAME.equals(propertyName)) {
        PsiFile file = (PsiFile)event.getElement();
        String oldName = (String)event.getOldValue();
        releaseFile(file, oldName);
        VirtualFile vFile = file.getVirtualFile();
        if (myProjectFileIndex.isInContent(vFile)) {
          cacheFile(file);
        }
      }
      else if (propertyName.equals(PsiTreeChangeEvent.PROP_ROOTS)) {
        myInitialized = false;
        /*
        _fillCache();
        */
      }
      else if (propertyName.equals(PsiTreeChangeEvent.PROP_FILE_TYPES)) {
        /*
        _fillCache();
        */
        myInitialized = false;
      }
    }
  }
}
