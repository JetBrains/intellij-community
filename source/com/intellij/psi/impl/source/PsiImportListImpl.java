package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.cache.FileView;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Map;

public class PsiImportListImpl extends SlaveRepositoryPsiElement implements PsiImportList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiImportListImpl");
  private volatile PsiImportStatementBaseImpl[] myRepositoryImports = null;
  private volatile PsiImportStatementImpl[] myRepositoryClassImports = null;
  private volatile PsiImportStaticStatementImpl[] myRepositoryStaticImports = null;

  private volatile Map<String,PsiImportStatement> myClassNameToImportMap = null;
  private volatile Map<String,PsiImportStatement> myPackageNameToImportMap = null;
  private volatile Map<String,PsiImportStatementBase> myNameToSingleImportMap = null;

  private static final PsiElementArrayConstructor IMPORT_STATEMENT_BASE_IMPL_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return new PsiImportStatementBaseImpl[length];
    }
  };

  public PsiImportListImpl(PsiManagerEx manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public PsiImportListImpl(PsiManagerEx manager, SrcRepositoryPsiElement owner) {
    super(manager, owner);
  }

  protected Object clone() {
    PsiImportListImpl clone = (PsiImportListImpl)super.clone();
    clone.myRepositoryImports = null;
    clone.myRepositoryClassImports = null;
    clone.myRepositoryStaticImports = null;
    clone.myClassNameToImportMap = null;
    clone.myPackageNameToImportMap = null;
    clone.myNameToSingleImportMap = null;
    return clone;
  }

  public void setOwner(SrcRepositoryPsiElement owner) {
    super.setOwner(owner);

    if (myOwner == null) {
      if (myRepositoryImports != null) {
        for (int i = 0; i < myRepositoryImports.length; i++) {
          PsiImportStatementBaseImpl anImport = myRepositoryImports[i];
          anImport.setOwnerAndIndex(this, i);
        }
      }
      myRepositoryImports = null;
      myRepositoryClassImports = null;
      myRepositoryStaticImports = null;
    }
    else {
      myRepositoryImports = (PsiImportStatementBaseImpl[])bindIndexedSlaves(IMPORT_STATEMENT_BASE_BIT_SET,
                                                                            IMPORT_STATEMENT_BASE_IMPL_ARRAY_CONSTRUCTOR);
      myRepositoryClassImports = null;
      myRepositoryStaticImports = null;
    }
  }

  public void subtreeChanged() {
    myClassNameToImportMap = null;
    myPackageNameToImportMap = null;
    myNameToSingleImportMap = null;
    super.subtreeChanged();
  }

  @NotNull
  public PsiImportStatement[] getImportStatements() {
    if (myOwner == null) {
      return calcTreeElement().getChildrenAsPsiElements(IMPORT_STATEMENT_BIT_SET, PSI_IMPORT_STATEMENT_ARRAY_CONSTRUCTOR);
    }
    else {
      PsiImportStatementImpl[] repositoryClassImports = myRepositoryClassImports;
      if (repositoryClassImports == null) {
        PsiImportStatementBaseImpl[] repositoryImports = calcRepositoryImports();
        final ArrayList<PsiImportStatementImpl> importStatements = new ArrayList<PsiImportStatementImpl>();
        for (PsiImportStatementBaseImpl repositoryImport : repositoryImports) {
          if (repositoryImport instanceof PsiImportStatementImpl) {
            importStatements.add((PsiImportStatementImpl)repositoryImport);
          }
        }
        repositoryClassImports = myRepositoryClassImports = importStatements.isEmpty() ? PsiImportStatementImpl.EMPTY_ARRAY : importStatements.toArray(new PsiImportStatementImpl[importStatements.size()]);
      }
      return repositoryClassImports;
    }
  }

  @NotNull
  public PsiImportStaticStatement[] getImportStaticStatements() {
    if (myOwner == null) {
      return calcTreeElement().getChildrenAsPsiElements(IMPORT_STATIC_STATEMENT_BIT_SET, PSI_IMPORT_STATIC_STATEMENT_ARRAY_CONSTRUCTOR);
    }
    else {
      PsiImportStaticStatementImpl[] repositoryStaticImports = myRepositoryStaticImports;
      if (repositoryStaticImports == null) {
        PsiImportStatementBaseImpl[] repositoryImports = calcRepositoryImports();
        final ArrayList<PsiImportStaticStatementImpl> importStatements = new ArrayList<PsiImportStaticStatementImpl>();
        for (PsiImportStatementBaseImpl repositoryImport : repositoryImports) {
          if (repositoryImport instanceof PsiImportStaticStatementImpl) {
            importStatements.add((PsiImportStaticStatementImpl)repositoryImport);
          }
        }
        repositoryStaticImports = myRepositoryStaticImports = importStatements.isEmpty() ? PsiImportStaticStatementImpl.EMPTY_ARRAY : importStatements.toArray(new PsiImportStaticStatementImpl[importStatements.size()]);
      }
      return repositoryStaticImports;
    }

  }

  @NotNull
  public PsiImportStatementBase[] getAllImportStatements() {
    if (myOwner != null) {
      return calcRepositoryImports();
    }
    else {
      return calcTreeElement().getChildrenAsPsiElements(IMPORT_STATEMENT_BASE_BIT_SET, PSI_IMPORT_STATEMENT_BASE_ARRAY_CONSTRUCTOR);
    }
  }

  private PsiImportStatementBaseImpl[] calcRepositoryImports() {
    PsiImportStatementBaseImpl[] repositoryImports = myRepositoryImports;
    if (repositoryImports != null) return repositoryImports;
    ASTNode treeElement = getTreeElement();
    if (treeElement == null) {
      final FileView fileView = getRepositoryManager().getFileView();
      final long repositoryId = getRepositoryId();
      int count = fileView.getImportStatementsCount(repositoryId);
      repositoryImports = count == 0 ? PsiImportStatementBaseImpl.EMPTY_ARRAY : new PsiImportStatementBaseImpl[count];
      for (int i = 0; i < repositoryImports.length; i++) {
        if (fileView.isImportStatic(repositoryId, i)) {
          repositoryImports[i] = new PsiImportStaticStatementImpl(myManager, this, i);
        }
        else {
          repositoryImports[i] = new PsiImportStatementImpl(myManager, this, i);
        }
      }
    }
    else {
      final ASTNode[] imports = treeElement.getChildren(IMPORT_STATEMENT_BASE_BIT_SET);
      int count = imports.length;
      repositoryImports = count == 0 ? PsiImportStatementBaseImpl.EMPTY_ARRAY : new PsiImportStatementBaseImpl[count];
      for (int i = 0; i < repositoryImports.length; i++) {
        final IElementType type = imports[i].getElementType();
        if (type == IMPORT_STATEMENT) {
          repositoryImports[i] = new PsiImportStatementImpl(myManager, this, i);
        }
        else if (imports[i].getElementType() == IMPORT_STATIC_STATEMENT) {
          repositoryImports[i] = new PsiImportStaticStatementImpl(myManager, this, i);
        }
        else {
          LOG.assertTrue(false, "Unknown child: " + type.toString() + " " + type);
        }
      }
    }
    return myRepositoryImports = repositoryImports;
  }

  public PsiImportStatement findSingleClassImportStatement(String name) {
    for (;;) {
      Map<String, PsiImportStatement> map = myClassNameToImportMap;
      if (map == null) {
        initializeMaps();
      }
      else {
        return map.get(name);
      }
    }
  }

  public PsiImportStatement findOnDemandImportStatement(String name) {
    for (;;) {
      Map<String, PsiImportStatement> map = myPackageNameToImportMap;
      if (map == null) {
        initializeMaps();
      }
      else {
        return map.get(name);
      }
    }
  }

  public PsiImportStatementBase findSingleImportStatement(String name) {
    for (;;) {
      Map<String, PsiImportStatementBase> map = myNameToSingleImportMap;
      if (map == null) {
        initializeMaps();
      }
      else {
        return map.get(name);
      }
    }
  }

  public boolean isReplaceEquivalent(PsiImportList otherList) {
    return getText().equals(otherList.getText());
  }

  private void initializeMaps() {
    Map<String, PsiImportStatement> classNameToImportMap = new HashMap<String, PsiImportStatement>();
    Map<String, PsiImportStatement> packageNameToImportMap = new HashMap<String, PsiImportStatement>();
    Map<String, PsiImportStatementBase> nameToSingleImportMap = new HashMap<String, PsiImportStatementBase>();
    PsiImportStatement[] imports = getImportStatements();
    for (PsiImportStatement anImport : imports) {
      String qName = anImport.getQualifiedName();
      if (qName == null) continue;
      if (anImport.isOnDemand()) {
        packageNameToImportMap.put(qName, anImport);
      }
      else {
        classNameToImportMap.put(qName, anImport);
        PsiJavaCodeReferenceElement importReference = anImport.getImportReference();
        if (importReference == null) continue;
        nameToSingleImportMap.put(importReference.getReferenceName(), anImport);
      }
    }

    PsiImportStaticStatement[] importStatics = getImportStaticStatements();
    for (PsiImportStaticStatement importStatic : importStatics) {
      if (!importStatic.isOnDemand()) {
        String referenceName = importStatic.getReferenceName();
        if (referenceName != null) {
          nameToSingleImportMap.put(referenceName, importStatic);
        }
      }
    }

    myClassNameToImportMap = classNameToImportMap;
    myPackageNameToImportMap = packageNameToImportMap;
    myNameToSingleImportMap = nameToSingleImportMap;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitImportList(this);
  }

  public String toString() {
    return "PsiImportList";
  }
}
