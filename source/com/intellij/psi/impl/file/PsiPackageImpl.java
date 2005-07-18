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
import com.intellij.openapi.util.Key;
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
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PsiPackageImpl extends PsiElementBase implements PsiPackage {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiPackageImpl");

  private final PsiManagerImpl myManager;
  private final String myQualifiedName;

  public PsiPackageImpl(PsiManagerImpl manager, String qualifiedName) {
    myManager = manager;
    myQualifiedName = qualifiedName;
  }

  public boolean equals(Object o) {
    if (o instanceof PsiPackageImpl) {
      return myManager == ((PsiPackageImpl)o).myManager && myQualifiedName.equals(((PsiPackageImpl)o).myQualifiedName);
    }
    return false;
  }

  public int hashCode() {
    return myQualifiedName.hashCode();
  }

  public String getQualifiedName() {
    return myQualifiedName;
  }

  public PsiDirectory[] getDirectories() {
    return getDirectories(GlobalSearchScope.allScope(myManager.getProject()));
  }

  public PsiDirectory[] getDirectories(GlobalSearchScope scope) {
    FileIndex projectFileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
    VirtualFile[] dirs = projectFileIndex.getDirectoriesByPackageName(myQualifiedName, false);
    ArrayList<PsiDirectory> list = new ArrayList<PsiDirectory>();
    for (VirtualFile dir : dirs) {
      if (!scope.contains(dir)) continue;
      PsiDirectory psiDir = myManager.findDirectory(dir);
      LOG.assertTrue(psiDir != null);
      list.add(psiDir);
    }
    return list.toArray(new PsiDirectory[list.size()]);
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

  public PsiElement setName(String name) throws IncorrectOperationException {
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

  public Language getLanguage() {
    return StdFileTypes.JAVA.getLanguage();
  }

  public PsiManager getManager() {
    return myManager;
  }

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
    return new char[0]; // TODO throw new InsupportedOperationException()
  }

  public boolean textMatches(CharSequence text) {
    return false;
  }

  public boolean textMatches(PsiElement element) {
    return false;
  }

  public PsiElement copy() {
    LOG.error("method not implemented");
    return null;
  }

  public PsiElement add(PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addBefore(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAdd(PsiElement element) throws IncorrectOperationException {
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

  public PsiElement replace(PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public boolean isValid() {
    return getDirectories().length > 0;
  }

  public boolean isWritable() {
    PsiDirectory[] dirs = getDirectories();
    for (PsiDirectory dir : dirs) {
      if (!dir.isWritable()) return false;
    }
    return true;
  }

  public <T> T getUserData(Key<T> key) {
    return null;
  }

  public <T> void putUserData(Key<T> key, T value) {
    throw new RuntimeException("PsiPackage is not peresisitent. Cannot store user data in it.");
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitPackage(this);
  }

  public String toString() {
    return "PsiPackage:" + getQualifiedName();
  }

  public PsiClass[] getClasses() {
    return getClasses(GlobalSearchScope.allScope(myManager.getProject()));
  }

  public PsiClass[] getClasses(GlobalSearchScope scope) {
    return myManager.getClasses(this, scope);
  }

  public PsiPackage[] getSubPackages() {
    return getSubPackages(GlobalSearchScope.allScope(myManager.getProject()));
  }

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
      NameHint nameHint = processor.getHint(NameHint.class);
      if (nameHint != null) {
        final PsiClass aClass = findClassByName(nameHint.getName(), scope);
        if (aClass != null) {
          if (!processor.execute(aClass, substitutor)) return false;
        }
      }
      else {
        PsiClass[] classes = getClasses(scope);
        for (PsiClass aClass : classes) {
          if (!processor.execute(aClass, substitutor)) {
            return false;
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

  public void navigate(boolean requestFocus) {
    final ProjectView projectView = ProjectView.getInstance(getProject());
    projectView.changeView(PackageViewPane.ID);
    final PsiDirectory[] directories = getDirectories();
    final VirtualFile firstDir = directories[0].getVirtualFile();
    final boolean isLibraryRoot = PackageUtil.isLibraryRoot(firstDir, getProject());

    final Module module = ProjectRootManager.getInstance(getProject()).getFileIndex().getModuleForFile(firstDir);
    final PackageElement packageElement = new PackageElement(module,
                                                             this,
                                                             isLibraryRoot);
    projectView.getProjectViewPaneById(PackageViewPane.ID).select(packageElement, firstDir, requestFocus);
    ToolWindowManager.getInstance(getProject()).getToolWindow(ToolWindowId.PROJECT_VIEW).activate(null);
  }

  public boolean isPhysical() {
    return true;
  }

  public ASTNode getNode() {
    return null;
  }
}