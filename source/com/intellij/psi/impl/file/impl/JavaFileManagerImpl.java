/*
 * @author max
 */
package com.intellij.psi.impl.file.impl;

import com.intellij.AppTopics;
import com.intellij.ProjectTopics;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerConfiguration;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.RepositoryElementsManager;
import com.intellij.psi.impl.cache.RepositoryIndex;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Query;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class JavaFileManagerImpl implements JavaFileManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.impl.JavaFileManagerImpl");
  private final ConcurrentHashMap<GlobalSearchScope, PsiClass> myCachedObjectClassMap = new ConcurrentHashMap<GlobalSearchScope, PsiClass>();
  private final Map<String,PsiClass> myNameToClassMap = new ConcurrentHashMap<String, PsiClass>(); // used only in mode without repository
  @NonNls private static final String JAVA_EXTENSION = ".java";
  @NonNls private static final String CLASS_EXTENSION = ".class";
  private final PsiManagerEx myManager;
  private final ProjectRootManager myProjectRootManager;
  private final FileManager myFileManager;
  private final boolean myUseRepository;
  private Set<String> myNontrivialPackagePrefixes = null;
  private boolean myInitialized = false;
  private boolean myDisposed = false;


  public JavaFileManagerImpl(final PsiManagerEx manager, final ProjectRootManager projectRootManager, FileManager fileManager, MessageBus bus) {
    myManager = manager;
    myProjectRootManager = projectRootManager;
    myFileManager = fileManager;

    PsiManagerConfiguration configuration = PsiManagerConfiguration.getInstance();
    myUseRepository = configuration.REPOSITORY_ENABLED;

    myManager.registerRunnableToRunOnChange(new Runnable() {
      public void run() {
        myCachedObjectClassMap.clear();
      }
    });

    final MessageBusConnection connection = bus.connect();
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(final ModuleRootEvent event) {
      }

      public void rootsChanged(final ModuleRootEvent event) {
        myNontrivialPackagePrefixes = null;
        clearNonRepositoryMaps();
      }
    });

    connection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      public void before(final List<? extends VFileEvent> events) {
        clearNonRepositoryMaps();
      }

      public void after(final List<? extends VFileEvent> events) {
        clearNonRepositoryMaps();
      }
    });

    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
      public void fileWithNoDocumentChanged(final VirtualFile file) {
        clearNonRepositoryMaps();
      }
    });
  }

  public void initialize() {
    myInitialized = true;
  }

  public void dispose() {
    myDisposed = true;
    myCachedObjectClassMap.clear();
  }

  private void clearNonRepositoryMaps() {
    if (!myUseRepository) {
      myNameToClassMap.clear();
    }
  }

  @Nullable
  public PsiPackage findPackage(@NotNull String packageName) {
    Query<VirtualFile> dirs = PackageIndex.getInstance(myManager.getProject()).getDirsByPackageName(packageName, false);
    if (dirs.findFirst() == null) return null;
    return new PsiPackageImpl(myManager, packageName);
  }

  public PsiClass[] findClasses(@NotNull String qName, @NotNull GlobalSearchScope scope) {
    RepositoryManager repositoryManager = myManager.getRepositoryManager();
      long[] classIds = repositoryManager.getIndex().getClassesByQualifiedName(qName, null);
      if (classIds.length == 0) return PsiClass.EMPTY_ARRAY;

      ArrayList<PsiClass> result = new ArrayList<PsiClass>();
      for (long classId : classIds) {
        PsiClass aClass = (PsiClass)myManager.getRepositoryElementsManager().findOrCreatePsiElementById(classId);

        final String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName == null || !qualifiedName.equals(qName)) continue;

        VirtualFile vFile = aClass.getContainingFile().getVirtualFile();
        if (!fileIsInScope(scope, vFile)) continue;

        result.add(aClass);
      }
      return result.toArray(new PsiClass[result.size()]);
    }

  @Nullable
  public PsiClass findClass(@NotNull String qName, @NotNull GlobalSearchScope scope) {
    if (!myUseRepository) {
      return findClassWithoutRepository(qName);
    }

    if (!myInitialized) {
      LOG.error("Access to psi files should be performed only after startup activity");
      return null;
    }
    LOG.assertTrue(!myDisposed);

    if ("java.lang.Object".equals(qName)) { // optimization
      PsiClass cached = myCachedObjectClassMap.get(scope);
      if (cached == null) {
        cached = _findClass(qName, scope);
        if (cached != null) {
          cached = myCachedObjectClassMap.cacheOrGet(scope, cached);
        }
      }

      return cached;
    }

    return _findClass(qName, scope);
  }

  @Nullable
  private PsiClass findClassWithoutRepository(String qName) {
    PsiClass aClass = myNameToClassMap.get(qName);
    if (aClass != null) {
      return aClass;
    }

    aClass = _findClassWithoutRepository(qName);
    myNameToClassMap.put(qName, aClass);
    return aClass;
  }

  @Nullable
  private PsiClass _findClassWithoutRepository(String qName) {
    VirtualFile[] sourcePath = myProjectRootManager.getFilesFromAllModules(OrderRootType.SOURCES).clone();
    VirtualFile[] classPath = myProjectRootManager.getFilesFromAllModules(OrderRootType.CLASSES).clone();

    int index = 0;
    while (index < qName.length()) {
      int index1 = qName.indexOf('.', index);
      if (index1 < 0) {
        index1 = qName.length();
      }
      String name = qName.substring(index, index1);

      final int sourceType = 0;
      //final int compiledType = 1;

      for (int type = 0; type < 2; type++) {
        VirtualFile[] vDirs = type == sourceType ? sourcePath : classPath;
        for (VirtualFile vDir : vDirs) {
          if (vDir != null) {
            VirtualFile vChild = type == sourceType
                                 ? vDir.findChild(name + JAVA_EXTENSION)
                                 : vDir.findChild(name + CLASS_EXTENSION);
            if (vChild != null) {
              PsiFile file = myFileManager.findFile(vChild);
              if (file instanceof PsiJavaFile) {
                PsiClass aClass = findClassByName((PsiJavaFile)file, name);
                if (aClass != null) {
                  index = index1 + 1;
                  while (index < qName.length()) {
                    index1 = qName.indexOf('.', index);
                    if (index1 < 0) {
                      index1 = qName.length();
                    }
                    name = qName.substring(index, index1);
                    aClass = findClassByName(aClass, name);
                    if (aClass == null) return null;
                    index = index1 + 1;
                  }
                  return aClass;
                }
              }
            }
          }
        }
      }

      boolean existsDir = false;
      for (int type = 0; type < 2; type++) {
        VirtualFile[] vDirs = type == sourceType ? sourcePath : classPath;
        for (int i = 0; i < vDirs.length; i++) {
          if (vDirs[i] != null) {
            VirtualFile vDirChild = vDirs[i].findChild(name);
            if (vDirChild != null) {
              PsiDirectory dir = myFileManager.findDirectory(vDirChild);
              if (dir != null) {
                vDirs[i] = vDirChild;
                existsDir = true;
                continue;
              }
            }
            vDirs[i] = null;
          }
        }
      }
      if (!existsDir) return null;
      index = index1 + 1;
    }
    return null;
  }

  @Nullable
  private static PsiClass findClassByName(PsiJavaFile scope, String name) {
    PsiClass[] classes = scope.getClasses();
    for (PsiClass aClass : classes) {
      if (name.equals(aClass.getName())) {
        return aClass;
      }
    }
    return null;
  }

  @Nullable
  private static PsiClass findClassByName(PsiClass scope, String name) {
    PsiClass[] classes = scope.getInnerClasses();
    for (PsiClass aClass : classes) {
      if (name.equals(aClass.getName())) {
        return aClass;
      }
    }
    return null;
  }

  @Nullable
  private PsiClass _findClass(String qName, GlobalSearchScope scope) {
    RepositoryManager repositoryManager = myManager.getRepositoryManager();
    RepositoryIndex index = repositoryManager.getIndex();
    VirtualFileFilter rootFilter = null;//index.rootFilterBySearchScope(scope);
    long[] classIds = index.getClassesByQualifiedName(qName, rootFilter);
    if (classIds.length == 0) return null;

    RepositoryElementsManager repositoryElementsManager = myManager.getRepositoryElementsManager();
    VirtualFile bestFile = null;
    PsiClass bestClass = null;
    for (long classId : classIds) {
      PsiClass aClass = (PsiClass)repositoryElementsManager.findOrCreatePsiElementById(classId);
      LOG.assertTrue(aClass != null, "null class returned while looking for : " + qName);
      LOG.assertTrue(aClass.isValid(), "class is not valid while looking for : " + qName);
      if (!aClass.isValid()) continue;

      final String qualifiedName = aClass.getQualifiedName();
      if (qualifiedName == null || !qualifiedName.equals(qName)) continue;

      PsiFile file = aClass.getContainingFile();
      if (file == null) {
        LOG.error("aClass=" + aClass);
        continue;
      }
      VirtualFile vFile = file.getVirtualFile();
      if (!fileIsInScope(scope, vFile)) continue;
      if (bestFile == null || scope.compare(vFile, bestFile) > 0) {
        bestFile = vFile;
        bestClass = aClass;
      }
    }
    return bestClass;
  }


  private boolean fileIsInScope(final GlobalSearchScope scope, final VirtualFile vFile) {
    if (!scope.contains(vFile)) return false;

    if (vFile.getFileType() == StdFileTypes.CLASS) {
      // See IDEADEV-5626
      final VirtualFile root = ProjectRootManager.getInstance(myManager.getProject()).getFileIndex().getClassRootForFile(vFile);
      VirtualFile parent = vFile.getParent();
      final PsiNameHelper nameHelper = JavaPsiFacade.getInstance(myManager.getProject()).getNameHelper();
      while (parent != null && parent != root) {
        if (!nameHelper.isIdentifier(parent.getName())) return false;
        parent = parent.getParent();
      }
    }

    return true;
  }

  public Collection<String> getNonTrivialPackagePrefixes() {
    if (myNontrivialPackagePrefixes == null) {
      Set<String> names = new HashSet<String>();
      final ProjectRootManager rootManager = myProjectRootManager;
      final VirtualFile[] sourceRoots = rootManager.getContentSourceRoots();
      final ProjectFileIndex fileIndex = rootManager.getFileIndex();
      for (final VirtualFile sourceRoot : sourceRoots) {
        final String packageName = fileIndex.getPackageNameByDirectory(sourceRoot);
        if (packageName != null && packageName.length() > 0) {
          names.add(packageName);
        }
      }
      myNontrivialPackagePrefixes = names;
    }
    return myNontrivialPackagePrefixes;
  }  
}