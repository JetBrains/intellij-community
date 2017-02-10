/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.psi.impl.references;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
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
      return "No module named " + myElement.getReferencedName();
    }
    return super.getUnresolvedDescription();
  }

  @NotNull
  @Override
  protected List<RatedResolveResult> resolveInner() {
    final PyImportElement parent = PsiTreeUtil.getParentOfType(myElement, PyImportElement.class); //importRef.getParent();
    final QualifiedName qname = myElement.asQualifiedName();
    return qname == null ? Collections.<RatedResolveResult>emptyList() : ResolveImportUtil.resolveNameInImportStatement(parent, qname);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    // no completion in invalid import statements
    PyImportElement importElement = PsiTreeUtil.getParentOfType(myElement, PyImportElement.class);
    if (importElement != null) {
      PsiErrorElement prevError = PsiTreeUtil.getPrevSiblingOfType(importElement, PsiErrorElement.class);
      if (prevError != null) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
      }
    }

    PyExpression qualifier = myElement.getQualifier();
    final TypeEvalContext context = TypeEvalContext.codeCompletion(myElement.getProject(),
                                                                   CompletionUtil.getOriginalOrSelf(myElement).getContainingFile());
    if (qualifier != null) {
      // qualifier's type must be module, it should know how to complete
      PyType type = context.getType(qualifier);
      if (type != null) {
        Object[] variants = getTypeCompletionVariants(myElement, type);
        if (!alreadyHasImportKeyword()) {
          replaceInsertHandler(variants, ImportKeywordHandler.INSTANCE);
        }
        return variants;
      }
      else {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
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

    public ImportVariantCollector(@NotNull TypeEvalContext context) {
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
          PsiElement modCandidate = src.getReference().resolve();
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
            return myObjects.toArray();
          }
          else if (modCandidate instanceof PsiDirectory) {
            fillFromDir((PsiDirectory)modCandidate, ImportKeywordHandler.INSTANCE);
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
        // look at dir by level
        if ((relativeLevel >= 0 || !ResolveImportUtil.isAbsoluteImportEnabledFor(myCurrentFile))) {
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
      }
      if (relativeLevel == -1) {
        fillFromQName(QualifiedName.fromComponents(), insertHandler);
      }

      return ArrayUtil.toObjectArray(myObjects);
    }

    private void fillFromQName(QualifiedName thisQName, InsertHandler<LookupElement> insertHandler) {
      QualifiedNameResolver visitor = new QualifiedNameResolverImpl(thisQName).fromElement(myCurrentFile);
      for (PsiDirectory dir : visitor.resultsOfType(PsiDirectory.class)) {
        fillFromDir(dir, insertHandler);
      }
    }

    private void addImportedNames(@NotNull PyImportElement[] importElements) {
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

  /**
   * Adds ' import ' text after the item.
   */
  private static class ImportKeywordHandler implements InsertHandler<LookupElement> {
    public static final InsertHandler<LookupElement> INSTANCE = new ImportKeywordHandler();

    private static final String IMPORT_KWD = " import ";

    public void handleInsert(InsertionContext context, LookupElement item) {
      final Editor editor = context.getEditor();
      final Document document = editor.getDocument();
      int tailOffset = context.getTailOffset();
      document.insertString(tailOffset, IMPORT_KWD);
      editor.getCaretModel().moveToOffset(tailOffset + IMPORT_KWD.length());
    }
  }
}
