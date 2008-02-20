/*
 * @author max
 */
package com.intellij.psi.impl;

import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.impl.file.impl.JavaFileManagerImpl;
import com.intellij.psi.impl.migration.PsiMigrationImpl;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.JavaDummyHolder;
import com.intellij.psi.impl.source.JavaDummyHolderFactory;
import com.intellij.psi.impl.source.codeStyle.HelperFactory;
import com.intellij.psi.impl.source.codeStyle.JavaHelperFactory;
import com.intellij.psi.impl.source.javadoc.JavadocManagerImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.resolve.PsiResolveHelperImpl;
import com.intellij.psi.impl.source.tree.JavaChangeUtilSupport;
import com.intellij.psi.javadoc.JavadocManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

public class JavaPsiFacadeImpl extends JavaPsiFacadeEx implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.JavaPsiFacadeImpl");

  private PsiMigrationImpl myCurrentMigration;
  private final PsiElementFinder[] myElementFinders;
  private PsiShortNamesCache myShortNamesCache;
  private final PsiResolveHelper myResolveHelper;
  private final JavadocManager myJavadocManager;
  private final PsiNameHelper myNameHelper;
  private final PsiElementFactory myElementFactory;
  private final PsiConstantEvaluationHelper myConstantEvaluationHelper;
  private final ConcurrentMap<String, PsiPackage> myPackageCache = new ConcurrentHashMap<String, PsiPackage>();
  private final Project myProject;
  private final JavaFileManager myFileManager;
  private final ProgressManager myProgressManager;
  private boolean myDisposed = false;


  public JavaPsiFacadeImpl(Project project,
                           PsiManagerImpl psiManager,
                           final ProjectRootManagerEx projectRootManagerEx,
                           StartupManager startupManager,
                           PsiManagerConfiguration psiManagerConfiguration,
                           MessageBus bus

  ) {
    myProject = project;
    myResolveHelper = new PsiResolveHelperImpl(PsiManager.getInstance(project));
    myJavadocManager = new JavadocManagerImpl();
    myNameHelper = new PsiNameHelperImpl(this);
    myConstantEvaluationHelper = new PsiConstantEvaluationHelperImpl();
    myElementFactory = new PsiElementFactoryImpl(psiManager);

    List<PsiElementFinder> elementFinders = new ArrayList<PsiElementFinder>();
    elementFinders.addAll(Arrays.asList(myProject.getComponents(PsiElementFinder.class)));
    elementFinders.add(new PsiElementFinderImpl()); //this finder should be added at end for Fabrique's needs
    myElementFinders = elementFinders.toArray(new PsiElementFinder[elementFinders.size()]);


    boolean isProjectDefault = project.isDefault();
    if (psiManagerConfiguration.REPOSITORY_ENABLED && !isProjectDefault) {
      myShortNamesCache = new PsiShortNamesCacheImpl((PsiManagerEx)PsiManager.getInstance(project));
    }
    else {
      myShortNamesCache = new EmptyShortNamesCacheImpl();
    }

    myFileManager = new JavaFileManagerImpl(psiManager, projectRootManagerEx, psiManager.getFileManager(), bus);
    myProgressManager = ProgressManager.getInstance();

    psiManager.registerRunnableToRunOnChange(new Runnable() {
      public void run() {
        myPackageCache.clear();
      }
    });

    PsiModificationTrackerImpl modificationTracker = (PsiModificationTrackerImpl) psiManager.getModificationTracker();
    psiManager.addTreeChangePreprocessor(new JavaCodeBlockModificationListener(modificationTracker));

    startupManager.registerStartupActivity(
      new Runnable() {
        public void run() {
          runStartupActivity();
        }
      }
    );


    JavaFindUsagesHandler.setupForProject(project);
    JavaChangeUtilSupport.setup();
    DummyHolderFactory.setFactory(new JavaDummyHolderFactory());
    HelperFactory.setFactory(new JavaHelperFactory());

    Disposer.register(project, this);
  }

  private void runStartupActivity() {
    myFileManager.initialize();
    myShortNamesCache.runStartupActivity();
  }

  public void dispose() {
    myDisposed = true;
    myFileManager.dispose();
  }

  /**
   * @deprecated
   */
  public PsiClass findClass(@NotNull String qualifiedName) {
    return findClass(qualifiedName, GlobalSearchScope.allScope(myProject));
  }

  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    myProgressManager.checkCanceled(); // We hope this method is being called often enough to cancel daemon processes smoothly

    for (PsiElementFinder finder : myElementFinders) {
      PsiClass aClass = finder.findClass(qualifiedName, scope);
      if (aClass != null) return aClass;
    }

    return null;
  }

  @NotNull
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    List<PsiClass> classes = new SmartList<PsiClass>();
    for (PsiElementFinder finder : myElementFinders) {
      PsiClass[] finderClasses = finder.findClasses(qualifiedName, scope);
      classes.addAll(Arrays.asList(finderClasses));
    }

    return classes.toArray(new PsiClass[classes.size()]);
  }

  @NotNull
  public PsiConstantEvaluationHelper getConstantEvaluationHelper() {
    return myConstantEvaluationHelper;
  }

  public PsiPackage findPackage(@NotNull String qualifiedName) {
    PsiPackage aPackage = myPackageCache.get(qualifiedName);
    if (aPackage == null) {
      for (PsiElementFinder finder : myElementFinders) {
        aPackage = finder.findPackage(qualifiedName);
        if (aPackage != null) {
          aPackage = ConcurrencyUtil.cacheOrGet(myPackageCache, qualifiedName, aPackage);
          break;
        }
      }
    }

    return aPackage;
  }


  public PsiMigrationImpl getCurrentMigration() {
    return myCurrentMigration;
  }

  @NotNull
  public PsiJavaParserFacade getParserFacade() {
    return getElementFactory(); // TODO: ligter implementation which doesn't mark all the elements as generated.
  }

  @NotNull
  public PsiResolveHelper getResolveHelper() {
    return myResolveHelper;
  }

  @NotNull
  public PsiShortNamesCache getShortNamesCache() {
    return myShortNamesCache;
  }

  public void registerShortNamesCache(@NotNull PsiShortNamesCache cache) {
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
    myCurrentMigration = new PsiMigrationImpl(this, (PsiManagerImpl)PsiManager.getInstance(myProject));
    return myCurrentMigration;
  }

  @NotNull
  public JavadocManager getJavadocManager() {
    return myJavadocManager;
  }

  @NotNull
  public PsiNameHelper getNameHelper() {
    return myNameHelper;
  }

  public PsiClass[] getClasses(PsiPackageImpl psiPackage, GlobalSearchScope scope) {
    List<PsiClass> result = new ArrayList<PsiClass>();
    for (PsiElementFinder finder : myElementFinders) {
      PsiClass[] classes = finder.getClasses(psiPackage, scope);
      result.addAll(Arrays.asList(classes));
    }

    return result.toArray(new PsiClass[result.size()]);
  }

  public PsiPackage[] getSubPackages(PsiPackageImpl psiPackage, GlobalSearchScope scope) {
    List<PsiPackage> result = new ArrayList<PsiPackage>();
    for (PsiElementFinder finder : myElementFinders) {
      PsiPackage[] packages = finder.getSubPackages(psiPackage, scope);
      result.addAll(Arrays.asList(packages));
    }

    return result.toArray(new PsiPackage[result.size()]);
  }

  public JavaFileManager getJavaFileManager() {
    return myFileManager;
  }

  private class PsiElementFinderImpl implements PsiElementFinder {
    public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
      PsiClass psiClass = myFileManager.findClass(qualifiedName, scope);

      if (psiClass == null && myCurrentMigration != null) {
        psiClass = myCurrentMigration.getMigrationClass(qualifiedName);
      }

      return psiClass;
    }

    @NotNull
    public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
      final PsiClass[] classes = myFileManager.findClasses(qualifiedName, scope);
      if (classes.length == 0 && myCurrentMigration != null) {
        final PsiClass migrationClass = myCurrentMigration.getMigrationClass(qualifiedName);
        if (migrationClass != null) {
          return new PsiClass[]{migrationClass};
        }
      }
      return classes;
    }

    public PsiPackage findPackage(@NotNull String qualifiedName) {
      final PsiPackage aPackage = myFileManager.findPackage(qualifiedName);
      if (aPackage == null && myCurrentMigration != null) {
        final PsiPackage migrationPackage = myCurrentMigration.getMigrationPackage(qualifiedName);
        if (migrationPackage != null) return migrationPackage;
      }

      return aPackage;
    }

    @NotNull
    public PsiPackage[] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
      final Map<String, PsiPackage> packagesMap = new HashMap<String, PsiPackage>();
      final String qualifiedName = psiPackage.getQualifiedName();
      final PsiDirectory[] dirs = psiPackage.getDirectories(scope);
      for (PsiDirectory dir : dirs) {
        PsiDirectory[] subdirs = dir.getSubdirectories();
        for (PsiDirectory subdir : subdirs) {
          final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(subdir);
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
    public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
      ArrayList<PsiClass> list = new ArrayList<PsiClass>();
      final PsiDirectory[] dirs = psiPackage.getDirectories(scope);
      for (PsiDirectory dir : dirs) {
        PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(dir);
        list.addAll(Arrays.asList(classes));
      }
      return list.toArray(new PsiClass[list.size()]);
    }
  }

  public void migrationModified(boolean terminated) {
    if (terminated) {
      myCurrentMigration = null;
    }

    ((PsiManagerEx)PsiManager.getInstance(myProject)).physicalChange();
  }


  public boolean isPartOfPackagePrefix(String packageName) {
    final Collection<String> packagePrefixes = myFileManager.getNonTrivialPackagePrefixes();
    for (final String subpackageName : packagePrefixes) {
      if (isSubpackageOf(subpackageName, packageName)) return true;
    }
    return false;
  }

  private static boolean isSubpackageOf(final String subpackageName, String packageName) {
    return subpackageName.equals(packageName) ||
           subpackageName.startsWith(packageName) && subpackageName.charAt(packageName.length()) == '.';
  }

  public boolean isInPackage(@NotNull PsiElement element, @NotNull PsiPackage aPackage) {
    final PsiFile file = FileContextUtil.getContextFile(element);
    if (file instanceof JavaDummyHolder) {
      return ((JavaDummyHolder) file).isInPackage(aPackage);
    }
    if (file instanceof PsiJavaFile) {
      final String packageName = ((PsiJavaFile) file).getPackageName();
      return packageName.equals(aPackage.getQualifiedName());
    }
    return false;
  }

  public boolean arePackagesTheSame(@NotNull PsiElement element1, @NotNull PsiElement element2) {
    PsiFile file1 = FileContextUtil.getContextFile(element1);
    PsiFile file2 = FileContextUtil.getContextFile(element2);
    if (Comparing.equal(file1, file2)) return true;
    if (file1 instanceof JavaDummyHolder && file2 instanceof JavaDummyHolder) return true;
    if (file1 instanceof JavaDummyHolder || file2 instanceof JavaDummyHolder) {
      JavaDummyHolder dummyHolder = (JavaDummyHolder) (file1 instanceof JavaDummyHolder ? file1 : file2);
      PsiElement other = file1 instanceof JavaDummyHolder ? file2 : file1;
      return dummyHolder.isSamePackage(other);
    }
    if (!(file1 instanceof PsiClassOwner)) return false;
    if (!(file2 instanceof PsiClassOwner)) return false;
    String package1 = ((PsiClassOwner) file1).getPackageName();
    String package2 = ((PsiClassOwner) file2).getPackageName();
    return Comparing.equal(package1, package2);
  }

  @NotNull
  public PsiFile[] findFormsBoundToClass(String className) {
    if (className == null) return PsiFile.EMPTY_ARRAY;
    PsiManagerEx myManager = (PsiManagerEx)PsiManager.getInstance(myProject);
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myManager.getProject());
    PsiFile[] files = myManager.getCacheManager().getFilesWithWord(className, UsageSearchContext.IN_FOREIGN_LANGUAGES, projectScope, true);
    if (files.length == 0) return PsiFile.EMPTY_ARRAY;
    List<PsiFile> boundForms = new ArrayList<PsiFile>(files.length);
    for (PsiFile psiFile : files) {
      if (psiFile.getFileType() != StdFileTypes.GUI_DESIGNER_FORM) continue;

      String text = psiFile.getText();
      try {
        String boundClass = Utils.getBoundClassName(text);
        if (className.equals(boundClass)) boundForms.add(psiFile);
      }
      catch (Exception e) {
        LOG.debug(e);
      }
    }

    return boundForms.toArray(new PsiFile[boundForms.size()]);
  }

  public boolean isFieldBoundToForm(@NotNull PsiField field) {
    PsiClass aClass = field.getContainingClass();
    if (aClass != null && aClass.getQualifiedName() != null) {
      PsiFile[] formFiles = findFormsBoundToClass(aClass.getQualifiedName());
      for (PsiFile file : formFiles) {
        final PsiReference[] references = file.getReferences();
        for (final PsiReference reference : references) {
          if (reference.isReferenceTo(field)) return true;
        }
      }
    }

    return false;
  }

  public Project getProject() {
    return myProject;
  }

  @NotNull
  public PsiElementFactory getElementFactory() {
    return myElementFactory;
  }

  public void setAssertOnFileLoadingFilter(final VirtualFileFilter filter) {
    ((PsiManagerImpl)PsiManager.getInstance(myProject)).setAssertOnFileLoadingFilter(filter);
  }

  private static class JavaCodeBlockModificationListener implements PsiTreeChangePreprocessor {
    private PsiModificationTrackerImpl myModificationTracker;

    private JavaCodeBlockModificationListener(final PsiModificationTrackerImpl modificationTracker) {
      myModificationTracker = modificationTracker;
    }

    public void treeChanged(final PsiTreeChangeEventImpl event) {
      boolean changedInsideCodeBlock = false;

      switch (event.getCode()) {
        case PsiManagerImpl.BEFORE_CHILDREN_CHANGE:
          if (event.getParent() instanceof PsiFile) {
            changedInsideCodeBlock = true;
            break; // May be caused by fake PSI event from PomTransaction. A real event will anyway follow.
          }

        case PsiManagerImpl.CHILDREN_CHANGED :
          changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
        break;

        case PsiManagerImpl.BEFORE_CHILD_ADDITION:
        case PsiManagerImpl.BEFORE_CHILD_REMOVAL:
        case PsiManagerImpl.CHILD_ADDED :
        case PsiManagerImpl.CHILD_REMOVED :
          changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
        break;

        case PsiManagerImpl.BEFORE_PROPERTY_CHANGE:
        case PsiManagerImpl.PROPERTY_CHANGED :
          changedInsideCodeBlock = false;
        break;

        case PsiManagerImpl.BEFORE_CHILD_REPLACEMENT:
        case PsiManagerImpl.CHILD_REPLACED :
          changedInsideCodeBlock = isInsideCodeBlock(event.getParent());
        break;

        case PsiManagerImpl.BEFORE_CHILD_MOVEMENT:
        case PsiManagerImpl.CHILD_MOVED :
          changedInsideCodeBlock = isInsideCodeBlock(event.getOldParent()) && isInsideCodeBlock(event.getNewParent());
        break;

        default:
          LOG.error("Unknown code:" + event.getCode());
      }

      if (!changedInsideCodeBlock) {
        myModificationTracker.incOutOfCodeBlockModificationCounter();
      }
    }

    private static boolean isInsideCodeBlock(PsiElement element) {
      if (element == null || element.getParent() == null) return true;
      while(true){
        if (element instanceof PsiFile || element instanceof PsiDirectory || element == null){
          return false;
        }
        PsiElement pparent = element.getParent();
        if (element instanceof PsiClass) return false; // anonymous or local class
        if (element instanceof PsiCodeBlock){
          if (pparent instanceof PsiMethod || pparent instanceof PsiClassInitializer){
            return true;
          }
        }
        element = pparent;
      }
    }

  }
}