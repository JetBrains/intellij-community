package com.intellij.psi.impl.source;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.cache.FileView;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.RepositoryTreeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;

public class PsiImportListImpl extends SlaveRepositoryPsiElement implements PsiImportList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.PsiImportListImpl");
  private PsiImportStatementBaseImpl[] myRepositoryImports = null;
  private PsiImportStatementImpl[] myRepositoryClassImports = null;
  private PsiImportStaticStatementImpl[] myRepositoryStaticImports = null;

  private HashMap myClassNameToImportMap = null;
  private HashMap myPackageNameToImportMap = null;
  private HashMap myNameToSingleImportMap = null;

  private static final PsiElementArrayConstructor IMPORT_STATEMENT_BASE_IMPL_ARRAY_CONSTRUCTOR = new PsiElementArrayConstructor() {
    public PsiElement[] newPsiElementArray(int length) {
      return new PsiImportStatementBaseImpl[length];
    }
  };

  public PsiImportListImpl(PsiManagerImpl manager, RepositoryTreeElement treeElement) {
    super(manager, treeElement);
  }

  public PsiImportListImpl(PsiManagerImpl manager, SrcRepositoryPsiElement owner) {
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

    if (myOwner == null){
      if (myRepositoryImports != null){
        for(int i = 0; i < myRepositoryImports.length; i++){
          PsiImportStatementBaseImpl anImport = myRepositoryImports[i];
          anImport.setOwnerAndIndex(this, i);
        }
      }
      myRepositoryImports = null;
      myRepositoryClassImports = null;
      myRepositoryStaticImports = null;
    }
    else{
      myRepositoryImports = (PsiImportStatementBaseImpl[])bindIndexedSlaves(IMPORT_STATEMENT_BASE_BIT_SET, IMPORT_STATEMENT_BASE_IMPL_ARRAY_CONSTRUCTOR);
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

  public PsiImportStatement[] getImportStatements(){
    if (myOwner != null){
      if (myRepositoryClassImports == null){
        calcRepositoryImports();
        final ArrayList<PsiImportStatementImpl> importStatements = new ArrayList<PsiImportStatementImpl>();
        for (int i = 0; i < myRepositoryImports.length; i++) {
          PsiImportStatementBaseImpl repositoryImport = myRepositoryImports[i];
          if (repositoryImport instanceof PsiImportStatementImpl) {
            importStatements.add((PsiImportStatementImpl) repositoryImport);
          }
        }
        myRepositoryClassImports = (PsiImportStatementImpl[])importStatements.toArray(new PsiImportStatementImpl[importStatements.size()]);
      }
      return myRepositoryClassImports;
    }
    else{
      return (PsiImportStatement[])calcTreeElement().getChildrenAsPsiElements(IMPORT_STATEMENT_BIT_SET, PSI_IMPORT_STATEMENT_ARRAY_CONSTRUCTOR);
    }
  }

  public PsiImportStaticStatement[] getImportStaticStatements() {
    if (myOwner != null){
      if (myRepositoryStaticImports == null){
        calcRepositoryImports();
        final ArrayList<PsiImportStaticStatementImpl> importStatements = new ArrayList<PsiImportStaticStatementImpl>();
        for (int i = 0; i < myRepositoryImports.length; i++) {
          PsiImportStatementBaseImpl repositoryImport = myRepositoryImports[i];
          if (repositoryImport instanceof PsiImportStaticStatementImpl) {
            importStatements.add((PsiImportStaticStatementImpl) repositoryImport);
          }
        }
        myRepositoryStaticImports = (PsiImportStaticStatementImpl[])importStatements.toArray(new PsiImportStaticStatementImpl[importStatements.size()]);
      }
      return myRepositoryStaticImports;
    }
    else{
      return (PsiImportStaticStatement[])calcTreeElement().getChildrenAsPsiElements(IMPORT_STATIC_STATEMENT_BIT_SET, PSI_IMPORT_STATIC_STATEMENT_ARRAY_CONSTRUCTOR);
    }

  }

  public PsiImportStatementBase[] getAllImportStatements() {
    if (myOwner != null) {
      calcRepositoryImports();
      return myRepositoryImports;
    }
    else {
      return (PsiImportStatementBase[])calcTreeElement().getChildrenAsPsiElements(IMPORT_STATEMENT_BASE_BIT_SET, PSI_IMPORT_STATEMENT_BASE_ARRAY_CONSTRUCTOR);
    }
  }

  private void calcRepositoryImports() {
    if (myRepositoryImports == null){
      CompositeElement treeElement = getTreeElement();
      if (treeElement != null){
        final TreeElement[] imports = treeElement.getChildren(IMPORT_STATEMENT_BASE_BIT_SET);
        int count = imports.length;
        myRepositoryImports = new PsiImportStatementBaseImpl[count];
        for(int i = 0; i < myRepositoryImports.length; i++){
          final IElementType type = imports[i].getElementType();
          if (type == IMPORT_STATEMENT) {
            myRepositoryImports[i] = new PsiImportStatementImpl(myManager, this, i);
          }
          else if (imports[i].getElementType() == IMPORT_STATIC_STATEMENT){
            myRepositoryImports[i] = new PsiImportStaticStatementImpl(myManager, this, i);
          }
          else {
            LOG.assertTrue(false, "Unknown child: " + type.toString() + " " + type);
          }
        }
      }
      else{
        final FileView fileView = getRepositoryManager().getFileView();
        final long repositoryId = getRepositoryId();
        int count = fileView.getImportStatementsCount(repositoryId);
        myRepositoryImports = new PsiImportStatementBaseImpl[count];
        for(int i = 0; i < myRepositoryImports.length; i++){
          if (fileView.isImportStatic(repositoryId, i)) {
            myRepositoryImports[i] = new PsiImportStaticStatementImpl(myManager, this, i);
          }
          else {
            myRepositoryImports[i] = new PsiImportStatementImpl(myManager, this, i);
          }
        }
      }

    }
  }

  public PsiImportStatement findSingleClassImportStatement(String qName) {
    initializeMaps();
    return (PsiImportStatement)myClassNameToImportMap.get(qName);
  }

  public PsiImportStatement findOnDemandImportStatement(String packageName) {
    initializeMaps();
    return (PsiImportStatement)myPackageNameToImportMap.get(packageName);
  }

  public PsiImportStatementBase findSingleImportStatement(String name) {
    initializeMaps();
    return (PsiImportStatementBase)myNameToSingleImportMap.get(name);
  }

  private void initializeMaps() {
    if (myClassNameToImportMap == null){
      myClassNameToImportMap = new HashMap();
      myPackageNameToImportMap = new HashMap();
      myNameToSingleImportMap = new HashMap();
      PsiImportStatement[] imports = getImportStatements();
      for(int i = 0; i < imports.length; i++){
        PsiImportStatement anImport = imports[i];
        String qName = anImport.getQualifiedName();
        if (qName == null) continue;
        if (anImport.isOnDemand()){
          myPackageNameToImportMap.put(qName, anImport);
        }
        else{
          myClassNameToImportMap.put(qName, anImport);
          myNameToSingleImportMap.put(anImport.getImportReference().getReferenceName(), anImport);
        }
      }

      PsiImportStaticStatement[] importStatics = getImportStaticStatements();
      for (int i = 0; i < importStatics.length; i++) {
        PsiImportStaticStatement importStatic = importStatics[i];
        if (!importStatic.isOnDemand()) {
          String referenceName = importStatic.getReferenceName();
          if (referenceName != null) {
            myNameToSingleImportMap.put(referenceName, importStatic);
          }
        }
      }
    }
  }

  public void accept(PsiElementVisitor visitor){
    visitor.visitImportList(this);
  }

  public String toString(){
    return "PsiImportList";
  }
}
