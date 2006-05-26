package com.intellij.psi.impl;

import com.intellij.formatting.FormatterEx;
import com.intellij.formatting.FormatterImpl;
import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.j2ee.extResources.ExternalResourceListener;
import com.intellij.j2ee.openapi.ex.ExternalResourceManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.RepositoryManager;
import com.intellij.psi.impl.cache.impl.CacheManagerImpl;
import com.intellij.psi.impl.cache.impl.CacheUtil;
import com.intellij.psi.impl.cache.impl.CompositeCacheManager;
import com.intellij.psi.impl.cache.impl.RepositoryManagerImpl;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.migration.PsiMigrationImpl;
import com.intellij.psi.impl.search.PsiSearchHelperImpl;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.javadoc.JavadocManagerImpl;
import com.intellij.psi.impl.source.resolve.PsiResolveHelperImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.javadoc.JavadocManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;

public class PsiManagerImpl extends PsiManager implements ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiManagerImpl");

  private final Project myProject;

  private FileManager myFileManager;
  private PsiElementFactory myElementFactory;
  private PsiSearchHelper mySearchHelper;
  private PsiShortNamesCache myShortNamesCache;
  private PsiResolveHelper myResolveHelper;
  //private MemoryManager myMemoryManager;
  private CacheManager myCacheManager;
  private RepositoryManager myRepositoryManager;
  private RepositoryElementsManager myRepositoryElementsManager;
  private JavadocManager myJavadocManager;
  private PsiNameHelper myNameHelper;
  private PsiModificationTrackerImpl myModificationTracker;
  private ResolveCache myResolveCache;
  private CachedValuesManager myCachedValuesManager;
  private PsiConstantEvaluationHelper myConstantEvaluationHelper;
  private Map<String, PsiPackage> myPackageCache = new HashMap<String, PsiPackage>();

  private final ArrayList<PsiTreeChangeListener> myTreeChangeListeners = new ArrayList<PsiTreeChangeListener>();
  private PsiTreeChangeListener[] myCachedTreeChangeListeners = null;
  private boolean myTreeChangeEventIsFiring = false;

  private final HashMap myUserMap = new HashMap();

  private final ArrayList<Runnable> myRunnablesOnChange = new ArrayList<Runnable>();
  private final ArrayList<WeakReference<Runnable>> myWeakRunnablesOnChange = new ArrayList<WeakReference<Runnable>>();
  private final ArrayList<Runnable> myRunnablesOnAnyChange = new ArrayList<Runnable>();
  private final ArrayList<Runnable> myRunnablesAfterAnyChange = new ArrayList<Runnable>();

  private ExternalResourceListener myExternalResourceListener;
  private boolean myIsDisposed;

  private VirtualFileFilter myAssertOnFileLoadingFilter = VirtualFileFilter.NONE;

  private int myBatchFilesProcessingModeCount = 0;

  private static final Key<PsiFile> CACHED_PSI_FILE_COPY_IN_FILECONTENT = Key.create("CACHED_PSI_FILE_COPY_IN_FILECONTENT");
  public static final int BEFORE_CHILD_ADDITION = 0;
  public static final int BEFORE_CHILD_REMOVAL = 1;
  public static final int BEFORE_CHILD_REPLACEMENT = 2;
  public static final int BEFORE_CHILD_MOVEMENT = 3;
  public static final int BEFORE_CHILDREN_CHANGE = 4;
  public static final int BEFORE_PROPERTY_CHANGE = 5;
  public static final int CHILD_ADDED = 6;
  public static final int CHILD_REMOVED = 7;
  public static final int CHILD_REPLACED = 8;
  public static final int CHILD_MOVED = 9;
  public static final int CHILDREN_CHANGED = 10;
  public static final int PROPERTY_CHANGED = 11;
  private PsiMigrationImpl myCurrentMigration;
  private LanguageLevel myLanguageLevel;
  private PsiElementFinder[] myElementFinders;

  private List<LanguageInjector> myLanguageInjectors = new ArrayList<LanguageInjector>();
  private ProgressManager myProgressManager;

  public PsiManagerImpl(Project project,
                        PsiManagerConfiguration psiManagerConfiguration,
                        final ProjectRootManagerEx projectRootManagerEx,
                        ExternalResourceManagerEx externalResourceManagerEx,
                        StartupManagerEx startupManagerEx,
                        FileTypeManager fileTypeManager,
                        VirtualFileManager virtualFileManager,
                        FileDocumentManager fileDocumentManager) {
    myProject = project;

    if (psiManagerConfiguration.REPOSITORY_ENABLED) {
      myRepositoryManager = new RepositoryManagerImpl(this);
    } else {
      myRepositoryManager = new EmptyRepository.MyRepositoryManagerImpl();
    }

    myLanguageLevel = projectRootManagerEx.getLanguageLevel();

    boolean isProjectDefault = project.isDefault();

    myFileManager = isProjectDefault ? new EmptyRepository.EmptyFileManager() : new FileManagerImpl(this, fileTypeManager,
                                                                                                    virtualFileManager, fileDocumentManager,
                                                                                                    projectRootManagerEx);
    myElementFactory = new PsiElementFactoryImpl(this);
    mySearchHelper = new PsiSearchHelperImpl(this);
    myResolveHelper = new PsiResolveHelperImpl(this);
    //myMemoryManager = new MemoryManager();
    final CompositeCacheManager cacheManager = new CompositeCacheManager();
    if (psiManagerConfiguration.REPOSITORY_ENABLED && !isProjectDefault) {
      myShortNamesCache = new PsiShortNamesCacheImpl(this, projectRootManagerEx);
      cacheManager.addCacheManager(new CacheManagerImpl(this));
      myRepositoryElementsManager = new RepositoryElementsManager(this);
    }
    else {
      myShortNamesCache = new EmptyRepository.PsiShortNamesCacheImpl();
      cacheManager.addCacheManager(new EmptyRepository.CacheManagerImpl());
      myRepositoryElementsManager = new EmptyRepository.MyRepositoryElementsManager(this);
    }
    final CacheManager[] managers = myProject.getComponents(CacheManager.class);
    for (CacheManager manager : managers) {
      cacheManager.addCacheManager(manager);
    }

    myCacheManager = cacheManager;

    myJavadocManager = new JavadocManagerImpl();
    myNameHelper = new PsiNameHelperImpl(this);
    myExternalResourceListener = new MyExternalResourceListener();
    myModificationTracker = new PsiModificationTrackerImpl(this);
    myResolveCache = new ResolveCache(this);
    myCachedValuesManager = new CachedValuesManagerImpl(this);
    myConstantEvaluationHelper = new PsiConstantEvaluationHelperImpl();

    List<PsiElementFinder> elementFinders = new ArrayList<PsiElementFinder>();
    elementFinders.addAll(Arrays.asList(myProject.getComponents(PsiElementFinder.class)));
    elementFinders.add(new PsiElementFinderImpl()); //this finder should be added at end for Fabrique's needs
    myElementFinders = elementFinders.toArray(new PsiElementFinder[elementFinders.size()]);

    if (externalResourceManagerEx != null) {
      externalResourceManagerEx.addExteralResourceListener(myExternalResourceListener);
    }

    if (startupManagerEx != null) {
      startupManagerEx.registerPreStartupActivity(
        new Runnable() {
          public void run() {
            // update effective language level before the project is opened because it might be changed
            // e.g. while setting up newly created project
            if (!ApplicationManager.getApplication().isUnitTestMode()) {
              myLanguageLevel = projectRootManagerEx.getLanguageLevel();
            }
            runPreStartupActivity();
          }
        }
      );

      startupManagerEx.registerStartupActivity(
        new Runnable() {
          public void run() {
            runStartupActivity();
          }
        }
      );
    }
    myProgressManager = ProgressManager.getInstance();
  }

  @NotNull
  public PsiConstantEvaluationHelper getConstantEvaluationHelper() {
    return myConstantEvaluationHelper;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myFileManager.dispose();
    myCacheManager.dispose();
    myRepositoryManager.dispose();

    ExternalResourceManagerEx externalResourceManager = ExternalResourceManagerEx.getInstanceEx();
    if (externalResourceManager != null) {
      externalResourceManager.removeExternalResourceListener(myExternalResourceListener);
    }
    myIsDisposed = true;
  }

  public boolean isDisposed() {
    return myIsDisposed;
  }

  @NotNull
  public LanguageLevel getEffectiveLanguageLevel() {
    return myLanguageLevel;
  }

  public boolean isPartOfPackagePrefix(String packageName) {
    final Collection<String> packagePrefixes = myFileManager.getNonTrivialPackagePrefixes();
    for (final String subpackageName : packagePrefixes) {
      if (isSubpackageOf(subpackageName, packageName)) return true;
    }
    return false;
  }

  private static boolean isSubpackageOf(final String subpackageName, String packageName) {
    if (subpackageName.equals(packageName)) return true;
    if (!subpackageName.startsWith(packageName)) return false;
    return subpackageName.charAt(packageName.length()) == '.';
  }

  public void dropResolveCaches() {
    myResolveCache.clearCache();
  }

  public boolean isInPackage(PsiElement element, PsiPackage aPackage) {
    final PsiFile file = ResolveUtil.getContextFile(element);
    if (file instanceof DummyHolder) {
      return ((DummyHolder) file).isInPackage(aPackage);
    }
    if (file instanceof PsiJavaFile) {
      final String packageName = ((PsiJavaFile) file).getPackageName();
      return packageName.equals(aPackage != null ? aPackage.getQualifiedName() : "");
    }
    return false;
  }

  public boolean isInProject(PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file instanceof PsiFileImpl && file.isPhysical() && file.getViewProvider().getVirtualFile() instanceof LightVirtualFile) return true;

    if (element instanceof PsiPackage) {
      PsiDirectory[] dirs = ((PsiPackage) element).getDirectories();
      for (PsiDirectory dir : dirs) {
        if (!isInProject(dir)) return false;
      }
      return true;
    }

    Module module = ModuleUtil.findModuleForPsiElement(element);
    return module != null;
  }

  public void performActionWithFormatterDisabled(final Runnable r) {
    final PostprocessReformattingAspect component = getProject().getComponent(PostprocessReformattingAspect.class);
    try {
      ((FormatterImpl)FormatterEx.getInstance()).disableFormatting();
      component.disablePostprocessFormattingInside(new Computable<Object>() {
        public Object compute() {
          r.run();
          return null;
        }
      });
    }
    finally {
      ((FormatterImpl)FormatterEx.getInstance()).enableFormatting();
    }
  }

  public <T> T performActionWithFormatterDisabled(Computable<T> r) {
    try {
      final PostprocessReformattingAspect component = PostprocessReformattingAspect.getInstance(getProject());
      ((FormatterImpl)FormatterEx.getInstance()).disableFormatting();
      return component.disablePostprocessFormattingInside(r);
    }
    finally {
      ((FormatterImpl)FormatterEx.getInstance()).enableFormatting();
    }
  }

  public void registerLanguageInjector(LanguageInjector injector) {
    myLanguageInjectors.add(injector);
  }

  public void unregisterLanguageInjector(@NotNull LanguageInjector injector) {
    myLanguageInjectors.remove(injector);
  }

  public void postponeAutoFormattingInside(Runnable runnable) {
    PostprocessReformattingAspect.getInstance(getProject()).postponeFormattingInside(runnable);
  }

  public boolean arePackagesTheSame(PsiElement element1, PsiElement element2) {
    PsiFile file1 = ResolveUtil.getContextFile(element1);
    PsiFile file2 = ResolveUtil.getContextFile(element2);
    if (Comparing.equal(file1, file2)) return true;
    if (file1 instanceof DummyHolder && file2 instanceof DummyHolder) return true;
    if (file1 instanceof DummyHolder || file2 instanceof DummyHolder) {
      DummyHolder dummyHolder = (DummyHolder) (file1 instanceof DummyHolder ? file1 : file2);
      PsiElement other = file1 instanceof DummyHolder ? file2 : file1;
      return dummyHolder.isSamePackage(other);
    }
    if (!(file1 instanceof PsiJavaFile)) return false;
    if (!(file2 instanceof PsiJavaFile)) return false;
    String package1 = ((PsiJavaFile) file1).getPackageName();
    String package2 = ((PsiJavaFile) file2).getPackageName();
    return Comparing.equal(package1, package2);
  }


  public void projectClosed() {
  }

  public void projectOpened() {
  }

  public void runStartupActivity() {
    myShortNamesCache.runStartupActivity();
  }

  public void runPreStartupActivity() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("PsiManager.runPreStartupActivity()");
    }
    myFileManager.runStartupActivity();

    myCacheManager.initialize();

    StartupManagerEx startupManager = StartupManagerEx.getInstanceEx(myProject);
    if (startupManager != null) {
      FileSystemSynchronizer synchronizer = startupManager.getFileSystemSynchronizer();

      if (PsiManagerConfiguration.getInstance().REPOSITORY_ENABLED) {
        synchronizer.registerCacheUpdater(myRepositoryManager.getCacheUpdater());

        CacheUpdater[] updaters = myCacheManager.getCacheUpdaters();
        for (CacheUpdater updater : updaters) {
          synchronizer.registerCacheUpdater(updater);
        }
      }
    }
  }

  public void setAssertOnFileLoadingFilter(VirtualFileFilter filter) {
    myAssertOnFileLoadingFilter = filter;
  }

  public boolean isAssertOnFileLoading(VirtualFile file) {
    return myAssertOnFileLoadingFilter.accept(file);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public FileManager getFileManager() {
    return myFileManager;
  }

  public RepositoryManager getRepositoryManager() {
    LOG.assertTrue(!myIsDisposed);
    return myRepositoryManager;
  }

  public RepositoryElementsManager getRepositoryElementsManager() {
    return myRepositoryElementsManager;
  }

  public CacheManager getCacheManager() {
    LOG.assertTrue(!myIsDisposed);
    return myCacheManager;
  }

  @NotNull
  public CodeStyleManager getCodeStyleManager() {
    return CodeStyleManager.getInstance(myProject);
  }

  public ResolveCache getResolveCache() {
    myProgressManager.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly
    return myResolveCache;
  }

  @NotNull
  public PsiDirectory[] getRootDirectories(int rootType) {
    return myFileManager.getRootDirectories(rootType);
  }

  /**
   * @deprecated
   */
  public PsiClass findClass(String qualifiedName) {
    return findClass(qualifiedName, GlobalSearchScope.allScope(myProject));
  }

  public PsiClass findClass(String qualifiedName, GlobalSearchScope scope) {
    myProgressManager.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly

    for (PsiElementFinder finder : myElementFinders) {
      PsiClass aClass = finder.findClass(qualifiedName, scope);
      if (aClass != null) return aClass;
    }

    return null;
  }

  @NotNull
  public PsiClass[] findClasses(String qualifiedName, GlobalSearchScope scope) {
    List<PsiClass> classes = new ArrayList<PsiClass>();
    for (PsiElementFinder finder : myElementFinders) {
      PsiClass[] finderClasses = finder.findClasses(qualifiedName, scope);
      for (PsiClass finderClass : finderClasses) {
        classes.add(finderClass);
      }
    }

    return classes.toArray(new PsiClass[classes.size()]);
  }

  public boolean areElementsEquivalent(PsiElement element1, PsiElement element2) {
    myProgressManager.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly

    if (element1 == element2) return true;
    if (element1 == null || element2 == null) {
      return false;
    }
    if (element1.equals(element2)) return true;
    if (element1 instanceof PsiDirectory || element2 instanceof PsiDirectory) {
      return false;
    }
    if (element1 instanceof PsiClass) {
      if (!(element2 instanceof PsiClass)) return false;
      String name1 = ((PsiClass)element1).getName();
      if (name1 == null) return false;
      String name2 = ((PsiClass)element2).getName();
      if (name2 == null) return false;
      if (name1.hashCode() != name2.hashCode()) return false;
      if (!name1.equals(name2)) return false;
      String qName1 = ((PsiClass)element1).getQualifiedName();
      String qName2 = ((PsiClass)element2).getQualifiedName();
      if (qName1 == null || qName2 == null) {
        //noinspection StringEquality
        if (qName1 != qName2) return false;

        if (element1 instanceof PsiTypeParameter && element2 instanceof PsiTypeParameter) {
          PsiTypeParameter p1 = (PsiTypeParameter)element1;
          PsiTypeParameter p2 = (PsiTypeParameter)element2;

          return p1.getIndex() == p2.getIndex() &&
                 areElementsEquivalent(p1.getOwner(), p2.getOwner());

        }
        else {
          return false;
        }
      }
      return qName1.hashCode() == qName2.hashCode() && qName1.equals(qName2);
    }
    if (element1 instanceof PsiField) {
      if (!(element2 instanceof PsiField)) return false;
      String name1 = ((PsiField)element1).getName();
      if (name1 == null) return false;
      String name2 = ((PsiField)element2).getName();
      if (!name1.equals(name2)) return false;
      PsiClass aClass1 = ((PsiField)element1).getContainingClass();
      PsiClass aClass2 = ((PsiField)element2).getContainingClass();
      return aClass1 != null && aClass2 != null && areElementsEquivalent(aClass1, aClass2);
    }
    if (element1 instanceof PsiMethod) {
      if (!(element2 instanceof PsiMethod)) return false;
      String name1 = ((PsiMethod)element1).getName();
      String name2 = ((PsiMethod)element2).getName();
      if (!name1.equals(name2)) return false;
      PsiClass aClass1 = ((PsiMethod)element1).getContainingClass();
      PsiClass aClass2 = ((PsiMethod)element2).getContainingClass();
      if (aClass1 == null || aClass2 == null || !areElementsEquivalent(aClass1, aClass2)) return false;
      return MethodSignatureUtil.areSignaturesEqual(((PsiMethod)element1).getSignature(PsiSubstitutor.EMPTY),
                                                    ((PsiMethod)element2).getSignature(PsiSubstitutor.EMPTY));
    }

    if (element1 instanceof XmlTag && element2 instanceof XmlTag) {
      if (!element1.isPhysical() && !element2.isPhysical()) return element1.getText().equals(element2.getText());
    }

    if (element1 instanceof XmlElementDecl && element2 instanceof XmlElementDecl) {
      if (!element1.isPhysical()) element1 = element1.getOriginalElement();  
      if (!element2.isPhysical()) element2 = element2.getOriginalElement();
      return element1 == element2;
    }
    
    return false;
  }

  public PsiFile findFile(VirtualFile file) {
    return myFileManager.findFile(file);
  }

  @Nullable
  public FileViewProvider findViewProvider(@NotNull VirtualFile file) {
    return myFileManager.findViewProvider(file);
  }

  public void cleanupForNextTest() {
    //myFileManager.cleanupForNextTest();
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode());
  }

  @Nullable
  public PsiFile getFile(FileContent content) {
    PsiFile psiFile = content.getUserData(CACHED_PSI_FILE_COPY_IN_FILECONTENT);
    if (psiFile == null) {
      final VirtualFile vFile = content.getVirtualFile();
      psiFile = myFileManager.getCachedPsiFile(vFile);
      if (psiFile == null) {
        psiFile = findFile(vFile);
        if (psiFile == null) return null;
        psiFile = CacheUtil.createFileCopy(content, psiFile);
      }
      content.putUserData(CACHED_PSI_FILE_COPY_IN_FILECONTENT, psiFile);
    }

    LOG.assertTrue(psiFile instanceof PsiCompiledElement || psiFile.isValid());
    return psiFile;
  }

  public PsiDirectory findDirectory(VirtualFile file) {
    return myFileManager.findDirectory(file);
  }

  public PsiPackage findPackage(String qualifiedName) {
    PsiPackage aPackage = myPackageCache.get(qualifiedName);
    if (aPackage == null) {
      for (PsiElementFinder finder : myElementFinders) {
        aPackage = finder.findPackage(qualifiedName);
        if (aPackage != null) {
          myPackageCache.put(qualifiedName, aPackage);
          break;
        }
      }
    }

    return aPackage;
  }

  public PsiMigrationImpl getCurrentMigration() {
    return myCurrentMigration;
  }

  public void invalidateFile(PsiFile file) {
    if (myIsDisposed) {
      LOG.error("Disposed PsiManager calls invalidateFile!");
    }

    final VirtualFile virtualFile = file.getVirtualFile();
    if (file.getViewProvider().isPhysical() && myCacheManager != null) {
      myCacheManager.addOrInvalidateFile(virtualFile);
    }
  }

  public void reloadFromDisk(PsiFile file) {
    myFileManager.reloadFromDisk(file);
  }

  public void addPsiTreeChangeListener(PsiTreeChangeListener listener) {
    myTreeChangeListeners.add(listener);
    myCachedTreeChangeListeners = null;
  }

  public void removePsiTreeChangeListener(PsiTreeChangeListener listener) {
    myTreeChangeListeners.remove(listener);
    myCachedTreeChangeListeners = null;
  }

  public void beforeChildAddition(PsiTreeChangeEventImpl event) {
    event.setCode(BEFORE_CHILD_ADDITION);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "beforeChildAddition: parent = " + event.getParent()
      );
    }
    fireEvent(event);
  }

  public void beforeChildRemoval(PsiTreeChangeEventImpl event) {
    event.setCode(BEFORE_CHILD_REMOVAL);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "beforeChildRemoval: child = " + event.getChild()
        + ", parent = " + event.getParent()
      );
    }
    fireEvent(event);
  }

  public void beforeChildReplacement(PsiTreeChangeEventImpl event) {
    event.setCode(BEFORE_CHILD_REPLACEMENT);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "beforeChildReplacement: oldChild = " + event.getOldChild()
        + ", parent = " + event.getParent()
      );
    }
    fireEvent(event);
  }

  public void beforeChildrenChange(PsiTreeChangeEventImpl event) {
    event.setCode(BEFORE_CHILDREN_CHANGE);
    if (LOG.isDebugEnabled()) {
      LOG.debug("beforeChildrenChange: parent = " + event.getParent());
    }
    fireEvent(event);
  }

  public void beforeChildMovement(PsiTreeChangeEventImpl event) {
    event.setCode(BEFORE_CHILD_MOVEMENT);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "beforeChildMovement: child = " + event.getChild()
        + ", oldParent = " + event.getOldParent()
        + ", newParent = " + event.getNewParent()
      );
    }
    fireEvent(event);
  }

  public void beforePropertyChange(PsiTreeChangeEventImpl event) {
    event.setCode(BEFORE_PROPERTY_CHANGE);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "beforePropertyChange: element = " + event.getElement()
        + ", propertyName = " + event.getPropertyName()
        + ", oldValue = " + event.getOldValue()
      );
    }
    fireEvent(event);
  }

  public void childAdded(PsiTreeChangeEventImpl event) {
    onChange(true);
    event.setCode(CHILD_ADDED);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "childAdded: child = " + event.getChild()
        + ", parent = " + event.getParent()
      );
    }
    fireEvent(event);
    afterAnyChange();
  }

  public void childRemoved(PsiTreeChangeEventImpl event) {
    onChange(true);
    event.setCode(CHILD_REMOVED);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "childRemoved: child = " + event.getChild() + ", parent = " + event.getParent()
      );
    }
    fireEvent(event);
    afterAnyChange();
  }

  public void childReplaced(PsiTreeChangeEventImpl event) {
    onChange(true);
    event.setCode(CHILD_REPLACED);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "childReplaced: oldChild = " + event.getOldChild()
        + ", newChild = " + event.getNewChild()
        + ", parent = " + event.getParent()
      );
    }
    fireEvent(event);
    afterAnyChange();
  }

  public void childMoved(PsiTreeChangeEventImpl event) {
    onChange(true);
    event.setCode(CHILD_MOVED);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "childMoved: child = " + event.getChild()
        + ", oldParent = " + event.getOldParent()
        + ", newParent = " + event.getNewParent()
      );
    }
    fireEvent(event);
    afterAnyChange();
  }

  public void childrenChanged(PsiTreeChangeEventImpl event) {
    onChange(true);
    event.setCode(CHILDREN_CHANGED);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "childrenChanged: parent = " + event.getParent()
      );
    }
    fireEvent(event);
    afterAnyChange();
  }

  public void propertyChanged(PsiTreeChangeEventImpl event) {
    onChange(true);
    event.setCode(PROPERTY_CHANGED);
    if (LOG.isDebugEnabled()) {
      LOG.debug(
        "propertyChanged: element = " + event.getElement()
        + ", propertyName = " + event.getPropertyName()
        + ", oldValue = " + event.getOldValue()
        + ", newValue = " + event.getNewValue()
      );
    }
    fireEvent(event);
    afterAnyChange();
  }

  private void fireEvent(PsiTreeChangeEventImpl event) {
    boolean isRealTreeChange = event.getCode() != PROPERTY_CHANGED && event.getCode() != BEFORE_PROPERTY_CHANGE;

    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (isRealTreeChange) {
      LOG.assertTrue(!myTreeChangeEventIsFiring, "Changes to PSI are not allowed inside event processing");
    }

    if (isRealTreeChange) {
      myTreeChangeEventIsFiring = true;
    }
    try {
      myModificationTracker.treeChanged(event);

      if (myCachedTreeChangeListeners == null) {
        myCachedTreeChangeListeners = myTreeChangeListeners.toArray(
          new PsiTreeChangeListener[myTreeChangeListeners.size()]
        );
      }
      PsiTreeChangeListener[] listeners = myCachedTreeChangeListeners;
      for (PsiTreeChangeListener listener : listeners) {
        try {
          switch (event.getCode()) {
            case BEFORE_CHILD_ADDITION:
              listener.beforeChildAddition(event);
              break;

            case BEFORE_CHILD_REMOVAL:
              listener.beforeChildRemoval(event);
              break;

            case BEFORE_CHILD_REPLACEMENT:
              listener.beforeChildReplacement(event);
              break;

            case BEFORE_CHILD_MOVEMENT:
              listener.beforeChildMovement(event);
              break;

            case BEFORE_CHILDREN_CHANGE:
              listener.beforeChildrenChange(event);
              break;

            case BEFORE_PROPERTY_CHANGE:
              listener.beforePropertyChange(event);
              break;

            case CHILD_ADDED:
              listener.childAdded(event);
              break;

            case CHILD_REMOVED:
              listener.childRemoved(event);
              break;

            case CHILD_REPLACED:
              listener.childReplaced(event);
              break;

            case CHILD_MOVED:
              listener.childMoved(event);
              break;

            case CHILDREN_CHANGED:
              listener.childrenChanged(event);
              break;

            case PROPERTY_CHANGED:
              listener.propertyChanged(event);
              break;
          }
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
    }
    finally {
      if (isRealTreeChange) {
        myTreeChangeEventIsFiring = false;
      }
    }
  }

  public void registerRunnableToRunOnChange(Runnable runnable) {
    myRunnablesOnChange.add(runnable);
  }

  public void registerWeakRunnableToRunOnChange(Runnable runnable) {
    myWeakRunnablesOnChange.add(new WeakReference<Runnable>(runnable));
  }

  public void registerRunnableToRunOnAnyChange(Runnable runnable) { // includes non-physical changes
    myRunnablesOnAnyChange.add(runnable);
  }

  public void registerRunnableToRunAfterAnyChange(Runnable runnable) { // includes non-physical changes
    myRunnablesAfterAnyChange.add(runnable);
  }

  public void nonPhysicalChange() {
    onChange(false);
  }

  private void onChange(boolean isPhysical) {
    if (isPhysical) {
      myPackageCache.clear();
      runRunnables(myRunnablesOnChange);

      WeakReference[] refs = myWeakRunnablesOnChange.toArray(
        new WeakReference[myWeakRunnablesOnChange.size()]);
      myWeakRunnablesOnChange.clear();
      for (WeakReference ref : refs) {
        Runnable runnable = (Runnable)ref.get();
        if (runnable != null) {
          runnable.run();
        }
      }
    }

    runRunnables(myRunnablesOnAnyChange);
  }

  private void afterAnyChange() {
    runRunnables(myRunnablesAfterAnyChange);
  }

  private static void runRunnables(ArrayList<Runnable> runnables) {
    Runnable[] array = runnables.toArray(new Runnable[runnables.size()]);
    for (Runnable aArray : array) {
      aArray.run();
    }
  }

  @NotNull
  public PsiElementFactory getElementFactory() {
    return myElementFactory;
  }

  @NotNull
  public PsiSearchHelper getSearchHelper() {
    return mySearchHelper;
  }

  @NotNull
  public PsiResolveHelper getResolveHelper() {
    return myResolveHelper;
  }

  @NotNull
  public PsiShortNamesCache getShortNamesCache() {
    return myShortNamesCache;
  }

  public void registerShortNamesCache(PsiShortNamesCache cache) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    if (myShortNamesCache instanceof CompositeShortNamesCache) {
      ((CompositeShortNamesCache)myShortNamesCache).addCache(cache);
    }
    else {
      CompositeShortNamesCache composite = new CompositeShortNamesCache();
      composite.addCache(myShortNamesCache);
      composite.addCache(cache);
      myShortNamesCache = composite;
    }
  }

  @NotNull
  public PsiMigration startMigration() {
    LOG.assertTrue(myCurrentMigration == null);
    myCurrentMigration = new PsiMigrationImpl(this);
    return myCurrentMigration;
  }

  @NotNull
  public JavadocManager getJavadocManager() {
    return myJavadocManager;
  }

  @NotNull
  public PsiModificationTracker getModificationTracker() {
    return myModificationTracker;
  }

  @NotNull
  public CachedValuesManager getCachedValuesManager() {
    return myCachedValuesManager;
  }

  @NotNull
  public PsiNameHelper getNameHelper() {
    return myNameHelper;
  }

  public void moveDirectory(final PsiDirectory dir, PsiDirectory newParent) throws IncorrectOperationException {
    checkMove(dir, newParent);

    try {
      dir.getVirtualFile().move(this, newParent.getVirtualFile());
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString(),e);
    }
  }

  public void moveFile(final PsiFile file, PsiDirectory newParent) throws IncorrectOperationException {
    checkMove(file, newParent);

    try {
      final VirtualFile virtualFile = file.getVirtualFile();
      assert virtualFile != null;
      virtualFile.move(this, newParent.getVirtualFile());
    }
    catch (IOException e) {
      throw new IncorrectOperationException(e.toString(),e);
    }
  }

  public void checkMove(PsiElement element, PsiElement newContainer) throws IncorrectOperationException {
    if (element instanceof PsiPackage) {
      PsiDirectory[] dirs = ((PsiPackage)element).getDirectories();
      if (dirs.length == 0) {
        throw new IncorrectOperationException();
      }
      else if (dirs.length > 1) {
        throw new IncorrectOperationException(
          "Moving of packages represented by more than one physical directory is not supported.");
      }
      checkMove(dirs[0], newContainer);
      return;
    }

    element.checkDelete();
    newContainer.checkAdd(element);
    checkIfMoveIntoSelf(element, newContainer);
  }

  private static void checkIfMoveIntoSelf(PsiElement element, PsiElement newContainer) throws IncorrectOperationException {
    PsiElement container = newContainer;
    while (container != null) {
      if (container == element) {
        if (element instanceof PsiDirectory) {
          if (element == newContainer) {
            throw new IncorrectOperationException("Cannot move directory into itself.");
          }
          else {
            throw new IncorrectOperationException("Cannot move directory into its subdirectory.");
          }
        }
        else {
          throw new IncorrectOperationException();
        }
      }
      container = container.getParent();
    }
  }

  public void startBatchFilesProcessingMode() {
    myBatchFilesProcessingModeCount++;
  }

  public void finishBatchFilesProcessingMode() {
    myBatchFilesProcessingModeCount--;
    LOG.assertTrue(myBatchFilesProcessingModeCount >= 0);
  }

  public boolean isBatchFilesProcessingMode() {
    return myBatchFilesProcessingModeCount > 0;
  }

  @SuppressWarnings({"unchecked"})
  public <T> T getUserData(Key<T> key) {
    synchronized (myUserMap) {
      return (T)myUserMap.get(key);
    }
  }

  @SuppressWarnings({"unchecked"})
  public <T> void putUserData(Key<T> key, T value) {
    synchronized (myUserMap) {
      if (value != null) {
        myUserMap.put(key, value);
      }
      else {
        myUserMap.remove(key);
      }
    }
  }

  @NotNull
  public String getComponentName() {
    return "PsiManager";
  }

  public void physicalChange() {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode(),
                   "DO NOT use this function in live code!!!");
    onChange(true);
  }

  public void migrationModified(boolean terminated) {
    if (terminated) {
      myCurrentMigration = null;
    }
    onChange(true);
  }

  public PsiClass[] getClasses(PsiPackageImpl psiPackage, GlobalSearchScope scope) {
    List<PsiClass> result = new ArrayList<PsiClass>();
    for (PsiElementFinder finder : myElementFinders) {
      PsiClass[] classes = finder.getClasses(psiPackage, scope);
      for (PsiClass aClass : classes) {
        result.add(aClass);
      }
    }

    return result.toArray(new PsiClass[result.size()]);
  }

  public PsiPackage[] getSubPackages(PsiPackageImpl psiPackage, GlobalSearchScope scope) {
    List<PsiPackage> result = new ArrayList<PsiPackage>();
    for (PsiElementFinder finder : myElementFinders) {
      PsiPackage[] packages = finder.getSubPackages(psiPackage, scope);
      for (PsiPackage aPackage : packages) {
        result.add(aPackage);
      }
    }

    return result.toArray(new PsiPackage[result.size()]);
  }

  @NotNull
  public List<? extends LanguageInjector> getLanguageInjectors() {
    return myLanguageInjectors;
  }

  private class MyExternalResourceListener implements ExternalResourceListener {
    public void externalResourceChanged() {
      onChange(true);
    }
  }

  public void setEffectiveLanguageLevel(LanguageLevel languageLevel) {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode() || myProject.isDefault(), "Use PsiManager.setEffectiveLanguageLevel only from unit tests");
    myLanguageLevel = languageLevel;
  }

  private class PsiElementFinderImpl implements PsiElementFinder {
    public PsiClass findClass(String qualifiedName, GlobalSearchScope scope) {
      PsiClass psiClass = myFileManager.findClass(qualifiedName, scope);

      if (psiClass == null && myCurrentMigration != null) {
        psiClass = myCurrentMigration.getMigrationClass(qualifiedName);
      }

      return psiClass;
    }

    @NotNull
    public PsiClass[] findClasses(String qualifiedName, GlobalSearchScope scope) {
      final PsiClass[] classes = myFileManager.findClasses(qualifiedName, scope);
      if (classes.length == 0 && myCurrentMigration != null) {
        final PsiClass migrationClass = myCurrentMigration.getMigrationClass(qualifiedName);
        if (migrationClass != null) {
          return new PsiClass[]{migrationClass};
        }
      }
      return classes;
    }

    public PsiPackage findPackage(String qualifiedName) {
      final PsiPackage aPackage = myFileManager.findPackage(qualifiedName);
      if (aPackage == null && myCurrentMigration != null) {
        final PsiPackage migrationPackage = myCurrentMigration.getMigrationPackage(qualifiedName);
        if (migrationPackage != null) return migrationPackage;
      }

      return aPackage;
    }

    @NotNull
    public PsiPackage[] getSubPackages(PsiPackage psiPackage, GlobalSearchScope scope) {
      final Map<String, PsiPackage> packagesMap = new HashMap<String, PsiPackage>();
      final String qualifiedName = psiPackage.getQualifiedName();
      final PsiDirectory[] dirs = psiPackage.getDirectories(scope);
      for (PsiDirectory dir : dirs) {
        PsiDirectory[] subdirs = dir.getSubdirectories();
        for (PsiDirectory subdir : subdirs) {
          final PsiPackage aPackage = subdir.getPackage();
          if (aPackage != null) {
            final String subQualifiedName = aPackage.getQualifiedName();
            if (subQualifiedName.startsWith(qualifiedName) && !packagesMap.containsKey(subQualifiedName)) {
              packagesMap.put(aPackage.getQualifiedName(), aPackage);
            }
          }
        }
      }
      return packagesMap.values().toArray(new PsiPackage[packagesMap.size()]);
    }

    @NotNull
    public PsiClass[] getClasses(PsiPackage psiPackage, GlobalSearchScope scope) {
      ArrayList<PsiClass> list = new ArrayList<PsiClass>();
      final PsiDirectory[] dirs = psiPackage.getDirectories(scope);
      for (PsiDirectory dir : dirs) {
        PsiClass[] classes = dir.getClasses();
        for (PsiClass aClass : classes) {
          list.add(aClass);
        }
      }
      return list.toArray(new PsiClass[list.size()]);
    }
  }
}
