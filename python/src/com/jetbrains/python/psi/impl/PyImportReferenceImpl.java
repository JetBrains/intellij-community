package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.io.FileUtil;
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
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkType;
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
    ResultList ret = new ResultList();
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
          for (int i=0; i < variants.length; i+=1) {
            Object item = variants[i];
            if (item instanceof LookupElementBuilder) {
              variants[i] = ((LookupElementBuilder)item).setInsertHandler(ImportKeywordHandler.INSTANCE);
            }
            else if (item instanceof PsiNamedElement) {
              final PsiNamedElement element = (PsiNamedElement)item;
              variants[i] = LookupElementBuilder
                .create(element.getName()) // it can't really have null name
                .setIcon(element.getIcon(0))
                .setInsertHandler(ImportKeywordHandler.INSTANCE);
            }
          }
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

  private boolean alreadyHasImportKeyword() {
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
      int relative_level = 0;
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
        if (myCurrentFile != null && (relative_level > 0 || !ResolveImportUtil.isAbsoluteImportEnabledFor(myCurrentFile))) {
          PyQualifiedName thisQName = ResolveImportUtil.findShortestImportableQName(myCurrentFile.getContainingDirectory());
          if (thisQName == null) {
            fillFromDir(ResolveImportUtil.stepBackFrom(myCurrentFile, relative_level), insertHandler);
          }
          else if (thisQName.getComponentCount() >= relative_level) {
            thisQName = thisQName.removeTail(relative_level);
            fillFromQName(thisQName, insertHandler);
          }
        }
      }
      if (relative_level == 0) {
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
    private void fillFromDir(PsiDirectory target_dir, @Nullable InsertHandler<LookupElement> handler) {
      if (target_dir != null) {
        for (PsiElement dir_item : target_dir.getChildren()) {
          if (dir_item != myCurrentFile) {
            if (dir_item instanceof PsiDirectory) {
              final PsiDirectory dir = (PsiDirectory)dir_item;
              if (dir.findFile(PyNames.INIT_DOT_PY) != null) {
                final String name = dir.getName();
                if (PyNames.isIdentifier(name)) {
                  myObjects.add(LookupElementBuilder
                                  .create(name)
                                  .setTypeText(getPresentablePath(dir.getParent()))
                                  .setIcon(dir.getIcon(Iconable.ICON_FLAG_CLOSED)));
                }
              }
            }
            else if (dir_item instanceof PsiFile) { // plain file
              String filename = ((PsiFile)dir_item).getName();
              if (!PyNames.INIT_DOT_PY.equals(filename) && filename.endsWith(PyNames.DOT_PY)) {
                final String name = filename.substring(0, filename.length() - PyNames.DOT_PY.length());
                if (PyNames.isIdentifier(name)) {
                  final PsiDirectory dir = ((PsiFile)dir_item).getContainingDirectory();
                  myObjects.add(LookupElementBuilder
                                  .create(name)
                                  .setTypeText(getPresentablePath(dir))
                                  .setInsertHandler(handler)
                                  .setIcon(dir_item.getIcon(0)));
                }
              }
            }
          }
        }
      }
    }

    private String getPresentablePath(PsiDirectory directory) {
      if (directory == null) {
        return "";
      }
      final String path = directory.getVirtualFile().getPath();
      if (path.contains(PythonSdkType.SKELETON_DIR_NAME)) {
        return "<built-in>";
      }
      return FileUtil.toSystemDependentName(path);
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
