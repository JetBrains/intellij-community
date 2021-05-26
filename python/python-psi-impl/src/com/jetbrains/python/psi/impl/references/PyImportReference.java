// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl.references;

import com.intellij.codeInsight.completion.CompletionUtilCoreImpl;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Reference in an import statement:<br/>
 * <code>import <u>foo.name</u></code>
 *
 * @author yole
 */
public class PyImportReference extends PyReferenceImpl {
  protected final PyReferenceExpressionImpl myElement;

  public PyImportReference(PyReferenceExpressionImpl element, PyResolveContext context) {
    super(element, context);
    myElement = element;
  }

  public static PyImportReference forElement(PyReferenceExpressionImpl expression, PsiElement importParent, PyResolveContext context) {
    if (importParent instanceof PyImportElement) {
      final PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(importParent, PyImportStatementBase.class);
      if (importStatement instanceof PyFromImportStatement) {
        return new PyFromImportNameReference(expression, context);
      }
      return new PyImportReference(expression, context);
    }
    return new PyFromImportSourceReference(expression, context);
  }

  @Override
  public String getUnresolvedDescription() {
    final PyImportStatement importStatement = PsiTreeUtil.getParentOfType(myElement, PyImportStatement.class);
    if (importStatement != null) {
      return PyPsiBundle.message("unresolved.import.reference", myElement.getReferencedName());
    }
    return super.getUnresolvedDescription();
  }

  @NotNull
  @Override
  protected List<RatedResolveResult> resolveInner() {
    final PyImportElement parent = PsiTreeUtil.getParentOfType(myElement, PyImportElement.class); //importRef.getParent();
    final QualifiedName qname = myElement.asQualifiedName();
    return qname == null ? Collections.emptyList() : ResolveImportUtil.resolveNameInImportStatement(parent, qname);
  }

  @Override
  public Object @NotNull [] getVariants() {
    // no completion in invalid import statements
    PyImportElement importElement = PsiTreeUtil.getParentOfType(myElement, PyImportElement.class);
    if (importElement != null) {
      PsiErrorElement prevError = PsiTreeUtil.getPrevSiblingOfType(importElement, PsiErrorElement.class);
      if (prevError != null) {
        return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
      }
    }

    PyExpression qualifier = myElement.getQualifier();
    final TypeEvalContext context = TypeEvalContext.codeCompletion(myElement.getProject(),
                                                                   CompletionUtilCoreImpl.getOriginalOrSelf(myElement).getContainingFile());
    if (qualifier != null) {
      // qualifier's type must be module, it should know how to complete
      PyType type = context.getType(qualifier);
      if (type != null) {
        Object[] variants = type.getCompletionVariants(myElement.getName(), myElement, new ProcessingContext());
        if (!alreadyHasImportKeyword()) {
          replaceInsertHandler(variants, ImportKeywordHandler.INSTANCE);
        }
        return variants;
      }
      else {
        return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
      }
    }
    else {
      // complete to possible modules
      return new ImportVariantCollector(context).execute();
    }
  }

  private static void replaceInsertHandler(Object[] variants, final InsertHandler<LookupElement> insertHandler) {
    for (int i=0; i < variants.length; i+=1) {
      Object item = variants[i];
      if (hasChildPackages(item)) continue;
      if (item instanceof LookupElementBuilder) {
        variants[i] = ((LookupElementBuilder)item).withInsertHandler(insertHandler);
      }
      else if (item instanceof PsiNamedElement) {
        final PsiNamedElement element = (PsiNamedElement)item;
        final String name = element.getName();
        assert name != null; // it can't really have null name
        variants[i] = LookupElementBuilder
          .create(name)
          .withIcon(element.getIcon(0))
          .withInsertHandler(insertHandler);
      }
    }
  }

  private static boolean hasChildPackages(Object item) {
    PsiElement itemElement = null;
    if (item instanceof PsiElement) {
      itemElement = (PsiElement) item;
    }
    else if (item instanceof LookupElement) {
      LookupElement lookupElement = (LookupElement) item;
      final PsiElement element = lookupElement.getPsiElement();
      if (element != null) {
        itemElement = element;
      }
    }
    return !(itemElement instanceof PsiFile);  // TODO deeper check?
  }

  private boolean alreadyHasImportKeyword() {
    if (PsiTreeUtil.getParentOfType(myElement, PyImportStatement.class) != null) {
      return true;
    }
    ASTNode node = myElement.getNode();
    while (node != null) {
      final IElementType nodeType = node.getElementType();
      if (nodeType == PyTokenTypes.IMPORT_KEYWORD) {
        return true;
      }
      node = node.getTreeNext();
    }
    return false;
  }

  class ImportVariantCollector {
    private final PsiFile myCurrentFile;
    private final Set<String> myNamesAlready;
    private final List<Object> myObjects;
    @NotNull private final TypeEvalContext myContext;

    ImportVariantCollector(@NotNull TypeEvalContext context) {
      myContext = context;
      PsiFile currentFile = myElement.getContainingFile();
      currentFile = currentFile.getOriginalFile();
      myCurrentFile = currentFile;
      myNamesAlready = new HashSet<>();
      myObjects = new ArrayList<>();
    }

    public Object[] execute() {
      int relativeLevel = -1;
      InsertHandler<LookupElement> insertHandler = null;

      // NOTE: could use getPointInImport()
      // are we in "import _" or "from foo import _"?
      PyFromImportStatement fromImport = PsiTreeUtil.getParentOfType(myElement, PyFromImportStatement.class);
      if (fromImport != null && myElement.getParent() != fromImport) { // in "from foo import _"
        PyReferenceExpression src = fromImport.getImportSource();
        if (src != null) {
          ResolveResult[] resolved = src.getReference().multiResolve(false);
          for (ResolveResult result : resolved) {
            PsiElement modCandidate = result.getElement();
            if (modCandidate instanceof PyExpression) {
              addImportedNames(fromImport.getImportElements()); // don't propose already imported items
              // try to collect submodules
              PyExpression module = (PyExpression)modCandidate;
              PyType qualifierType = myContext.getType(module);
              if (qualifierType != null) {
                ProcessingContext ctx = new ProcessingContext();
                ctx.put(PyType.CTX_NAMES, myNamesAlready);
                Collections.addAll(myObjects, qualifierType.getCompletionVariants(myElement.getName(), myElement, ctx));
              }
            }
            else if (modCandidate instanceof PsiDirectory) {
              fillFromDir((PsiDirectory)modCandidate, ImportKeywordHandler.INSTANCE);
            }
          }
          if (!myObjects.isEmpty()) {
            return myObjects.toArray();
          }
        }
        else { // null source, must be a "from ... import"
          relativeLevel = fromImport.getRelativeLevel();
          if (relativeLevel > 0) {
            PsiDirectory relativeDir = ResolveImportUtil.stepBackFrom(myCurrentFile, relativeLevel);
            if (relativeDir != null) {
              addImportedNames(fromImport.getImportElements());
              fillFromDir(relativeDir, null);
            }
          }
        }
        fillFromRootsIfNotRelative(relativeLevel, insertHandler);
      }
      else { // in "import _" or "from _ import"
        PsiElement prevElem = PyPsiUtils.getPrevNonWhitespaceSibling(myElement);
        while (prevElem != null && prevElem.getNode().getElementType() == PyTokenTypes.DOT) {
          relativeLevel += 1;
          prevElem = PyPsiUtils.getPrevNonWhitespaceSibling(prevElem);
        }
        if (fromImport != null) {
          addImportedNames(fromImport.getImportElements());
          if (!alreadyHasImportKeyword()) {
            insertHandler = ImportKeywordHandler.INSTANCE;
          }
        }
        else {
          myNamesAlready.add(PyNames.FUTURE_MODULE); // never add it to "import ..."
          PyImportStatement importStatement = PsiTreeUtil.getParentOfType(myElement, PyImportStatement.class);
          if (importStatement != null) {
            addImportedNames(importStatement.getImportElements());
          }
        }
        final PsiDirectory containingDirectory = myCurrentFile.getContainingDirectory();
        // look at dir by level
        if (LanguageLevel.forElement(myCurrentFile).isPy3K() && containingDirectory != null &&
            PyUtil.isExplicitPackage(containingDirectory)) {
          fillFromRootsIfNotRelative(relativeLevel, insertHandler);
          fillFromSameDirectoryOrRelative(relativeLevel, insertHandler);
        }
        else {
          fillFromSameDirectoryOrRelative(relativeLevel, insertHandler);
          fillFromRootsIfNotRelative(relativeLevel, insertHandler);
        }
      }

      return ArrayUtil.toObjectArray(myObjects);
    }

    private void fillFromRootsIfNotRelative(int relativeLevel, @Nullable InsertHandler<LookupElement> insertHandler) {
      if (relativeLevel == -1) {
        fillFromQName(QualifiedName.fromComponents(), insertHandler);
      }
    }

    private void fillFromSameDirectoryOrRelative(int relativeLevel, @Nullable InsertHandler<LookupElement> insertHandler) {
      if (relativeLevel < 0 && ResolveImportUtil.isAbsoluteImportEnabledFor(myCurrentFile)) return;
      final PsiDirectory containingDirectory = myCurrentFile.getContainingDirectory();
      if (containingDirectory != null) {
        QualifiedName thisQName = QualifiedNameFinder.findShortestImportableQName(containingDirectory);
        if (thisQName == null || thisQName.getComponentCount() == relativeLevel) {
          fillFromDir(ResolveImportUtil.stepBackFrom(myCurrentFile, relativeLevel), insertHandler);
        }
        else if (thisQName.getComponentCount() > relativeLevel) {
          thisQName = thisQName.removeTail(relativeLevel);
          fillFromQName(thisQName, insertHandler);
        }
      }
    }

    private void fillFromQName(QualifiedName thisQName, InsertHandler<LookupElement> insertHandler) {
      StreamEx.of(PyResolveImportUtil.resolveQualifiedName(thisQName, PyResolveImportUtil.fromFoothold(myCurrentFile)))
        .select(PsiDirectory.class)
        .forEach(directory -> fillFromDir(directory, insertHandler));
    }

    private void addImportedNames(PyImportElement @NotNull [] importElements) {
      for (PyImportElement element : importElements) {
        PyReferenceExpression ref = element.getImportReferenceExpression();
        if (ref != null) {
          String s = ref.getReferencedName();
          if (s != null) myNamesAlready.add(s);
        }
      }
    }

    /**
     * Adds variants found under given dir.
     */
    private void fillFromDir(PsiDirectory targetDir, @Nullable InsertHandler<LookupElement> insertHandler) {
      if (targetDir != null) {
        PsiFile initPy = targetDir.findFile(PyNames.INIT_DOT_PY);
        if (initPy instanceof PyFile) {
          PyModuleType moduleType = new PyModuleType((PyFile)initPy);
          ProcessingContext context = new ProcessingContext();
          context.put(PyType.CTX_NAMES, myNamesAlready);
          Object[] completionVariants = moduleType.getCompletionVariants("", getElement(), context);
          if (insertHandler != null) {
            replaceInsertHandler(completionVariants, insertHandler);
          }
          myObjects.addAll(Arrays.asList(completionVariants));
        }
        else {
          myObjects.addAll(PyModuleType.getSubModuleVariants(targetDir, myElement, myNamesAlready));
        }
      }
    }
  }

  @Override
  public HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
    final PyExpression qualifier = myElement.getQualifier();
    if (qualifier != null && context.getType(qualifier) == null) {
      /* in case the element qualifier can not be resolved, the qualifier need to be highlighted instead of the element */
      return null;
    }
    return HighlightSeverity.ERROR;
  }

  /**
   * Adds ' import ' text after the item.
   */
  private static class ImportKeywordHandler implements InsertHandler<LookupElement> {
    public static final InsertHandler<LookupElement> INSTANCE = new ImportKeywordHandler();

    private static final String IMPORT_KWD = " import ";

    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull LookupElement item) {
      final Editor editor = context.getEditor();
      final Document document = editor.getDocument();
      int tailOffset = context.getTailOffset();
      document.insertString(tailOffset, IMPORT_KWD);
      editor.getCaretModel().moveToOffset(tailOffset + IMPORT_KWD.length());
    }
  }
}
