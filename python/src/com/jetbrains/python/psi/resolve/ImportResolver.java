package com.jetbrains.python.psi.resolve;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.django.facet.DjangoFacetType;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
* @author yole
*/
public class ImportResolver implements RootVisitor {
  final PsiFile myFootholdFile;
  final boolean myCheckForPackage;
  @Nullable private final Module myModule;
  private final PsiElement myFoothold;
  final @NotNull PyQualifiedName myQualifiedName;
  final @NotNull PsiManager myPsiManager;
  final Set<PsiElement> results = Sets.newLinkedHashSet();
  private boolean myAcceptRootAsTopLevelPackage;

  public ImportResolver(@Nullable Module module,
                        PsiElement foothold, 
                        @NotNull PyQualifiedName qName,
                        @NotNull PsiManager psiManager,
                        boolean checkForPackage) {
    myModule = module;
    myFoothold = foothold;
    myQualifiedName = qName;
    myPsiManager = psiManager;
    myFootholdFile = foothold != null ? foothold.getContainingFile() : null;
    myCheckForPackage = checkForPackage;
    if (module != null && FacetManager.getInstance(module).getFacetByType(DjangoFacetType.ID) != null) {
      myAcceptRootAsTopLevelPackage = true;      
    }
  }
  
  public boolean visitRoot(final VirtualFile root) {
    if (!root.isValid()) {
      return true;
    }
    PsiElement module = resolveInRoot(root, myQualifiedName, myPsiManager, myFootholdFile, myCheckForPackage);
    if (module != null) {
      results.add(module);
    }

    if (myAcceptRootAsTopLevelPackage && myQualifiedName.matchesPrefix(PyQualifiedName.fromDottedString(root.getName()))) {
      module = resolveInRoot(root.getParent(), myQualifiedName, myPsiManager, myFootholdFile, myCheckForPackage);
      if (module != null) {
        results.add(module);
      }
    }

    return true;
  }

  @NotNull
  public List<PsiElement> resultsAsList() {
    return Lists.newArrayList(results);
  }

  public void go() {
    if (myModule != null) {
      RootVisitorHost.visitRoots(myModule, this);      
    }
    else if (myFoothold != null) {
      RootVisitorHost.visitSdkRoots(myFoothold, this);
    }
    else {
      throw new IllegalStateException();
    }
  }

  @Nullable
  protected static PsiElement resolveInRoot(VirtualFile root,
                                            PyQualifiedName qualifiedName,
                                            PsiManager psiManager,
                                            @Nullable PsiFile foothold_file,
                                            boolean checkForPackage) {
    PsiElement module = root.isDirectory() ? psiManager.findDirectory(root) : psiManager.findFile(root);
    if (module == null) return null;
    for (String component : qualifiedName.getComponents()) {
      if (component == null) {
        module = null;
        break;
      }
      module = ResolveImportUtil.resolveChild(module, component, foothold_file, root, true, checkForPackage); // only files, we want a module
    }
    return module;
  }
}
