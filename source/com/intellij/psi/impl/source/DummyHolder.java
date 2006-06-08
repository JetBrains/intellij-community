package com.intellij.psi.impl.source;

import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.StdLanguages;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;

public class DummyHolder extends PsiFileImpl implements PsiImportHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.DummyHolder");

  private PsiElement myContext;
  private CharTable myTable = null;
  private final LinkedHashMap<String, PsiClass> myPseudoImports = new LinkedHashMap<String, PsiClass>();
  private Boolean myExplicitlyValid = null;
  private Language myLanguage = StdLanguages.JAVA;

  private FileElement myFileElement = null;

  public DummyHolder(PsiManager manager, TreeElement contentElement, PsiElement context) {
    this(manager, contentElement, context, SharedImplUtil.findCharTableByTree(contentElement));
  }

  public DummyHolder(PsiManager manager, CharTable table, boolean validity) {
    this(manager, null, null, table);
    myExplicitlyValid = Boolean.valueOf(validity);
  }

  public DummyHolder(PsiManager manager, PsiElement context) {
    super(DUMMY_HOLDER, DUMMY_HOLDER, new DummyHolderViewProvider(manager));
    ((DummyHolderViewProvider)getViewProvider()).setDummyHolder(this);
    LOG.assertTrue(manager != null);
    myContext = context;
    if (context != null) {
      myLanguage = context.getLanguage();
    }
  }

  public DummyHolder(PsiManager manager, TreeElement contentElement, PsiElement context, CharTable table) {
    this(manager, context);
    LOG.assertTrue(manager != null);
    myContext = context;
    myTable = table;
    if(contentElement != null) {
      TreeUtil.addChildren(getTreeElement(), contentElement);
      clearCaches();
    }
  }

  public DummyHolder(PsiManager manager, PsiElement context, CharTable table) {
    this(manager, context);
    myTable = table;
  }

  public DummyHolder(final PsiManager manager, final CharTable table, final Language language) {
    this(manager, null, table);
    myLanguage = language;
  }


  public PsiElement getContext() {
    return myContext;
  }

  public boolean isValid() {
    if(myExplicitlyValid != null) return myExplicitlyValid.booleanValue();
    if (!super.isValid()) return false;
    return !(myContext != null && !myContext.isValid());
  }

  public boolean importClass(PsiClass aClass) {
    if (myContext != null) {
      final PsiClass resolved = getManager().getResolveHelper().resolveReferencedClass(aClass.getName(), myContext);
      if (resolved != null) {
        return resolved.equals(aClass);
      }
    }

    String className = aClass.getName();
    if (!myPseudoImports.containsKey(className)) {
      myPseudoImports.put(className, aClass);
      myManager.nonPhysicalChange(); // to clear resolve caches!
      return true;
    }
    else {
      return false;
    }
  }

  public boolean hasImports() {
    return !myPseudoImports.isEmpty();
  }

  public boolean processDeclarations(PsiScopeProcessor processor, PsiSubstitutor substitutor, PsiElement lastParent, PsiElement place) {
    ElementClassHint classHint = processor.getHint(ElementClassHint.class);
    if (classHint == null || classHint.shouldProcess(PsiClass.class)) {
      final NameHint nameHint = processor.getHint(NameHint.class);
      final String name = nameHint != null ? nameHint.getName() : null;
      //"pseudo-imports"
      if (name != null) {
        PsiClass imported = myPseudoImports.get(name);
        if (imported != null) {
          if (!processor.execute(imported, substitutor)) return false;
        }
      } else {
        for (PsiClass aClass : myPseudoImports.values()) {
          if (!processor.execute(aClass, substitutor)) return false;
        }
      }

      if (myContext == null) {
        if (!ResolveUtil.processImplicitlyImportedPackages(processor, substitutor, place, getManager())) return false;
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
      final PsiElement otherContext = ((DummyHolder)other).myContext;
      if (myContext == null) return otherContext == null;
      return myContext.getManager().arePackagesTheSame(myContext, otherContext);
    }
    if (other instanceof PsiJavaFile) {
      if (myContext != null) return myContext.getManager().arePackagesTheSame(myContext, other);
      final String packageName = ((PsiJavaFile)other).getPackageName();
      return "".equals(packageName);
    }
    return false;
  }

  @NotNull
  public FileType getFileType() {
    if (myContext != null) {
      PsiFile containingFile = myContext.getContainingFile();
      if (containingFile != null) return containingFile.getFileType();
    }
    return StdFileTypes.JAVA;
  }

  public boolean isInPackage(PsiPackage aPackage) {
    if (myContext != null) return myContext.getManager().isInPackage(myContext, aPackage);
    if (aPackage == null) return true;
    return "".equals(aPackage.getQualifiedName());
  }

  public Lexer createLexer() {
    final Language language = getLanguage();
    if (language.equals(StdLanguages.JAVA)) {
      LanguageLevel javaLanguageLevel;
      final PsiElement context = getContext();
      javaLanguageLevel = context != null ? PsiUtil.getLanguageLevel(context) : getManager().getEffectiveLanguageLevel();
      return new JavaLexer(javaLanguageLevel);
    }
    final ParserDefinition parserDefinition = language.getParserDefinition();
    return parserDefinition.createLexer(getManager().getProject());
  }

  public synchronized FileElement getTreeElement() {
    if(myFileElement == null){
      myFileElement = new FileElement(DUMMY_HOLDER);
      myFileElement.setPsiElement(this);
      if(myTable != null) myFileElement.setCharTable(myTable);
      clearCaches();
    }
    return myFileElement;
  }

  @NotNull
  public Language getLanguage() {
    return myLanguage;
  }

  public void setLanguage(final Language language) {
    myLanguage = language;
  }

  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException"})
  protected PsiFileImpl clone() {
    final PsiFileImpl psiFile = (PsiFileImpl)cloneImpl(myFileElement);
    final DummyHolderViewProvider dummyHolderViewProvider = new DummyHolderViewProvider(getManager());
    myViewProvider = dummyHolderViewProvider;
    dummyHolderViewProvider.setDummyHolder((DummyHolder)psiFile);
    final FileElement treeClone = (FileElement)calcTreeElement().clone();
    psiFile.myTreeElementPointer = treeClone; // should not use setTreeElement here because cloned file still have VirtualFile (SCR17963)
    if(isPhysical()) psiFile.myOriginalFile = this;
    else psiFile.myOriginalFile = myOriginalFile;
    treeClone.setPsiElement(psiFile);
    return psiFile;
  }

  private FileViewProvider myViewProvider = null;

  public FileViewProvider getViewProvider() {
    if(myViewProvider != null) return myViewProvider;
    return super.getViewProvider();
  }
}
