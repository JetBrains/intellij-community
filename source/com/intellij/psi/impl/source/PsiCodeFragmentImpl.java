package com.intellij.psi.impl.source;

import com.intellij.lang.Language;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceByDocument;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.resolve.JavaResolveUtil;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.StringTokenizer;

public class PsiCodeFragmentImpl extends PsiFileImpl implements JavaCodeFragment {
  private PsiElement myContext;
  private boolean myPhysical;
  private PsiType myThisType;
  private PsiType mySuperType;
  private LinkedHashMap<String, String> myPseudoImports = new LinkedHashMap<String, String>();
  private VisibilityChecker myVisibilityChecker;
  private ExceptionHandler myExceptionHandler;
  private GlobalSearchScope myResolveScope;
  private IntentionActionsFilter myIntentionActionsFilter;

  public PsiCodeFragmentImpl(Project project,
                             IElementType contentElementType,
                             boolean isPhysical,
                             @NonNls String name,
                             CharSequence text) {
    super(Constants.CODE_FRAGMENT,
          contentElementType,
          ((PsiManagerEx)PsiManager.getInstance(project)).getFileManager().createFileViewProvider(
            new LightVirtualFile(name, FileTypeManager.getInstance().getFileTypeByFileName(name), text), isPhysical)
    );
    ((SingleRootFileViewProvider)getViewProvider()).forceCachedPsi(this);
    myPhysical = isPhysical;
  }

  @NotNull
  public Language getLanguage() {
    return getContentElementType().getLanguage();
  }

  protected PsiCodeFragmentImpl clone() {
    final PsiCodeFragmentImpl clone = (PsiCodeFragmentImpl)cloneImpl((FileElement)calcTreeElement().clone());
    clone.myPhysical = false;
    clone.myOriginalFile = this;
    clone.myPseudoImports = new LinkedHashMap<String, String>(myPseudoImports);
    FileManager fileManager = ((PsiManagerEx)getManager()).getFileManager();
    SingleRootFileViewProvider cloneViewProvider = (SingleRootFileViewProvider)fileManager.createFileViewProvider(new LightVirtualFile(getName(), getLanguage(), getText()), false);
    cloneViewProvider.forceCachedPsi(clone);
    clone.myViewProvider = cloneViewProvider;
    return clone;
  }

  private FileViewProvider myViewProvider = null;

  @NotNull
  public FileViewProvider getViewProvider() {
    if(myViewProvider != null) return myViewProvider;
    return super.getViewProvider();
  }

  public boolean isValid() {
    if (!super.isValid()) return false;
    if (myContext != null && !myContext.isValid()) return false;
    return true;
  }

  @NotNull
  public FileType getFileType() {
    return StdFileTypes.JAVA;
  }


  public PsiElement getContext() {
    return myContext;
  }

  public void setContext(PsiElement context) {
    myContext = context;
  }

  public PsiType getThisType() {
    return myThisType;
  }

  public void setThisType(PsiType psiType) {
    myThisType = psiType;
  }

  public PsiType getSuperType() {
    return mySuperType;
  }

  public void setSuperType(final PsiType superType) {
    mySuperType = superType;
  }

  public String importsToString() {
    return StringUtil.join(myPseudoImports.values(), ",");
  }

  public void addImportsFromString(String imports) {
    StringTokenizer tokenizer = new StringTokenizer(imports, ",");
    while(tokenizer.hasMoreTokens()){
      String qName = tokenizer.nextToken();
      String name = PsiNameHelper.getShortClassName(qName);
      myPseudoImports.put(name, qName);
    }
  }

  public void setVisibilityChecker(VisibilityChecker checker) {
    myVisibilityChecker = checker;
  }

  public VisibilityChecker getVisibilityChecker() {
    return myVisibilityChecker;
  }

  public boolean isPhysical() {
    return myPhysical;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitCodeFragment(this);
    }
    else {
      visitor.visitFile(this);
    }
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    final ElementClassHint classHint = processor.getHint(ElementClassHint.class);

    if (classHint == null || classHint.shouldProcess(PsiClass.class)) {
      final NameHint nameHint = processor.getHint(NameHint.class);
      final String name = nameHint != null ? nameHint.getName(state) : null;
      if (name != null) {
        String qNameImported = myPseudoImports.get(name);
        if (qNameImported != null) {
          PsiClass imported = JavaPsiFacade.getInstance(myManager.getProject()).findClass(qNameImported, getResolveScope());
          if (imported != null) {
            if (!processor.execute(imported, state)) return false;
          }
        }
      }
      else {
        for (String qNameImported : myPseudoImports.values()) {
          PsiClass aClass = JavaPsiFacade.getInstance(myManager.getProject()).findClass(qNameImported, getResolveScope());
          if (aClass != null) {
            if (!processor.execute(aClass, state)) return false;
          }
        }
      }

      if (myContext == null) {
        return JavaResolveUtil.processImplicitlyImportedPackages(processor, state, place, getManager());
      }
    }


    IElementType i = myContentElementType;
    if (i == ElementType.TYPE_TEXT || i == ElementType.EXPRESSION_STATEMENT || i == ElementType.REFERENCE_TEXT) {
      return true;
    } else {
      processor.handleEvent(PsiScopeProcessor.Event.SET_DECLARATION_HOLDER, this);
      if (lastParent == null) {
        // Parent element should not see our vars
        return true;
      }

      return PsiScopesUtil.walkChildrenScopes(this, processor, state, lastParent, place);
    }
  }

  public String toString() {
    return "PsiCodeFragment:" + getName();
  }

  public boolean importClass(PsiClass aClass) {
    final String className = aClass.getName();
    final String qName = aClass.getQualifiedName();
    if (qName == null) return false;
    //if (!myPseudoImports.containsKey(className)){
    myPseudoImports.put(className, qName);
    myManager.nonPhysicalChange(); // to clear resolve caches!
    if (isPhysical()) {
      final Project project = myManager.getProject();
      final Document document = PsiDocumentManager.getInstance(project).getDocument(this);
      UndoManager.getInstance(project).undoableActionPerformed(new ImportClassUndoableAction(className, qName, document, myPseudoImports));
    }
    return true;
    //}
    //else{
    //  return false;
    //}
  }

  private static class ImportClassUndoableAction implements UndoableAction {
    private final String myClassName;
    private final String myQName;
    private final LinkedHashMap<String,String> myPseudoImports;
    private final Document myDocument;

    public ImportClassUndoableAction(final String className,
                                     final String qName,
                                     final Document document,
                                     final LinkedHashMap<String, String> pseudoImportsMap) {
      myClassName = className;
      myQName = qName;
      myDocument = document;
      myPseudoImports = pseudoImportsMap;
    }

    public boolean isComplex() {
      return false;
    }

    public void undo() {
      myPseudoImports.remove(myClassName);
    }

    public void redo() {
      myPseudoImports.put(myClassName, myQName);
    }

    public DocumentReference[] getAffectedDocuments() {
      Document document = myDocument;
      return new DocumentReference[]{DocumentReferenceByDocument.createDocumentReference(document)};
    }
  }

  public ExceptionHandler getExceptionHandler() {
    return myExceptionHandler;
  }

  public void setIntentionActionsFilter(final IntentionActionsFilter filter) {
    myIntentionActionsFilter = filter;
  }

  public IntentionActionsFilter getIntentionActionsFilter() {
    return myIntentionActionsFilter;
  }

  public void forceResolveScope(GlobalSearchScope scope) {
    myResolveScope = scope;
  }

  public GlobalSearchScope getForcedResolveScope() {
    return myResolveScope;
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    if (myResolveScope != null) return myResolveScope;
    return super.getResolveScope();
  }

  public void setExceptionHandler(final ExceptionHandler exceptionHandler) {
    myExceptionHandler = exceptionHandler;
  }
}
