package com.intellij.psi.impl.file;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.PackageViewPane;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PsiPackageImpl extends PsiElementBase implements PsiPackage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiPackageImpl");

  private final PsiManagerImpl myManager;
  private final String myQualifiedName;
  private CachedValue<Ref<PsiModifierList>> myAnnotationList;

  public PsiPackageImpl(PsiManagerImpl manager, String qualifiedName) {
    myManager = manager;
    myQualifiedName = qualifiedName;
  }

  public boolean equals(Object o) {
    return o instanceof PsiPackageImpl
           && myManager == ((PsiPackageImpl)o).myManager
           && myQualifiedName.equals(((PsiPackageImpl)o).myQualifiedName);
  }

  public int hashCode() {
    return myQualifiedName.hashCode();
  }

  @NotNull
  public String getQualifiedName() {
    return myQualifiedName;
  }

  @NotNull
  public PsiDirectory[] getDirectories() {
    return getDirectories(GlobalSearchScope.allScope(myManager.getProject()));
  }

  @NotNull
  public PsiDirectory[] getDirectories(GlobalSearchScope scope) {
    return new DirectoriesSearch().search(scope).toArray(PsiDirectory.EMPTY_ARRAY);
  }

  private class DirectoriesSearch extends QueryFactory<PsiDirectory, GlobalSearchScope> {
    public DirectoriesSearch() {
      registerExecutor(new QueryExecutor<PsiDirectory, GlobalSearchScope>() {
        public boolean execute(final GlobalSearchScope scope, final Processor<PsiDirectory> consumer) {
          FileIndex projectFileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
          projectFileIndex.getDirsByPackageName(myQualifiedName, false).forEach(new Processor<VirtualFile>() {
            public boolean process(final VirtualFile dir) {
              if (!scope.contains(dir)) return true;
              PsiDirectory psiDir = myManager.findDirectory(dir);
              assert psiDir != null;
              return consumer.process(psiDir);
            }
          });
          return true;
        }
      });
    }

    public Query<PsiDirectory> search(GlobalSearchScope scope) {
      return createQuery(scope);
    }
  }

  public String getName() {
    if (DebugUtil.CHECK_INSIDE_ATOMIC_ACTION_ENABLED) {
      ApplicationManager.getApplication().assertReadAccessAllowed();
    }
    if (myQualifiedName.length() == 0) return null;
    int index = myQualifiedName.lastIndexOf('.');
    if (index < 0) {
      return myQualifiedName;
    }
    else {
      return myQualifiedName.substring(index + 1);
    }
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      dir.setName(name);
    }
    return this;
  }

  public void checkSetName(String name) throws IncorrectOperationException {
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      dir.checkSetName(name);
    }
  }

  public void handleQualifiedNameChange(final String newQualifiedName) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final String oldQualifedName = myQualifiedName;
    final boolean anyChanged = changePackagePrefixes(oldQualifedName, newQualifiedName);
    if (anyChanged) {
      UndoManager.getInstance(myManager.getProject()).undoableActionPerformed(new UndoableAction() {
        public void undo() {
          changePackagePrefixes(newQualifiedName, oldQualifedName);
        }

        public void redo() {
          changePackagePrefixes(oldQualifedName, newQualifiedName);
        }

        public DocumentReference[] getAffectedDocuments() {
          return new DocumentReference[0];
        }

        public boolean isComplex() {
          return true;
        }
      });
    }
  }

  private boolean changePackagePrefixes(final String oldQualifiedName, final String newQualifiedName) {
    final Module[] modules = ModuleManager.getInstance(myManager.getProject()).getModules();
    List<ModifiableRootModel> modelsToCommit = new ArrayList<ModifiableRootModel>();
    for (final Module module : modules) {
      boolean anyChange = false;
      final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
      final ContentEntry[] contentEntries = rootModel.getContentEntries();
      for (final ContentEntry contentEntry : contentEntries) {
        final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        for (final SourceFolder sourceFolder : sourceFolders) {
          final String packagePrefix = sourceFolder.getPackagePrefix();
          if (packagePrefix.startsWith(oldQualifiedName)) {
            sourceFolder.setPackagePrefix(newQualifiedName + packagePrefix.substring(oldQualifiedName.length()));
            anyChange = true;
          }
        }
      }
      if (anyChange) {
        modelsToCommit.add(rootModel);
      }
    }

    if (!modelsToCommit.isEmpty()) {
      ProjectRootManager.getInstance(myManager.getProject()).multiCommit(
        modelsToCommit.toArray(new ModifiableRootModel[modelsToCommit.size()])
      );
      return true;
    } else {
      return false;
    }
  }

  public VirtualFile[] occursInPackagePrefixes() {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    final Module[] modules = ModuleManager.getInstance(myManager.getProject()).getModules();

    for (final Module module : modules) {
      final ContentEntry[] contentEntries = ModuleRootManager.getInstance(module).getContentEntries();
      for (final ContentEntry contentEntry : contentEntries) {
        final SourceFolder[] sourceFolders = contentEntry.getSourceFolders();
        for (final SourceFolder sourceFolder : sourceFolders) {
          final String packagePrefix = sourceFolder.getPackagePrefix();
          if (packagePrefix.startsWith(myQualifiedName)) {
            final VirtualFile file = sourceFolder.getFile();
            if (file != null) {
              result.add(file);
            }
          }
        }
      }
    }

    return result.toArray(new VirtualFile[result.size()]);
  }

  public PsiPackage getParentPackage() {
    if (myQualifiedName.length() == 0) return null;
    int lastDot = myQualifiedName.lastIndexOf('.');
    if (lastDot < 0) {
      return new PsiPackageImpl(myManager, "");
    }
    else {
      return new PsiPackageImpl(myManager, myQualifiedName.substring(0, lastDot));
    }
  }

  @NotNull
  public Language getLanguage() {
    return StdFileTypes.JAVA.getLanguage();
  }

  public PsiManager getManager() {
    return myManager;
  }

  @NotNull
  public PsiElement[] getChildren() {
    LOG.error("method not implemented");
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiElement getParent() {
    return getParentPackage();
  }

  public PsiFile getContainingFile() {
    return null;
  }

  public TextRange getTextRange() {
    return null;
  }

  public int getStartOffsetInParent() {
    return -1;
  }

  public int getTextLength() {
    return -1;
  }

  public PsiElement findElementAt(int offset) {
    return null;
  }

  public int getTextOffset() {
    return -1;
  }

  public String getText() {
    return null;
  }

  @NotNull
  public char[] textToCharArray() {
    return ArrayUtil.EMPTY_CHAR_ARRAY; // TODO throw new InsupportedOperationException()
  }

  public boolean textMatches(@NotNull CharSequence text) {
    return false;
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  public PsiElement copy() {
    LOG.error("method not implemented");
    return null;
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void delete() throws IncorrectOperationException {
    checkDelete();
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      dir.delete();
    }
  }

  public void checkDelete() throws IncorrectOperationException {
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      dir.checkDelete();
    }
  }

  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public boolean isValid() {
    return new DirectoriesSearch().search(GlobalSearchScope.allScope(getProject())).findFirst() != null;
  }

  public boolean isWritable() {
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      if (!dir.isWritable()) return false;
    }
    return true;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitPackage(this);
  }

  public String toString() {
    return "PsiPackage:" + getQualifiedName();
  }

  @NotNull
  public PsiClass[] getClasses() {
    return getClasses(GlobalSearchScope.allScope(myManager.getProject()));
  }

  @NotNull
  public PsiClass[] getClasses(GlobalSearchScope scope) {
    return myManager.getClasses(this, scope);
  }

  @Nullable
  public PsiModifierList getAnnotationList() {
    if (myAnnotationList == null) {
      myAnnotationList = myManager.getCachedValuesManager().createCachedValue(new PackageAnnotationValueProvider());
    }
    return myAnnotationList.getValue().get();
  }

  @NotNull
  public PsiPackage[] getSubPackages() {
    return getSubPackages(GlobalSearchScope.allScope(myManager.getProject()));
  }

  @NotNull
  public PsiPackage[] getSubPackages(GlobalSearchScope scope) {
    return myManager.getSubPackages(this, scope);
  }

  private PsiClass findClassByName(String name, GlobalSearchScope scope) {
    final String qName = getQualifiedName();
    final String classQName = qName.length() > 0 ? qName + "." + name : name;
    return myManager.findClass(classQName, scope);
  }

  private PsiPackage findSubPackageByName(String name, GlobalSearchScope scope) {
    final String qName = getQualifiedName();
    final String subpackageQName = qName.length() > 0 ? qName + "." + name : name;
    PsiPackage aPackage = myManager.findPackage(subpackageQName);
    if (aPackage == null) return null;
    if (aPackage.getDirectories(scope).length == 0) return null;
    return aPackage;
  }

  public boolean processDeclarations(PsiScopeProcessor processor,
                                     PsiSubstitutor substitutor,
                                     PsiElement lastParent,
                                     PsiElement place) {
    GlobalSearchScope scope = place.getResolveScope();

    processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
    ElementClassHint classHint = processor.getHint(ElementClassHint.class);

    if (classHint == null || classHint.shouldProcess(PsiClass.class)) {
      boolean isPlacePhysical = place.isPhysical();
      NameHint nameHint = processor.getHint(NameHint.class);
      if (nameHint != null) {
        final PsiClass aClass = findClassByName(nameHint.getName(), scope);
        if (aClass != null &&
            (!isPlacePhysical || getManager().getResolveHelper().isAccessible(aClass, place, null))) {
          if (!processor.execute(aClass, substitutor)) return false;
        }
      }
      else {
        PsiClass[] classes = getClasses(scope);
        for (PsiClass aClass : classes) {
          if (!isPlacePhysical || getManager().getResolveHelper().isAccessible(aClass, place, null)) {
            if (!processor.execute(aClass, substitutor)) {
              return false;
            }
          }
        }
        if (myManager.getCurrentMigration() != null) {
          final Iterator<PsiClass> migrationClasses = myManager.getCurrentMigration().getMigrationClasses(getQualifiedName());
          while (migrationClasses.hasNext()) {
            PsiClass psiClass = migrationClasses.next();
            if (!processor.execute(psiClass, substitutor)) {
              return false;
            }
          }
        }
      }
    }
    if (classHint == null || classHint.shouldProcess(PsiPackage.class)) {
      NameHint nameHint = processor.getHint(NameHint.class);
      if (nameHint != null) {
        PsiPackage aPackage = findSubPackageByName(nameHint.getName(), scope);
        if (aPackage != null) {
          if (!processor.execute(aPackage, substitutor)) return false;
        }
      }
      else {
        PsiPackage[] packs = getSubPackages(scope);
        for (PsiPackage pack : packs) {
          if (!processor.execute(pack, substitutor)) {
            return false;
          }
        }
        if (myManager.getCurrentMigration() != null) {
          final Iterator<PsiPackage> migrationClasses = myManager.getCurrentMigration().getMigrationPackages(getQualifiedName());
          while (migrationClasses.hasNext()) {
            PsiPackage psiPackage = migrationClasses.next();
            if (!processor.execute(psiPackage, substitutor)) {
              return false;
            }
          }
        }
      }
    }
    return true;
  }

  public boolean canNavigate() {
    return isValid();
  }

  public boolean canNavigateToSource() {
    return false;
  }

  public void navigate(boolean requestFocus) {
    final ProjectView projectView = ProjectView.getInstance(getProject());
    projectView.changeView(PackageViewPane.ID);
    final PsiDirectory[] directories = getDirectories();
    final VirtualFile firstDir = directories[0].getVirtualFile();
    final boolean isLibraryRoot = PackageUtil.isLibraryRoot(firstDir, getProject());

    final Module module = ProjectRootManager.getInstance(getProject()).getFileIndex().getModuleForFile(firstDir);
    final PackageElement packageElement = new PackageElement(module, this, isLibraryRoot);
    projectView.getProjectViewPaneById(PackageViewPane.ID).select(packageElement, firstDir, requestFocus);
    ToolWindowManager.getInstance(getProject()).getToolWindow(ToolWindowId.PROJECT_VIEW).activate(null);
  }

  public boolean isPhysical() {
    return true;
  }

  public ASTNode getNode() {
    return null;
  }

  private class PackageAnnotationValueProvider implements CachedValueProvider<Ref<PsiModifierList> > {
    @NonNls private static final String PACKAGE_INFO_FILE = "package-info.java";
    private final Object[] OOCB_DEPENDENCY = new Object[] { PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT };

    public Result<Ref<PsiModifierList> > compute() {
      for(PsiDirectory directory: getDirectories()) {
        PsiFile file = directory.findFile(PACKAGE_INFO_FILE);
        if (file != null) {
          PsiPackageStatement stmt = PsiTreeUtil.getChildOfType(file, PsiPackageStatement.class);
          if (stmt != null) {
            return new Result<Ref<PsiModifierList>>(Ref.create(stmt.getAnnotationList()), OOCB_DEPENDENCY );
          }
        }
      }
      return new Result<Ref<PsiModifierList> >(new Ref<PsiModifierList>(), OOCB_DEPENDENCY );
    }
  }
}
