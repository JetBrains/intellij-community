package com.intellij.psi.impl.file.impl;

import com.intellij.AppTopics;
import com.intellij.ProjectTopics;
import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerConfiguration;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiTreeChangeEventImpl;
import com.intellij.psi.impl.RepositoryElementsManager;
import com.intellij.psi.impl.cache.RepositoryIndex;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.compiled.ClsFileImpl;
import com.intellij.psi.impl.file.PsiDirectoryImpl;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Query;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ConcurrentWeakValueHashMap;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class FileManagerImpl implements FileManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.impl.FileManagerImpl");

  public static int MAX_INTELLISENSE_FILESIZE = maxIntellisenseFileSize();

  private final PsiManagerImpl myManager;

  private final FileTypeManager myFileTypeManager;

  private final ProjectRootManager myProjectRootManager;
  private ProjectFileIndex myProjectFileIndex = null;
  private RootManager myRootManager = null;

  private final ConcurrentHashMap<VirtualFile, PsiDirectory> myVFileToPsiDirMap = new ConcurrentHashMap<VirtualFile, PsiDirectory>();
  private final ConcurrentMap<VirtualFile, FileViewProvider> myVFileToViewProviderMap = new ConcurrentWeakValueHashMap<VirtualFile, FileViewProvider>();

  private VirtualFileListener myVirtualFileListener = null;
  private boolean myInitialized = false;
  private boolean myDisposed = false;
  private boolean myUseRepository = true;

  private final ConcurrentHashMap<GlobalSearchScope, PsiClass> myCachedObjectClassMap = new ConcurrentHashMap<GlobalSearchScope, PsiClass>();

  private final Map<String,PsiClass> myNameToClassMap = new ConcurrentHashMap<String, PsiClass>(); // used only in mode without repository
  private Set<String> myNontrivialPackagePrefixes = null;
  private final VirtualFileManager myVirtualFileManager;
  private final FileDocumentManager myFileDocumentManager;
  private MessageBusConnection myConnection;

  @NonNls private static final String JAVA_EXTENSION = ".java";
  @NonNls private static final String CLASS_EXTENSION = ".class";
  @NonNls private static final String MAX_INTELLISENSE_SIZE_PROPERTY = "idea.max.intellisense.filesize";

  public FileManagerImpl(PsiManagerImpl manager,
                         FileTypeManager fileTypeManager,
                         VirtualFileManager virtualFileManager,
                         FileDocumentManager fileDocumentManager,
                         ProjectRootManager projectRootManager) {
    myFileTypeManager = fileTypeManager;
    myManager = manager;
    myConnection = manager.getProject().getMessageBus().connect();

    myVirtualFileManager = virtualFileManager;
    myFileDocumentManager = fileDocumentManager;
    myProjectRootManager = projectRootManager;
  }

  public void dispose() {
    if (myInitialized) {
      myConnection.disconnect();

      myVirtualFileManager.removeVirtualFileListener(myVirtualFileListener);
      myCachedObjectClassMap.clear();
    }
    myDisposed = true;
  }

  private static int maxIntellisenseFileSize() {
    final String maxSizeS = System.getProperty(MAX_INTELLISENSE_SIZE_PROPERTY);
    return maxSizeS != null ? Integer.parseInt(maxSizeS) * 1024 : -1;
  }

  public void cleanupForNextTest() {
    myVFileToViewProviderMap.clear();
    myVFileToPsiDirMap.clear();
  }

  public FileViewProvider findViewProvider(final VirtualFile file) {
    FileViewProvider viewProvider = myVFileToViewProviderMap.get(file);
    if(viewProvider == null) {
      viewProvider = ConcurrencyUtil.cacheOrGet(myVFileToViewProviderMap, file, createFileViewProvider(file));
    }
    return viewProvider;
  }

  public FileViewProvider findCachedViewProvider(final VirtualFile file) {
    return myVFileToViewProviderMap.get(file);
  }

  public void setViewProvider(final VirtualFile virtualFile, final FileViewProvider fileViewProvider) {
    if (fileViewProvider == null) {
      myVFileToViewProviderMap.remove(virtualFile);
    }
    else {
      myVFileToViewProviderMap.put(virtualFile, fileViewProvider);
    }
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
        myCachedObjectClassMap.clear();
      }
    };
    myManager.registerRunnableToRunOnChange(runnable);

    myVirtualFileListener = new MyVirtualFileListener();
    myVirtualFileManager.addVirtualFileListener(myVirtualFileListener);

    myConnection.subscribe(AppTopics.FILE_TYPES, new FileTypeListener() {
      public void beforeFileTypesChanged(FileTypeEvent event) {}

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
    });

    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new MyModuleRootListener());
    myConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new MyFileDocumentManagerAdapter());
  }

  private void dispatchPendingEvents() {
    if (!myInitialized) {
      LOG.error("Project is already disposed");
    }

    //LOG.assertTrue(!myDisposed);

    // [dsl]todo[max, dsl] this is a hack. MUST FIX
    if (!ApplicationManager.getApplication().isDispatchThread()) return;

    myVirtualFileManager.dispatchPendingEvent(myVirtualFileListener);

    myConnection.deliverImmediately();
    //TODO: other listeners
  }

  // for tests
  public void checkConsistency() {
    ConcurrentWeakValueHashMap<VirtualFile, FileViewProvider> fileToViewProvider = new ConcurrentWeakValueHashMap<VirtualFile, FileViewProvider>(myVFileToViewProviderMap);
    myVFileToViewProviderMap.clear();
    for (VirtualFile vFile : fileToViewProvider.keySet()) {
      final FileViewProvider fileViewProvider = fileToViewProvider.get(vFile);

      LOG.assertTrue(vFile.isValid());
      PsiFile psiFile1 = findFile(vFile);
      if (psiFile1 != null && fileViewProvider != null) { // might get collected
        LOG.assertTrue(psiFile1.getClass().equals(fileViewProvider.getPsi(fileViewProvider.getBaseLanguage()).getClass()));
      }
    }

    ConcurrentHashMap<VirtualFile, PsiDirectory> fileToPsiDirMap = new ConcurrentHashMap<VirtualFile, PsiDirectory>((Map<? extends VirtualFile,? extends PsiDirectory>)myVFileToPsiDirMap);
    myVFileToPsiDirMap.clear();

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

  @Nullable
  public PsiFile findFile(@NotNull VirtualFile vFile) {
    if (vFile.isDirectory()) return null;
    final ProjectEx project = (ProjectEx)myManager.getProject();
    if (project.isDummy() || project.isDefault()) return null;

    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!vFile.isValid()) {
      LOG.assertTrue(false, "Invalid file: " + vFile);
      return null;
    }

    dispatchPendingEvents();
    final FileViewProvider viewProvider = findViewProvider(vFile);
    return viewProvider.getPsi(viewProvider.getBaseLanguage());
  }

  @Nullable
  public PsiFile getCachedPsiFile(@NotNull VirtualFile vFile) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    LOG.assertTrue(vFile.isValid());
    LOG.assertTrue(!myDisposed);
    if (!myInitialized) return null;

    dispatchPendingEvents();

    return getCachedPsiFileInner(vFile);
  }

  @NotNull
  public GlobalSearchScope getResolveScope(@NotNull PsiElement element) {
    final ProgressManager progressManager = ProgressManager.getInstance();
    progressManager.checkCanceled();

    VirtualFile vFile;
    final Project project = myManager.getProject();
    if (element instanceof PsiDirectory) {
      vFile = ((PsiDirectory)element).getVirtualFile();
    }
    else {
      final PsiFile containingFile = element.getContainingFile();
      if (containingFile instanceof PsiCodeFragment) {
        final GlobalSearchScope forcedScope = ((PsiCodeFragment)containingFile).getForcedResolveScope();
        if (forcedScope != null) {
          return forcedScope;
        }
        final PsiElement context = containingFile.getContext();
        if (context == null) {
          return GlobalSearchScope.allScope(project);
        }
        return getResolveScope(context);
      }

      final PsiFile contextFile = containingFile != null ? ResolveUtil.getContextFile(containingFile) : null;
      if (contextFile == null || contextFile instanceof XmlFile) {
        return GlobalSearchScope.allScope(project);
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
      return GlobalSearchScope.allScope(project);
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
      List<Module> modulesLibraryUsedIn = new ArrayList<Module>();
      List<OrderEntry> orderEntries = projectFileIndex.getOrderEntriesForFile(vFile);
      for (OrderEntry entry : orderEntries) {
        progressManager.checkCanceled();

        if (entry instanceof JdkOrderEntry) {
          return ((ProjectRootManagerEx)myProjectRootManager).getScopeForJdk((JdkOrderEntry)entry);
        }

        if (entry instanceof LibraryOrderEntry) {
          Module ownerModule = entry.getOwnerModule();
          modulesLibraryUsedIn.add(ownerModule);
        }
      }

      return ((ProjectRootManagerEx)myProjectRootManager).getScopeForLibraryUsedIn(modulesLibraryUsedIn);
    }
  }

  @NotNull
  public GlobalSearchScope getUseScope(@NotNull PsiElement element) {
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

  @Nullable
  private PsiFile createFileCopyWithNewName(VirtualFile vFile, String name) {
    // TODO[ik] remove this. Event handling and generation must be in view providers mechanism since we
    // need to track changes in _all_ psi views (e.g. namespace changes in XML)
    final FileTypeManager instance = FileTypeManager.getInstance();
    if(instance.isFileIgnored(name)) return null;
    final FileType fileTypeByFileName = instance.getFileTypeByFileName(name);
    final Document document = FileDocumentManager.getInstance().getDocument(vFile);
    return myManager.getElementFactory().createFileFromText(name, fileTypeByFileName,
                                                            document != null ? document.getCharsSequence() : "",
                                                            vFile.getModificationStamp(), true, false);
  }

  @Nullable
  public PsiDirectory findDirectory(@NotNull VirtualFile vFile) {
    LOG.assertTrue(myInitialized, "Access to psi files should be performed only after startup activity");
    LOG.assertTrue(!myDisposed, "Access to psi files should not be performed after disposal");

    ApplicationManager.getApplication().assertReadAccessAllowed();
    if (!vFile.isValid()) {
      LOG.error("File is not valid:" + vFile.getName());
    }

    if (!vFile.isDirectory()) return null;

    dispatchPendingEvents();

    PsiDirectory psiDir = myVFileToPsiDirMap.get(vFile);
    if (psiDir != null) return psiDir;

    if (myProjectRootManager.getFileIndex().isIgnored(vFile)) return null;

    VirtualFile parent = vFile.getParent();
    if (parent != null) { //?
      findDirectory(parent);// need to cache parent directory - used for firing events
    }
    psiDir = new PsiDirectoryImpl(myManager, vFile);
    return myVFileToPsiDirMap.cacheOrGet(vFile, psiDir);
  }

  @Nullable
  public PsiPackage findPackage(@NotNull String packageName) {
    Query<VirtualFile> dirs = myProjectRootManager.getFileIndex().getDirsByPackageName(packageName, false);
    if (dirs.findFirst() == null) return null;
    return new PsiPackageImpl(myManager, packageName);
  }

  public PsiDirectory[] getRootDirectories(int rootType) {
    return myRootManager.getRootDirectories(rootType);
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
      LOG.assertTrue(aClass != null);
      LOG.assertTrue(aClass.isValid());

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
      final PsiNameHelper nameHelper = myManager.getNameHelper();
      while (parent != null && parent != root) {
        if (!nameHelper.isIdentifier(parent.getName())) return false;
        parent = parent.getParent();
      }
    }

    return true;
  }

  @Nullable
  private PsiFile getCachedPsiFileInner(VirtualFile file) {
    final FileViewProvider fileViewProvider = myVFileToViewProviderMap.get(file);
    return fileViewProvider == null ? null : ((SingleRootFileViewProvider)fileViewProvider).getCachedPsi(fileViewProvider.getBaseLanguage());
  }

  public List<PsiFile> getAllCachedFiles() {
    List<PsiFile> files = new ArrayList<PsiFile>();
    for (FileViewProvider provider : myVFileToViewProviderMap.values()) {
      if (provider != null) {
        files.add(((SingleRootFileViewProvider)provider).getCachedPsi(provider.getBaseLanguage()));
      }
    }
    return files;
  }

  private void removeInvalidFilesAndDirs(boolean useFind) {
    ConcurrentHashMap<VirtualFile, PsiDirectory> fileToPsiDirMap = new ConcurrentHashMap<VirtualFile, PsiDirectory>((Map<? extends VirtualFile,? extends PsiDirectory>)myVFileToPsiDirMap);
    if (useFind) {
      myVFileToPsiDirMap.clear();
    }
    for (Iterator<VirtualFile> iterator = fileToPsiDirMap.keySet().iterator(); iterator.hasNext();) {
      VirtualFile vFile = iterator.next();
      if (!vFile.isValid()) {
        iterator.remove();
      }
      else {
        PsiDirectory psiDir = findDirectory(vFile);
        if (psiDir == null) {
          iterator.remove();
        }
      }
    }
    myVFileToPsiDirMap.clear();
    myVFileToPsiDirMap.putAll(fileToPsiDirMap);

    // note: important to update directories map first - findFile uses findDirectory!
    ConcurrentWeakValueHashMap<VirtualFile, FileViewProvider> fileToPsiFileMap = new ConcurrentWeakValueHashMap<VirtualFile, FileViewProvider>(myVFileToViewProviderMap);
    if (useFind) {
      myVFileToViewProviderMap.clear();
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

        if (!psiFile1.getClass().equals(view.getPsi(view.getBaseLanguage()).getClass()) ||
             psiFile1.getViewProvider().getBaseLanguage() != view.getBaseLanguage() // e.g. JSP <-> JSPX
           ) {
          iterator.remove();
        }
      }
    }
    myVFileToViewProviderMap.clear();
    myVFileToViewProviderMap.putAll(fileToPsiFileMap);
  }

  public void reloadFromDisk(@NotNull PsiFile file) {
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
          //if (((ClsFileImpl)file).isContentsLoaded()) {
            ((ClsFileImpl)file).unloadContent();
          //}
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
            VirtualFile parent = vFile.getParent();
            PsiDirectory parentDir = parent == null ? null : myVFileToPsiDirMap.get(parent);
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

      VirtualFile parent = vFile.getParent();
      final PsiDirectory parentDir = parent == null ? null : myVFileToPsiDirMap.get(parent);
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

      VirtualFile parent = event.getParent();
      final PsiDirectory parentDir = parent == null ? null : myVFileToPsiDirMap.get(parent);

      final PsiFile psiFile = getCachedPsiFileInner(vFile);
      if (psiFile != null) {
        myVFileToViewProviderMap.remove(vFile);

        if (parentDir != null) {
          ApplicationManager.getApplication().runWriteAction(new PsiExternalChangeAction() {
            public void run() {
              PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
              treeEvent.setParent(parentDir);
              treeEvent.setChild(psiFile);
              myManager.childRemoved(treeEvent);
            }
          });
        }
      }
      else {
        final PsiDirectory psiDir = myVFileToPsiDirMap.get(vFile);
        if (psiDir != null) {
          removeInvalidFilesAndDirs(false);

          if (parentDir != null) {
            ApplicationManager.getApplication().runWriteAction(new PsiExternalChangeAction() {
              public void run() {
                PsiTreeChangeEventImpl treeEvent = new PsiTreeChangeEventImpl(myManager);
                treeEvent.setParent(parentDir);
                treeEvent.setChild(psiDir);
                myManager.childRemoved(treeEvent);
              }
            });
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

      VirtualFile parent = vFile.getParent();
      final PsiDirectory parentDir = parent == null ? null : myVFileToPsiDirMap.get(parent);
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
      if (parent == null) return false;

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

      VirtualFile parent = vFile.getParent();
      final PsiDirectory parentDir = parent == null ? null : myVFileToPsiDirMap.get(parent);
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
                    myVFileToViewProviderMap.remove(vFile);

                    treeEvent.setChild(oldPsiFile);
                    myManager.childRemoved(treeEvent);
                  }
                  else if (!newPsiFile.getClass().equals(oldPsiFile.getClass()) ||
                           newPsiFile.getFileType() != myFileTypeManager.getFileTypeByFileName((String)event.getOldValue()) ||
                           languageDialectChanged(newPsiFile)
                          ) {
                    myVFileToViewProviderMap.put(vFile, fileViewProvider);

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
                    myVFileToViewProviderMap.put(vFile, fileViewProvider);
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
                  myVFileToViewProviderMap.put(vFile, newViewProvider);
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
                myVFileToViewProviderMap.remove(vFile);
                treeEvent.setParent(oldParentDir);
                treeEvent.setChild(oldElement);
                myManager.childRemoved(treeEvent);
              }
            }
            else {
              myVFileToViewProviderMap.put(vFile, newViewProvider);
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
      else myVFileToViewProviderMap.remove(vFile);
    }
  }

  // When file is renamed so that extension changes then language dialect might change and thus psiFile should be invalidated
  // We could detect it right now with checks of parser definition equivalence
  // The file name under passed psi file is "new" but parser def is from old name
  private static boolean languageDialectChanged(final PsiFile newPsiFile) {
    return ( newPsiFile.getLanguageDialect() != null && 
             newPsiFile.getLanguageDialect().getParserDefinition().getClass() != newPsiFile.getLanguage().getParserDefinition().getClass()
           ) ||
           ( newPsiFile.getLanguageDialect() == null &&
             newPsiFile instanceof PsiFileBase &&
             newPsiFile.getLanguage().getParserDefinition().getClass() == ((PsiFileBase)newPsiFile).getParserDefinition().getClass()
           );
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
    Set<VirtualFile> vFiles = myVFileToViewProviderMap.keySet();
    for (VirtualFile fileCacheEntry : vFiles) {
      final FileViewProvider view = myVFileToViewProviderMap.get(fileCacheEntry);
      PsiFile psiFile = view.getPsi(view.getBaseLanguage());
      if (psiFile instanceof PsiFileImpl && ((PsiFileImpl)psiFile).isContentsLoaded()) {
        out.write(fileCacheEntry.getPresentableUrl());
        out.write("\n");
      }
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
