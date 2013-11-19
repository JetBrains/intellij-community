/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.intellij.psi.util.QualifiedName;
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
    final TypeEvalContext context = TypeEvalContext.userInitiated(CompletionUtil.getOriginalOrSelf(myElement).getContainingFile());
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
      if (lookupElement.getObject() instanceof PsiElement) {
        itemElement = (PsiElement) lookupElement.getObject();
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
      final IElementType node_type = node.getElementType();
      if (node_type == PyTokenTypes.IMPORT_KEYWORD) {
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
      if (currentFile != null) currentFile = currentFile.getOriginalFile();
      myCurrentFile = currentFile;
      myNamesAlready = new HashSet<String>();
      myObjects = new ArrayList<Object>();
    }

    public Object[] execute() {
      int relativeLevel = -1;
      InsertHandler<LookupElement> insertHandler = null;

      // NOTE: could use getPointInImport()
      // are we in "import _" or "from foo import _"?
      PyFromImportStatement from_import = PsiTreeUtil.getParentOfType(myElement, PyFromImportStatement.class);
      if (from_import != null && myElement.getParent() != from_import) { // in "from foo import _"
        PyReferenceExpression src = from_import.getImportSource();
        if (src != null) {
          PsiElement mod_candidate = src.getReference().resolve();
          if (mod_candidate instanceof PyExpression) {
            addImportedNames(from_import.getImportElements()); // don't propose already imported items
            // try to collect submodules
            PyExpression module = (PyExpression)mod_candidate;
            PyType qualifierType = myContext.getType(module);
            if (qualifierType != null) {
              ProcessingContext ctx = new ProcessingContext();
              ctx.put(PyType.CTX_NAMES, myNamesAlready);
              Collections.addAll(myObjects, qualifierType.getCompletionVariants(myElement.getName(), myElement, ctx));
            }
            return myObjects.toArray();
          }
          else if (mod_candidate instanceof PsiDirectory) {
            fillFromDir((PsiDirectory)mod_candidate, ImportKeywordHandler.INSTANCE);
            return myObjects.toArray();
          }
        }
        else { // null source, must be a "from ... import"
          relativeLevel = from_import.getRelativeLevel();
          if (relativeLevel > 0) {
            PsiDirectory relative_dir = ResolveImportUtil.stepBackFrom(myCurrentFile, relativeLevel);
            if (relative_dir != null) {
              addImportedNames(from_import.getImportElements());
              fillFromDir(relative_dir, null);
            }
          }
        }
      }
      else { // in "import _" or "from _ import"
        ASTNode n = myElement.getNode().getTreePrev();
        while (n != null && n.getElementType() == PyTokenTypes.DOT) {
          relativeLevel += 1;
          n = n.getTreePrev();
        }
        if (from_import != null) {
          addImportedNames(from_import.getImportElements());
          if (!alreadyHasImportKeyword()) {
            insertHandler = ImportKeywordHandler.INSTANCE;
          }
        }
        else {
          myNamesAlready.add(PyNames.FUTURE_MODULE); // never add it to "import ..."
          PyImportStatement import_stmt = PsiTreeUtil.getParentOfType(myElement, PyImportStatement.class);
          if (import_stmt != null) {
            addImportedNames(import_stmt.getImportElements());
          }
        }
        // look at dir by level
        if (myCurrentFile != null && (relativeLevel >= 0 || !ResolveImportUtil.isAbsoluteImportEnabledFor(myCurrentFile))) {
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

    private void addImportedNames(@NotNull PyImportElement[] import_elts) {
      for (PyImportElement ielt : import_elts) {
        PyReferenceExpression ref = ielt.getImportReferenceExpression();
        if (ref != null) {
          String s = ref.getReferencedName();
          if (s != null) myNamesAlready.add(s);
        }
      }
    }

    // adds variants found under given dir
    private void fillFromDir(PsiDirectory target_dir, @Nullable InsertHandler<LookupElement> insertHandler) {
      if (target_dir != null) {
        PsiFile initPy = target_dir.findFile(PyNames.INIT_DOT_PY);
        if (initPy instanceof PyFile) {
          PyModuleType moduleType = new PyModuleType((PyFile)initPy);
          ProcessingContext context = new ProcessingContext();
          context.put(PyType.CTX_NAMES, myNamesAlready);
          Object[] completionVariants = moduleType.getCompletionVariants("", (PyExpression)getElement(), context);
          if (insertHandler != null) {
            replaceInsertHandler(completionVariants, insertHandler);
          }
          myObjects.addAll(Arrays.asList(completionVariants));
        }
        else {
          myObjects.addAll(PyModuleType.getSubModuleVariants(target_dir, myElement, myNamesAlready));
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
