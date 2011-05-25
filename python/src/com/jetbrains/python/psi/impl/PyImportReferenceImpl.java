package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyImportReferenceImpl extends PyReferenceImpl {
  private final PyReferenceExpressionImpl myElement;

  public PyImportReferenceImpl(PyReferenceExpressionImpl element, PyResolveContext context) {
    super(element, context);
    myElement = element;
  }

  @NotNull
  @Override
  protected List<RatedResolveResult> resolveInner() {
    ResolveResultList ret = new ResolveResultList();
    final String referencedName = myElement.getReferencedName();
    if (referencedName == null) return ret;

    int default_submodule_rate = RatedResolveResult.RATE_HIGH;

    // names inside module take precedence over submodules
    final PyImportElement import_elt = PsiTreeUtil.getParentOfType(myElement, PyImportElement.class);
    if (import_elt != null) {
      if (ret.poke(ResolveImportUtil.findImportedNameInsideModule(import_elt, referencedName), RatedResolveResult.RATE_HIGH)) {
        default_submodule_rate = RatedResolveResult.RATE_NORMAL;
      }
    }

    List<PsiElement> targets = ResolveImportUtil.resolveImportReference(myElement);
    for (PsiElement target : targets) {
      target = PyUtil.turnDirIntoInit(target);
      if (target != null) {   // ignore dirs without __init__.py, worthless
        int rate = default_submodule_rate;
        if (target instanceof PyFile) {
          VirtualFile vFile = ((PyFile)target).getVirtualFile();
          if (vFile != null && vFile.getLength() == 0) {
            rate -= 100;
          }
        }
        ret.poke(target, rate);
      }
    }

    return ret;
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
    if (qualifier != null) {
      // qualifier's type must be module, it should know how to complete
      PyType type = qualifier.getType(TypeEvalContext.fast());
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
      return new ImportVariantCollector().execute();
    }
  }

  private static void replaceInsertHandler(Object[] variants, final InsertHandler<LookupElement> insertHandler) {
    for (int i=0; i < variants.length; i+=1) {
      Object item = variants[i];
      if (hasChildPackages(item)) continue;
      if (item instanceof LookupElementBuilder) {
        variants[i] = ((LookupElementBuilder)item).setInsertHandler(insertHandler);
      }
      else if (item instanceof PsiNamedElement) {
        final PsiNamedElement element = (PsiNamedElement)item;
        variants[i] = LookupElementBuilder
          .create(element.getName()) // it can't really have null name
          .setIcon(element.getIcon(0))
          .setInsertHandler(insertHandler);
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

    public ImportVariantCollector() {
      PsiFile currentFile = myElement.getContainingFile();
      if (currentFile != null) currentFile = currentFile.getOriginalFile();
      myCurrentFile = currentFile;
      myNamesAlready = new HashSet<String>();
      myObjects = new ArrayList<Object>();
    }

    public Object[] execute() {
      int relative_level = -1;
      Condition<PsiElement> node_filter = new PyResolveUtil.FilterNameNotIn(myNamesAlready);
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
            // collect what's within module file
            final VariantsProcessor processor = new VariantsProcessor(myElement, node_filter, null);
            processor.setPlainNamesOnly(true); // we don't want parens after functions, etc
            PyResolveUtil.treeCrawlUp(processor, true, mod_candidate);
            final List<LookupElement> names_from_module = processor.getResultList();
            myObjects.addAll(names_from_module);
            for (LookupElement le : names_from_module) myNamesAlready.add(le.getLookupString()); // file's definitions shadow submodules
            // try to collect submodules
            PyExpression module = (PyExpression)mod_candidate;
            PyType qualifierType = module.getType(TypeEvalContext.fast());
            if (qualifierType != null) {
              ProcessingContext ctx = new ProcessingContext();
              ctx.put(PyType.CTX_NAMES, myNamesAlready);
              Collections.addAll(myObjects, qualifierType.getCompletionVariants(myElement.getName(), myElement, ctx));
            }
            return myObjects.toArray();
          }
        }
        else { // null source, must be a "from ... import"
          relative_level = from_import.getRelativeLevel();
          if (relative_level > 0) {
            PsiDirectory relative_dir = ResolveImportUtil.stepBackFrom(myCurrentFile, relative_level);
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
          relative_level += 1;
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
        if (myCurrentFile != null && (relative_level >= 0 || !ResolveImportUtil.isAbsoluteImportEnabledFor(myCurrentFile))) {
          final PsiDirectory containingDirectory = myCurrentFile.getContainingDirectory();
          if (containingDirectory != null) {
            PyQualifiedName thisQName = ResolveImportUtil.findShortestImportableQName(containingDirectory);
            if (thisQName == null) {
              fillFromDir(ResolveImportUtil.stepBackFrom(myCurrentFile, relative_level), insertHandler);
            }
            else if (thisQName.getComponentCount() >= relative_level) {
              thisQName = thisQName.removeTail(relative_level);
              fillFromQName(thisQName, insertHandler);
            }
          }
        }
      }
      if (relative_level == -1) {
        fillFromQName(PyQualifiedName.fromComponents(), insertHandler);
      }

      return ArrayUtil.toObjectArray(myObjects);
    }

    private void fillFromQName(PyQualifiedName thisQName, InsertHandler<LookupElement> insertHandler) {
      final List<PsiElement> dirs = ResolveImportUtil.resolveModulesInRoots(thisQName, myCurrentFile);
      for (PsiElement dir : dirs) {
        if (dir instanceof PsiDirectory) {
          fillFromDir((PsiDirectory)dir, insertHandler);
        }
      }
    }

    private void addImportedNames(@NotNull PyImportElement[] import_elts) {
      for (PyImportElement ielt : import_elts) {
        PyReferenceExpression ref = ielt.getImportReference();
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
          myObjects.addAll(PyModuleType.getSubmoduleVariants(target_dir, myElement, myNamesAlready));
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


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    PyImportReferenceImpl that = (PyImportReferenceImpl)o;

    if (!myElement.equals(that.myElement)) return false;
    if (!myContext.equals(that.myContext)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myElement.hashCode();
    result = 31 * result + myContext.hashCode();
    return result;
  }
}
