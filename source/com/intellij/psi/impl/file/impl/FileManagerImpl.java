package com.intellij.psi.impl.file.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.cache.RepositoryIndex;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.file.PsiDirectoryImpl;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.WeakValueHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

public class FileManagerImpl implements FileManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.impl.FileManagerImpl");

  public static int MAX_INTELLISENSE_FILESIZE = maxIntellisenseFileSize();

  private final PsiManagerImpl myManager;

  private final FileTypeManager myFileTypeManager;

  private final ProjectRootManager myProjectRootManager;
  private ProjectFileIndex myProjectFileIndex = null;
  private RootManager myRootManager = null;

  private HashMap<VirtualFile, PsiDirectory> myVFileToPsiDirMap = new HashMap<VirtualFile, PsiDirectory>();
  private WeakValueHashMap<VirtualFile, FileViewProvider> myVFileToPsiFileMap = new WeakValueHashMap<VirtualFile, FileViewProvider>(); // VirtualFile --> PsiFile

  private VirtualFileListener myVirtualFileListener = null;
  private FileDocumentManagerListener myFileDocumentManagerListener = null;
  private ModuleRootListener myModuleRootListener = null;
  private FileTypeListener myFileTypeListener = null;
  private boolean myInitialized = false;
  private boolean myDisposed = false;
  private boolean myUseRepository = true;

  private HashMap<GlobalSearchScope, PsiClass> myCachedObjectClassMap = null;

  private Map<String,PsiClass> myNameToClassMap = new HashMap<String, PsiClass>(); // used only in mode without repository
  private Set<String> myNontrivialPackagePrefixes = null;
  private final VirtualFileManager myVirtualFileManager;
  private final FileDocumentManager myFileDocumentManager;
  private static final @NonNls String JAVA_EXTENSION = ".java";
  private static final @NonNls String CLASS_EXTENSION = ".class";
  @NonNls private static final String MAX_INTELLISENSE_SIZE_PROPERTY = "idea.max.intellisense.filesize";

  public FileManagerImpl(PsiManagerImpl manager,
                         FileTypeManager fileTypeManager,
                         VirtualFileManager virtualFileManager,
                         FileDocumentManager fileDocumentManager,
                         ProjectRootManager projectRootManager) {
    myFileTypeManager = fileTypeManager;
    myManager = manager;
    myVirtualFileManager = virtualFileManager;
    myFileDocumentManager = fileDocumentManager;
    myProjectRootManager = projectRootManager;
  }

  public void dispose() {
    if (myInitialized) {
      myVirtualFileManager.removeVirtualFileListener(myVirtualFileListener);
      myFileDocumentManager.removeFileDocumentManagerListener(myFileDocumentManagerListener);
      myProjectRootManager.removeModuleRootListener(myModuleRootListener);
      myFileTypeManager.removeFileTypeListener(myFileTypeListener);
      synchronized (PsiLock.LOCK) {
        myCachedObjectClassMap = null;
      }
    }
    myDisposed = true;
  }

  private static int maxIntellisenseFileSize() {
    final String maxSizeS = System.getProperty(MAX_INTELLISENSE_SIZE_PROPERTY);
    return maxSizeS != null ? Integer.parseInt(maxSizeS) * 1024 : -1;
  }

  public void cleanupForNextTest() {
    myVFileToPsiFileMap.clear();
    myVFileToPsiDirMap.clear();
  }

  public FileViewProvider findViewProvider(final VirtualFile file) {
    FileViewProvider viewProvider;
    synchronized (PsiLock.LOCK) {
      viewProvider = myVFileToPsiFileMap.get(file);
      if(viewProvider == null){
        viewProvider = createFileViewProvider(file);
        myVFileToPsiFileMap.put(file, viewProvider);
      }
    }
    return viewProvider;
  }

  public FileViewProvider findCachedViewProvider(final VirtualFile file) {
    return myVFileToPsiFileMap.get(file);
  }

  public void setViewProvider(final VirtualFile virtualFile, final FileViewProvider fileViewProvider) {
    myVFileToPsiFileMap.put(virtualFile, fileViewProvider);
  }

  private FileViewProvider createFileViewProvider(final VirtualFile file) {
    FileViewProvider viewProvider = null;
    final FileType fileType = file.getFileType();
    if (fileType instanceof LanguageFileType) {
      final LanguageFileType languageFileType = (LanguageFileType)fileType;
      viewProvider = languageFileType.getLanguage().createViewProvider(file, myManager, true);
    }
    if (viewProvider == null) viewProvider = new SingleRootFileViewProvider(myManager, file);
    return viewProvider;
  }

  public void runStartupActivity() {
    LOG.assertTrue(!myInitialized);
    myDisposed = false;
    myInitialized = true;

    myRootManager = new RootManager(this, myProjectRootManager);

    PsiManagerConfiguration configuration = PsiManagerConfiguration.getInstance();
    myUseRepository = configuration.REPOSITORY_ENABLED;

    myProjectFileIndex = myProjectRootManager.getFileIndex();

    Runnable runnable = new Runnable() {
      public void run() {
        synchronized (PsiLock.LOCK) {
          myCachedObjectClassMap = null;
        }
      }
    };
    myManager.registerRunnableToRunOnChange(runnable);

    myVirtualFileListener = new MyVirtualFileListener();
    myVirtualFileManager.addVirtualFileListener(myVirtualFileListener);

    myFileDocumentManagerListener = new MyFileDocumentManagerAdapter();
    myFileDocumentManager.addFileDocumentManagerListener(myFileDocumentManagerListener);

    myModuleRootListener = new MyModuleRootListener();
    myProjectRootManager.addModuleRootListener(myModuleRootListener);

    myFileTypeListener = new FileTypeListener() {
      public void beforeFileTypesChanged(FileTypeEvent event) {
      }

      public void fileTypesChanged(FileTypeEvent e) {
        ApplicationManager.getApplication().runWriteAction(
          new Runnable() {
            public void run() {
              PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
              event.setPropertyName(PsiTreeChangeEvent.PROP_FILE_TYPES);
              myManager.beforePropertyChange(event);

              removeInvalidFilesAndDirs(true);

              event = new PsiTreeChangeEventImpl(myManager);
              event.setPropertyName(PsiTreeChangeEvent.PROP_FILE_TYPES);
              myManager.propertyChanged(event);
            }
          }
        );
      }
    };
    myFileTypeManager.addFileTypeListener(myFileTypeListener);
  }

  private void dispatchPendingEvents() {
    LOG.assertTrue(myInitialized);
    //LOG.assertTrue(!myDisposed);

    // [dsl]todo[max, dsl] this is a hack. MUST FIX
    if (!ApplicationManager.getApplication().isDispatchThread()) return;

    myVirtualFileManager.dispatchPendingEvent(myVirtualFileListener);
    myFileDocumentManager.dispatchPendingEvents(myFileDocumentManagerListener);
    myProjectRootManager.dispatchPendingEvent(myModuleRootListener);
    ((FileTypeManagerEx) myFileTypeManager).dispatchPendingEvents(myFileTypeListener);
    //TODO: other listeners
  }

  // for tests
  public void checkConsistency() {
    Map<VirtualFile, FileViewProvider> fileToPsiFileMap = myVFileToPsiFileMap;
    myVFileToPsiFileMap = new WeakValueHashMap<VirtualFile, FileViewProvider>();
    for (VirtualFile vFile : fileToPsiFileMap.keySet()) {
      final FileViewProvider psiProvider = fileToPsiFileMap.get(vFile);

      LOG.assertTrue(vFile.isValid());
      PsiFile psiFile1 = findFile(vFile);
      //LOG.assertTrue(psiFile1 != null, vFile.toString());
      if (psiFile1 != null) { // might get collected
        LOG.assertTrue(psiFile1.getClass().equals(psiProvider.getPsi(psiProvider.getBaseLanguage()).getClass()));
      }

      VirtualFile parent = vFile.getParent();
      //LOG.assertTrue(myVFileToPsiDirMap.containsKey(parent));
    }

    HashMap<VirtualFile, PsiDirectory> fileToPsiDirMap = myVFileToPsiDirMap;
    myVFileToPsiDirMap = new HashMap<VirtualFile, PsiDirectory>();
    for (VirtualFile vFile : fileToPsiDirMap.keySet()) {
      LOG.assertTrue(vFile.isValid());
      PsiDirectory psiDir1 = findDirectory(vFile);
      LOG.assertTrue(psiDir1 != null);

      VirtualFile parent = vFile.getParent();
      if (parent != null) {
        LOG.assertTrue(myVFileToPsiDirMap.containsKey(parent));
      }
    }
  }

  public PsiFile findFile(VirtualFile vFile) {
    final ProjectEx project = (ProjectEx)myManager.getProject();
    if (project.isDummy() || project.isDefault()) return null;

    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (vFile == null) {
      LOG.assertTrue(false);
      return null;
    }
    if (!vFile.isValid()) {
      LOG.assertTrue(false, "Invalid file: " + vFile);
    }

    dispatchPendingEvents();
    final FileViewProvider viewProvider = findViewProvider(vFile);
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  public PsiFile getCachedPsiFile(VirtualFile vFile) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    LOG.assertTrue(vFile.isValid());
    LOG.assertTrue(!myDisposed);
    if (!myInitialized) return null;

    dispatchPendingEvents();

    synchronized (PsiLock.LOCK) {
      return getCachedPsiFileInner(vFile);
    }
  }

  public GlobalSearchScope getResolveScope(PsiElement element) {
    VirtualFile vFile;
    if (element instanceof PsiDirectory) {
      vFile = ((PsiDirectory)element).getVirtualFile();
    }
    else {
      final PsiFile contextFile = ResolveUtil.getContextFile(element);
      if (contextFile == null || contextFile instanceof XmlFile) {
        return GlobalSearchScope.allScope(myManager.getProject());
      }
      vFile = contextFile.getVirtualFile();
      if (vFile == null) {
        PsiFile originalFile = contextFile.getOriginalFile();
        if (originalFile != null) {
          vFile = originalFile.getVirtualFile();
        }
      }
    }
    if (vFile == null) {
      return GlobalSearchScope.allScope(myManager.getProject());
    }

    ProjectFileIndex projectFileIndex = myProjectRootManager.getFileIndex();
    Module module = projectFileIndex.getModuleForFile(vFile);
    if (module != null) {
      boolean includeTests = projectFileIndex.isInTestSourceContent(vFile) ||
                             !projectFileIndex.isContentJavaSourceFile(vFile);
      return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, includeTests);
    }
    else {
      // resolve references in libraries in context of all modules which contain it
      GlobalSearchScope allInclusiveModuleScope = null;

      List<OrderEntry> orderEntries = projectFileIndex.getOrderEntriesForFile(vFile);
      for (OrderEntry entry : orderEntries) {
        if (entry instanceof LibraryOrderEntry || entry instanceof JdkOrderEntry) {
          Module ownerModule = entry.getOwnerModule();
          final GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(ownerModule);
          if (allInclusiveModuleScope == null) {
            allInclusiveModuleScope = moduleScope;
          }
          else {
            allInclusiveModuleScope = allInclusiveModuleScope.uniteWith(moduleScope);
          }
        }
      }

      if (allInclusiveModuleScope == null) {
        allInclusiveModuleScope = GlobalSearchScope.allScope(myManager.getProject());
      }

      return new LibrariesOnlyScope(allInclusiveModuleScope);
    }
  }

  @NotNull
  public GlobalSearchScope getUseScope(PsiElement element) {
    VirtualFile vFile;
    if (element instanceof PsiDirectory) {
      vFile = ((PsiDirectory)element).getVirtualFile();
    }
    else {
      final PsiFile containingFile = element.getContainingFile();
      if (containingFile == null) return GlobalSearchScope.allScope(myManager.getProject());
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      if (virtualFile == null) return GlobalSearchScope.allScope(myManager.getProject());
      vFile = virtualFile.getParent();
    }

    if (vFile == null) return GlobalSearchScope.allScope(myManager.getProject());
    ProjectFileIndex projectFileIndex = myProjectRootManager.getFileIndex();
    Module module = projectFileIndex.getModuleForFile(vFile);
    if (module != null) {
      boolean isTest = projectFileIndex.isInTestSourceContent(vFile);
      return isTest
             ? GlobalSearchScope.moduleTestsWithDependentsScope(module)
             : GlobalSearchScope.moduleWithDependentsScope(module);
    }
    else {
      return GlobalSearchScope.allScope(myManager.getProject());
    }
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

  private PsiFile createFileCopyWithNewName(VirtualFile vFile, String name) {
    // TODO[ik] remove this. Event handling and generation must be in view providers mechanism since we
    // need to track changes in _all_ psi views (e.g. namespace changes in XML)
    final FileTypeManager instance = FileTypeManager.getInstance();
    if(instance.isFileIgnored(name)) return null;
    final FileType fileTypeByFileName = instance.getFileTypeByFileName(name);
    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    return ((PsiElementFactoryImpl)myManager.getElementFactory()).createFileFromText(name, fileTypeByFileName, document != null ? document.getCharsSequence() : "",
                                                                                     vFile.getModificationStamp(), true, false);
  }

  public PsiDirectory findDirectory(VirtualFile vFile) {
    LOG.assertTrue(myInitialized, "Access to psi files should be performed only after startup activity");
    LOG.assertTrue(!myDisposed, "Access to psi files should not be performed after disposal");

    ApplicationManager.getApplication().assertReadAccessAllowed();
    LOG.assertTrue(vFile.isValid(), "File is not valid:" + vFile.getName());

    if (!vFile.isDirectory()) return null;

    dispatchPendingEvents();

    synchronized (PsiLock.LOCK) {
      PsiDirectory psiDir = myVFileToPsiDirMap.get(vFile);
      if (psiDir != null) return psiDir;

      if (myProjectRootManager.getFileIndex().isIgnored(vFile)) return null;

      VirtualFile parent = vFile.getParent();
      if (parent != null) { //?
        findDirectory(parent);// need to cache parent directory - used for firing events
      }
      psiDir = new PsiDirectoryImpl(myManager, vFile);
      myVFileToPsiDirMap.put(vFile, psiDir);
      return psiDir;
    }
  }

  public PsiPackage findPackage(String packageName) {
    VirtualFile[] dirs = myProjectRootManager.getFileIndex().getDirectoriesByPackageName(packageName, false);
    if (dirs.length == 0) return null;
    return new PsiPackageImpl(myManager, packageName);
  }

  public PsiDirectory[] getRootDirectories(int rootType) {
    return myRootManager.getRootDirectories(rootType);
  }

  public PsiClass[] findClasses(String qName, GlobalSearchScope scope) {
      RepositoryManager repositoryManager = myManager.getRepositoryManager();
      long[] classIds = repositoryManager.getIndex().getClassesByQualifiedName(qName, null);
      if (classIds.length == 0) return PsiClass.EMPTY_ARRAY;

      ArrayList<PsiClass> result = new ArrayList<PsiClass>();
      for (long classId : classIds) {
        PsiClass aClass = (PsiClass)myManager.getRepositoryElementsManager().findOrCreatePsiElementById(classId);
        VirtualFile vFile = aClass.getContainingFile().getVirtualFile();
        if (fileIsInScope(scope, vFile)) {
          result.add(aClass);
        }
      }
      return result.toArray(new PsiClass[result.size()]);
    }

  public PsiClass findClass(String qName, GlobalSearchScope scope) {
    if (!myUseRepository) {
      return findClassWithoutRepository(qName);
    }

    if (!myInitialized) {
      LOG.error("Access to psi files should be performed only after startup activity");
      return null;
    }
    LOG.assertTrue(!myDisposed);

    if ("java.lang.Object".equals(qName)) { // optimization
    synchronized (PsiLock.LOCK) {
        if (myCachedObjectClassMap == null) {
          myCachedObjectClassMap = new HashMap<GlobalSearchScope, PsiClass>();

          Module[] modules = ModuleManager.getInstance(myManager.getProject()).getModules();
          for (Module aModule : modules) {
            GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(aModule);
            PsiClass objectClass = _findClass(qName, moduleScope);
            myCachedObjectClassMap.put(moduleScope, objectClass);
          }

          GlobalSearchScope allScope = GlobalSearchScope.allScope(myManager.getProject());
          PsiClass objectClass = _findClass(qName, allScope);
          myCachedObjectClassMap.put(allScope, objectClass);
        }
        final PsiClass cachedClass = myCachedObjectClassMap.get(scope);
        return cachedClass == null ? _findClass(qName, scope) : cachedClass;
      }
    }

      return _findClass(qName, scope);
    }

  private PsiClass findClassWithoutRepository(String qName) {
    synchronized (PsiLock.LOCK) {
      if (myNameToClassMap.containsKey(qName)) return myNameToClassMap.get(qName);

      PsiClass aClass = _findClassWithoutRepository(qName);
      myNameToClassMap.put(qName, aClass);
      return aClass;
    }
  }

  private PsiClass _findClassWithoutRepository(String qName) {
    PsiClass aClass = myNameToClassMap.get(qName);
    if (aClass != null) return aClass;

    VirtualFile[] sourcePath = myRootManager.getSourceRootsCopy();
    VirtualFile[] classPath = myRootManager.getClassRootsCopy();

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
              PsiFile file = findFile(vChild);
              if (file instanceof PsiJavaFile) {
                aClass = findClassByName((PsiJavaFile)file, name);
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
              PsiDirectory dir = findDirectory(vDirChild);
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

  private static PsiClass findClassByName(PsiJavaFile scope, String name) {
    PsiClass[] classes = scope.getClasses();
    for (PsiClass aClass : classes) {
      if (name.equals(aClass.getName())) {
        return aClass;
      }
    }
    return null;
  }

  private static PsiClass findClassByName(PsiClass scope, String name) {
    PsiClass[] classes = scope.getInnerClasses();
    for (PsiClass aClass : classes) {
      if (name.equals(aClass.getName())) {
        return aClass;
      }
    }
    return null;
  }

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
      LOG.assertTrue(aClass != null);
      LOG.assertTrue(aClass.isValid());
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
      final PsiNameHelper nameHelper = myManager.getNameHelper();
      while (parent != null && parent != root) {
        if (!nameHelper.isIdentifier(parent.getName())) return false;
        parent = parent.getParent();
      }
    }

    return true;
  }

  public PsiFile getCachedPsiFileInner(VirtualFile file) {
    final FileViewProvider fileViewProvider = myVFileToPsiFileMap.get(file);
    return fileViewProvider != null ? ((SingleRootFileViewProvider)fileViewProvider).getCachedPsi(fileViewProvider.getBaseLanguage()) : null;
  }

  private void removeInvalidFilesAndDirs(boolean useFind) {
    HashMap<VirtualFile, PsiDirectory> fileToPsiDirMap = myVFileToPsiDirMap;
    if (useFind) {
      myVFileToPsiDirMap = new HashMap<VirtualFile, PsiDirectory>();
    }
    for (Iterator<VirtualFile> iterator = fileToPsiDirMap.keySet().iterator(); iterator.hasNext();) {
      VirtualFile vFile = iterator.next();
      if (!vFile.isValid()) {
        iterator.remove();
      } else {
        PsiDirectory psiDir = findDirectory(vFile);
        if (psiDir == null) {
          iterator.remove();
        }
      }
    }
    myVFileToPsiDirMap = fileToPsiDirMap;

    // note: important to update directories map first - findFile uses findDirectory!
    WeakValueHashMap<VirtualFile, FileViewProvider> fileToPsiFileMap = myVFileToPsiFileMap;
    if (useFind) {
      myVFileToPsiFileMap = new WeakValueHashMap<VirtualFile, FileViewProvider>();
    }
    for (Iterator<VirtualFile> iterator = fileToPsiFileMap.keySet().iterator(); iterator.hasNext();) {
      VirtualFile vFile = iterator.next();

      if (!vFile.isValid()) {
        iterator.remove();
        continue;
      }

      if (useFind) {
        FileViewProvider view = fileToPsiFileMap.get(vFile);
        if (view == null) { // soft ref. collected
          iterator.remove();
          continue;
        }
        PsiFile psiFile1 = findFile(vFile);
        if (psiFile1 == null) {
          iterator.remove();
          continue;
        }

        if (!psiFile1.getClass().equals(view.getPsi(view.getBaseLanguage()).getClass()))
          iterator.remove();
      }
    }
    myVFileToPsiFileMap = fileToPsiFileMap;
  }

  public void reloadFromDisk(PsiFile file) {
    reloadFromDisk(file, false);
  }

  private void reloadFromDisk(PsiFile file, boolean ignoreDocument) {
    VirtualFile vFile = file.getVirtualFile();
    assert vFile != null;

    if (!(file instanceof PsiBinaryFile)) {
      FileDocumentManager fileDocumentManager = myFileDocumentManager;
      Document document = fileDocumentManager.getCachedDocument(vFile);
      if (document != null && !ignoreDocument){
        fileDocumentManager.reloadFromDisk(document);
      }
      else{
        PsiTreeChangeEventImpl event = new PsiTreeChangeEventImpl(myManager);
        event.setParent(file);
        event.setFile(file);
        if (file instanceof PsiFileImpl && ((PsiFileImpl)file).isContentsLoaded()) {
          event.setOffset(0);
          event.setOldLength(file.getTextLength());
        }
        myManager.beforeChildrenChange(event);

        if (file instanceof PsiFileImpl) {
          PsiFileImpl fileImpl = (PsiFileImpl)file;
          fileImpl.subtreeChanged(); // important! otherwise cached information is not released
          if (fileImpl.isContentsLoaded()) {
            ((PsiFileImpl)file).unloadContent();
          }
        }
        else if (file instanceof ClsFileImpl) {
          if (((ClsFileImpl)file).isContentsLoaded()) {
            ((ClsFileImpl)file).unloadContent();
          }
        }

        myManager.childrenChanged(event);
      }
    }
  }

  private void clearNonRepositoryMaps() {
    myNameToClassMap.clear();
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {
    public void contentsChanged(final VirtualFileEvent event) {
      // handled by FileDocumentManagerListener
    }

    public void fileCreated(VirtualFileEvent event) {
      if (!myUseRepository) {
        clearNonRepositoryMaps();
      }

      final VirtualFile vFile = event.getFile();

      ApplicationManager.getApplication().runWriteAction(
        new PsiExternalChangeAction() {
          public void run() {
            PsiDirectory parentDir = myVFileToPsiDirMap.get(vFile.getParent());
            if (parentDir == null) return; // do not notifyListeners event if parent directory was never accessed via PSI

            if (!vFile.isDirectory()) {
              PsiFile psiFile = findFile(vFile);
              if (psiFile != null) {
                PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
                treeEvent.setParent(parentDir);
                myManager.beforeChildAddition(treeEvent);
                treeEvent.setChild(psiFile);
                myManager.childAdded(treeEvent);
              }
            }
            else {
              PsiDirectory psiDir = findDirectory(vFile);
              if (psiDir != null) {
                PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
                treeEvent.setParent(parentDir);
                myManager.beforeChildAddition(treeEvent);
                treeEvent.setChild(psiDir);
                myManager.childAdded(treeEvent);
              }
            }
          }
        }
      );
    }

    public void beforeFileDeletion(VirtualFileEvent event) {
      if (!myUseRepository) {
        clearNonRepositoryMaps();
      }

      final VirtualFile vFile = event.getFile();

      final PsiDirectory parentDir = myVFileToPsiDirMap.get(vFile.getParent());
      if (parentDir == null) return; // do not notify listeners if parent directory was never accessed via PSI

      ApplicationManager.getApplication().runWriteAction(
        new PsiExternalChangeAction() {
          public void run() {
            if (!vFile.isDirectory()) {
              PsiFile psiFile = findFile(vFile);
              if (psiFile != null) {
                PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
                treeEvent.setParent(parentDir);
                treeEvent.setChild(psiFile);
                myManager.beforeChildRemoval(treeEvent);
              }
            }
            else {
              PsiDirectory psiDir = findDirectory(vFile);
              if (psiDir != null) {
                PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
                treeEvent.setParent(parentDir);
                treeEvent.setChild(psiDir);
                myManager.beforeChildRemoval(treeEvent);
              }
            }
          }
        }
      );
    }

    public void fileDeleted(final VirtualFileEvent event) {
      if (!myUseRepository) {
        clearNonRepositoryMaps();
      }

      final VirtualFile vFile = event.getFile();

      final PsiDirectory parentDir = myVFileToPsiDirMap.get(event.getParent());

      if (!event.isDirectory()) {
        final PsiFile psiFile = getCachedPsiFileInner(vFile);
        if (psiFile != null) {
          myVFileToPsiFileMap.remove(vFile);

          if (parentDir != null) {
            ApplicationManager.getApplication().runWriteAction(
              new PsiExternalChangeAction() {
                public void run() {
                  PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
                  treeEvent.setParent(parentDir);
                  treeEvent.setChild(psiFile);
                  myManager.childRemoved(treeEvent);
                }
              }
            );
          }
        }
      }
      else {
        final PsiDirectory psiDir = myVFileToPsiDirMap.get(vFile);
        if (psiDir != null) {
          removeInvalidFilesAndDirs(false);

          if (parentDir != null) {
            ApplicationManager.getApplication().runWriteAction(
              new PsiExternalChangeAction() {
                public void run() {
                  PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
                  treeEvent.setParent(parentDir);
                  treeEvent.setChild(psiDir);
                  myManager.childRemoved(treeEvent);
                }
              }
            );
          }
        }
      }
    }

    public void beforePropertyChange(final VirtualFilePropertyEvent event) {
      if (!myUseRepository) {
        clearNonRepositoryMaps();
      }

      final VirtualFile vFile = event.getFile();
      final String propertyName = event.getPropertyName();

      final PsiDirectory parentDir = myVFileToPsiDirMap.get(vFile.getParent());
      if (parentDir == null) return; // do not notifyListeners event if parent directory was never accessed via PSI

      ApplicationManager.getApplication().runWriteAction(
        new PsiExternalChangeAction() {
          public void run() {
            PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
            treeEvent.setParent(parentDir);

            if (VirtualFile.PROP_NAME.equals(propertyName)) {
              final String newName = (String)event.getNewValue();

              if (vFile.isDirectory()) {
                PsiDirectory psiDir = findDirectory(vFile);
                if (psiDir != null) {
                  if (!myFileTypeManager.isFileIgnored(newName)) {
                    treeEvent.setChild(psiDir);
                    treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_DIRECTORY_NAME);
                    treeEvent.setOldValue(vFile.getName());
                    treeEvent.setNewValue(newName);
                    myManager.beforePropertyChange(treeEvent);
                  }
                  else {
                    treeEvent.setChild(psiDir);
                    myManager.beforeChildRemoval(treeEvent);
                  }
                }
                else {
                  if (!isExcludeRoot(vFile) && !myFileTypeManager.isFileIgnored(newName)) {
                    myManager.beforeChildAddition(treeEvent);
                  }
                }
              }
              else {
                final FileViewProvider viewProvider = findViewProvider(vFile);
                PsiFile psiFile = viewProvider.getPsi(viewProvider.getBaseLanguage());
                PsiFile psiFile1 = createFileCopyWithNewName(vFile, newName);

                if (psiFile != null) {
                  if (psiFile1 == null) {
                    treeEvent.setChild(psiFile);
                    myManager.beforeChildRemoval(treeEvent);
                  }
                  else if (!psiFile1.getClass().equals(psiFile.getClass())) {
                    treeEvent.setOldChild(psiFile);
                    myManager.beforeChildReplacement(treeEvent);
                  }
                  else {
                    treeEvent.setChild(psiFile);
                    treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_FILE_NAME);
                    treeEvent.setOldValue(vFile.getName());
                    treeEvent.setNewValue(newName);
                    myManager.beforePropertyChange(treeEvent);
                  }
                }
                else {
                  if (psiFile1 != null) {
                    myManager.beforeChildAddition(treeEvent);
                  }
                }
              }
            }
            else if (VirtualFile.PROP_WRITABLE.equals(propertyName)) {
              PsiFile psiFile = getCachedPsiFileInner(vFile);
              if (psiFile == null) return;

              treeEvent.setElement(psiFile);
              treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_WRITABLE);
              treeEvent.setOldValue(event.getOldValue());
              treeEvent.setNewValue(event.getNewValue());
              myManager.beforePropertyChange(treeEvent);
            }
          }
        }
      );
    }

    private boolean isExcludeRoot(VirtualFile file) {
      VirtualFile parent = file.getParent();
      Module module = myProjectRootManager.getFileIndex().getModuleForFile(parent);
      if (module == null) return false;
      VirtualFile[] excludeRoots = ModuleRootManager.getInstance(module).getExcludeRoots();
      for (VirtualFile root : excludeRoots) {
        if (root.equals(file)) return true;
      }
      return false;
    }

    public void propertyChanged(final VirtualFilePropertyEvent event) {
      if (!myUseRepository) {
        clearNonRepositoryMaps();
      }

      final String propertyName = event.getPropertyName();
      final VirtualFile vFile = event.getFile();

      final FileViewProvider fileViewProvider = findViewProvider(vFile);
      final PsiFile oldPsiFile;
      if (fileViewProvider != null) {
        oldPsiFile = ((SingleRootFileViewProvider)fileViewProvider).getCachedPsi(fileViewProvider.getBaseLanguage());
      }
      else oldPsiFile = null;

      final PsiDirectory parentDir = myVFileToPsiDirMap.get(vFile.getParent());
      if (parentDir == null) {
        boolean fire = VirtualFile.PROP_NAME.equals(propertyName) &&
                       vFile.isDirectory();
        if (fire) {
          PsiDirectory psiDir = myVFileToPsiDirMap.get(vFile);
          fire = psiDir != null;
        }
        if (!fire) return; // do not fire event if parent directory was never accessed via PSI
      }

      ApplicationManager.getApplication().runWriteAction(
        new PsiExternalChangeAction() {
          public void run() {
            PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
            treeEvent.setParent(parentDir);

            if (VirtualFile.PROP_NAME.equals(propertyName)) {
              if (vFile.isDirectory()) {
                PsiDirectory psiDir = myVFileToPsiDirMap.get(vFile);
                if (psiDir != null) {
                  if (myFileTypeManager.isFileIgnored(vFile.getName())) {
                    removeFilesAndDirsRecursively(vFile);

                    treeEvent.setChild(psiDir);
                    myManager.childRemoved(treeEvent);
                  }
                  else {
                    treeEvent.setElement(psiDir);
                    treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_DIRECTORY_NAME);
                    treeEvent.setOldValue(event.getOldValue());
                    treeEvent.setNewValue(event.getNewValue());
                    myManager.propertyChanged(treeEvent);
                  }
                }
                else {
                  PsiDirectory psiDir1 = findDirectory(vFile);
                  if (psiDir1 != null) {
                    treeEvent.setChild(psiDir1);
                    myManager.childAdded(treeEvent);
                  }
                }
              }
              else if (fileViewProvider != null){
                final FileViewProvider fileViewProvider = createFileViewProvider(vFile);
                final PsiFile newPsiFile = fileViewProvider.getPsi(fileViewProvider.getBaseLanguage());
                if(oldPsiFile != null) {
                  if (newPsiFile == null) {
                    myVFileToPsiFileMap.remove(vFile);

                    treeEvent.setChild(oldPsiFile);
                    myManager.childRemoved(treeEvent);
                  }
                  else if (!newPsiFile.getClass().equals(oldPsiFile.getClass()) ||
                           newPsiFile.getFileType() != myFileTypeManager.getFileTypeByFileName((String)event.getOldValue())
                          ) {
                    myVFileToPsiFileMap.put(vFile, fileViewProvider);

                    treeEvent.setOldChild(oldPsiFile);
                    treeEvent.setNewChild(newPsiFile);
                    myManager.childReplaced(treeEvent);
                  }
                  else {
                    treeEvent.setElement(oldPsiFile);
                    treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_FILE_NAME);
                    treeEvent.setOldValue(event.getOldValue());
                    treeEvent.setNewValue(event.getNewValue());
                    myManager.propertyChanged(treeEvent);
                  }
                }
                else {
                  if (newPsiFile != null) {
                    myVFileToPsiFileMap.put(vFile, fileViewProvider);
                    treeEvent.setChild(newPsiFile);
                    myManager.childAdded(treeEvent);
                  }
                }
              }
            }
            else if (VirtualFile.PROP_WRITABLE.equals(propertyName)) {
              if (oldPsiFile == null) return;

              treeEvent.setElement(oldPsiFile);
              treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_WRITABLE);
              treeEvent.setOldValue(event.getOldValue());
              treeEvent.setNewValue(event.getNewValue());
              myManager.propertyChanged(treeEvent);
            }
          }
        }
      );
    }

    public void beforeFileMovement(VirtualFileMoveEvent event) {
      final VirtualFile vFile = event.getFile();

      final PsiDirectory oldParentDir = findDirectory(event.getOldParent());
      final PsiDirectory newParentDir = findDirectory(event.getNewParent());
      if (oldParentDir == null && newParentDir == null) return;
      if (myFileTypeManager.isFileIgnored(vFile.getName())) return;

      ApplicationManager.getApplication().runWriteAction(
        new PsiExternalChangeAction() {
          public void run() {
            PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);

            boolean isExcluded = vFile.isDirectory() && myProjectFileIndex.isIgnored(vFile);
            if (oldParentDir != null && !isExcluded) {
              if (newParentDir != null) {
                treeEvent.setOldParent(oldParentDir);
                treeEvent.setNewParent(newParentDir);
                if (vFile.isDirectory()) {
                  PsiDirectory psiDir = findDirectory(vFile);
                  treeEvent.setChild(psiDir);
                }
                else {
                  PsiFile psiFile = findFile(vFile);
                  treeEvent.setChild(psiFile);
                }
                myManager.beforeChildMovement(treeEvent);
              }
              else {
                treeEvent.setParent(oldParentDir);
                if (vFile.isDirectory()) {
                  PsiDirectory psiDir = findDirectory(vFile);
                  treeEvent.setChild(psiDir);
                }
                else {
                  PsiFile psiFile = findFile(vFile);
                  treeEvent.setChild(psiFile);
                }
                myManager.beforeChildRemoval(treeEvent);
              }
            }
            else {
              LOG.assertTrue(newParentDir != null); // checked above
              treeEvent.setParent(newParentDir);
              myManager.beforeChildAddition(treeEvent);
            }
          }
        }
      );
    }

    public void fileMoved(VirtualFileMoveEvent event) {
      if (!myUseRepository) {
        clearNonRepositoryMaps();
      }

      final VirtualFile vFile = event.getFile();

      final PsiDirectory oldParentDir = findDirectory(event.getOldParent());
      final PsiDirectory newParentDir = findDirectory(event.getNewParent());
      if (oldParentDir == null && newParentDir == null) return;

      final PsiElement oldElement = vFile.isDirectory() ? myVFileToPsiDirMap.get(vFile) : getCachedPsiFileInner(vFile);
      removeInvalidFilesAndDirs(true);
      final FileViewProvider viewProvider = findViewProvider(vFile);
      final PsiElement newElement;
      final FileViewProvider newViewProvider;
      if (!vFile.isDirectory()){
        newViewProvider = createFileViewProvider(vFile);
        newElement = newViewProvider.getPsi(viewProvider.getBaseLanguage());
      }
      else {
        newElement = findDirectory(vFile);
        newViewProvider = null;
      }

      if (oldElement == null && newElement == null) return;

      ApplicationManager.getApplication().runWriteAction(
        new PsiExternalChangeAction() {
          public void run() {
            PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
            if (oldElement != null) {
              if (newElement != null) {
                if (!oldElement.getClass().equals(oldElement.getClass())) {
                  myVFileToPsiFileMap.put(vFile, newViewProvider);
                  PsiTreeChangeEventImpl treeRemoveEvent = new PsiTreeChangeEventImpl(myManager);
                  treeRemoveEvent.setParent(oldParentDir);
                  treeRemoveEvent.setChild(oldElement);
                  myManager.childRemoved(treeRemoveEvent);
                  PsiTreeChangeEventImpl treeAddEvent = new PsiTreeChangeEventImpl(myManager);
                  treeAddEvent.setParent(newParentDir);
                  treeAddEvent.setChild(newElement);
                  myManager.childAdded(treeAddEvent);
                }
                else {
                  treeEvent.setOldParent(oldParentDir);
                  treeEvent.setNewParent(newParentDir);
                  treeEvent.setChild(newElement);
                  myManager.childMoved(treeEvent);
                }
              }
              else {
                myVFileToPsiFileMap.remove(vFile);
                treeEvent.setParent(oldParentDir);
                treeEvent.setChild(oldElement);
                myManager.childRemoved(treeEvent);
              }
            }
            else {
              myVFileToPsiFileMap.put(vFile, newViewProvider);
              LOG.assertTrue(newElement != null); // checked above
              treeEvent.setParent(newParentDir);
              treeEvent.setChild(newElement);
              myManager.childAdded(treeEvent);
            }
          }
        }
      );
    }

    private void removeFilesAndDirsRecursively(VirtualFile vFile) {
      if (vFile.isDirectory()) {
        myVFileToPsiDirMap.remove(vFile);

        VirtualFile[] children = vFile.getChildren();
        for (VirtualFile child : children) {
          removeFilesAndDirsRecursively(child);
        }
      }
      else myVFileToPsiFileMap.remove(vFile);
    }
  }

  private class MyModuleRootListener implements ModuleRootListener {
    private VirtualFile[] myOldContentRoots = null;
    public void beforeRootsChange(final ModuleRootEvent event) {
      if (!myInitialized) return;
      if (event.isCausedByFileTypesChange()) return;
      ApplicationManager.getApplication().runWriteAction(
        new PsiExternalChangeAction() {
          public void run() {
            PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
            treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_ROOTS);
            final VirtualFile[] contentRoots = myProjectRootManager.getContentRoots();
            LOG.assertTrue(myOldContentRoots == null);
            myOldContentRoots = contentRoots;
            treeEvent.setOldValue(contentRoots);
            myManager.beforePropertyChange(treeEvent);
          }
        }
      );
    }

    public void rootsChanged(final ModuleRootEvent event) {
      dispatchPendingEvents();
      myNontrivialPackagePrefixes = null;

      if (!myInitialized) return;
      if (!myUseRepository) {
        clearNonRepositoryMaps();
      }
      if (event.isCausedByFileTypesChange()) return;
      ApplicationManager.getApplication().runWriteAction(
        new PsiExternalChangeAction() {
          public void run() {
            RepositoryManager repositoryManager = myManager.getRepositoryManager();

            removeInvalidFilesAndDirs(true);

            PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
            treeEvent.setPropertyName(PsiTreeChangeEvent.PROP_ROOTS);
            final VirtualFile[] contentRoots = myProjectRootManager.getContentRoots();
            treeEvent.setNewValue(contentRoots);
            LOG.assertTrue(myOldContentRoots != null);
            treeEvent.setOldValue(myOldContentRoots);
            myOldContentRoots = null;
            myManager.propertyChanged(treeEvent);
          }
        }
      );
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void dumpFilesWithContentLoaded(Writer out) throws IOException {
    out.write("Files with content loaded cached in FileManagerImpl:\n");
    Set<VirtualFile> vFiles = myVFileToPsiFileMap.keySet();
    for (VirtualFile fileCacheEntry : vFiles) {
      final FileViewProvider view = myVFileToPsiFileMap.get(fileCacheEntry);
      PsiFile psiFile = view.getPsi(view.getBaseLanguage());
      if (psiFile instanceof PsiFileImpl && ((PsiFileImpl)psiFile).isContentsLoaded()) {
        out.write(fileCacheEntry.getPresentableUrl());
        out.write("\n");
      }
    }
  }

  private static class LibrariesOnlyScope extends GlobalSearchScope {
    private final GlobalSearchScope myOriginal;

    public LibrariesOnlyScope(final GlobalSearchScope original) {
      myOriginal = original;
    }

    public boolean contains(VirtualFile file) {
      return myOriginal.contains(file);
    }

    public int compare(VirtualFile file1, VirtualFile file2) {
      return myOriginal.compare(file1, file2);
    }

    public boolean isSearchInModuleContent(Module aModule) {
      return false;
    }

    public boolean isSearchInLibraries() {
      return true;
    }
  }

  private class MyFileDocumentManagerAdapter extends FileDocumentManagerAdapter {
    public void fileWithNoDocumentChanged(VirtualFile file) {
      if (!myUseRepository) {
        clearNonRepositoryMaps();
      }

      final PsiFile psiFile = getCachedPsiFileInner(file);
      if (psiFile != null) {
        ApplicationManager.getApplication().runWriteAction(
          new PsiExternalChangeAction() {
            public void run() {
              reloadFromDisk(psiFile, true); // important to ignore document which might appear already!
            }
          }
        );
      }
    }
  }
}
