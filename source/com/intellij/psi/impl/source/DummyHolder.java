package com.intellij.psi.impl.source;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.CharTable;

import java.util.Iterator;
import java.util.LinkedHashMap;

public class DummyHolder extends PsiFileImpl implements PsiImportHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.DummyHolder");

  private PsiElement myContext;
  private CharTable myTable = null;
  private final LinkedHashMap<String, PsiClass> myPseudoImports = new LinkedHashMap<String, PsiClass>();

  public DummyHolder(PsiManager manager, TreeElement contentElement, PsiElement context) {
    this(manager, contentElement, context, SharedImplUtil.findCharTableByTree(contentElement));
  }

  public DummyHolder(PsiManager manager, PsiElement context) {
    super((PsiManagerImpl)manager, DUMMY_HOLDER);
    myContext = context;
    getTreeElement();
  }

  public DummyHolder(PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table) {
    super((PsiManagerImpl)manager, DUMMY_HOLDER);
    myContext = context;
    myTable = table;
    TreeUtil.addChildren(getTreeElement(), contentElement);
  }

  public DummyHolder(PsiManager manager, PsiElement context, CharTable table) {
    this(manager, context);
    myTable = table;
  }


  public PsiElement getContext() {
    return myContext;
  }

  public boolean isValid() {
    if (!super.isValid()) return false;
    if (myContext != null && !myContext.isValid()) return false;
    return true;
  }

  public boolean importClass(PsiClass aClass){
    LOG.assertTrue(myContext == null);
    String className = aClass.getName();
    if (!myPseudoImports.containsKey(className)){
      myPseudoImports.put(className, aClass);
      myManager.nonPhysicalChange(); // to clear resolve caches!
      return true;
    }
    else{
      return false;
    }
  }

  public boolean hasImports(){
    return !myPseudoImports.isEmpty();
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    if (myContext == null){ // process classes in lang&default package + "pseudo-imports"
      ElementClassHint classHint = (ElementClassHint)processor.getHint(ElementClassHint.class);
      if (classHint == null || classHint.shouldProcess(PsiClass.class)){
        final NameHint nameHint = (NameHint)processor.getHint(NameHint.class);
        final String name = nameHint != null ? nameHint.getName() : null;
        if (name != null){
          PsiClass imported = myPseudoImports.get(name);
          if (imported != null){
            if (!processor.execute(imported, substitutor)) return false;
          }
        }
        else{
          Iterator<PsiClass> iter = myPseudoImports.values().iterator();
          while(iter.hasNext()){
            PsiClass aClass = iter.next();
            if (!processor.execute(aClass, substitutor)) return false;
          }
        }

        PsiPackage langPackage = getManager().findPackage("java.lang");
        if (langPackage != null){
          if (!langPackage.processDeclarations(processor, substitutor, null, place)) return false;
        }

        PsiPackage defaultPackage = getManager().findPackage("");
        if (defaultPackage != null){
          if (!defaultPackage.processDeclarations(processor, substitutor, null, place)) return false;
        }
      }
    }
    return true;
  }

  public void accept(PsiElementVisitor visitor) {
    visitor.visitFile(this);
  }

  public String toString() {
    return "DummyHolder";
  }

  public boolean isSamePackage(PsiElement other) {
    if (other instanceof DummyHolder) {
      final PsiElement otherContext = ((DummyHolder) other).myContext;
      return (myContext == null && otherContext == null) || myContext.getManager().arePackagesTheSame(myContext, otherContext);
    }
    if (other instanceof PsiJavaFile) {
      if (myContext != null) return myContext.getManager().arePackagesTheSame(myContext, other);
      final String packageName = ((PsiJavaFile) other).getPackageName();
      return "".equals(packageName);
    }
    return false;
  }

  public FileType getFileType() {
    if (myContext!=null) {
      PsiFile containingFile = myContext.getContainingFile();
      if (containingFile!=null) return containingFile.getFileType();
    }
    return StdFileTypes.JAVA;
  }

  public boolean isInPackage(PsiPackage aPackage) {
    if (myContext != null) return myContext.getManager().isInPackage(myContext, aPackage);
    if (aPackage == null) return true;
    return "".equals(aPackage.getQualifiedName());
  }

  public Lexer createLexer() {
    return new JavaLexer(myManager.getEffectiveLanguageLevel());
  }

  public FileElement getTreeElement() {
    final FileElement holderElement = super.getTreeElement();
    if(myTable != null) holderElement.setCharTable(myTable);
    return holderElement;
  }
}
