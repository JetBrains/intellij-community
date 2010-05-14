package com.jetbrains.python.psi.impl;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.*;
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
    ResultList ret = new ResultList();

    final String referencedName = myElement.getReferencedName();
    if (referencedName == null) return ret;

    PsiElement target = ResolveImportUtil.resolveImportReference(myElement);

    target = PyUtil.turnDirIntoInit(target);
    if (target == null) {
      ret.clear();
      return ret; // it was a dir without __init__.py, worthless
    }
    ret.poke(target, RatedResolveResult.RATE_HIGH);
    return ret;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    PyExpression qualifier = myElement.getQualifier();
    if (qualifier != null) {
      // qualifier's type must be module, it should know how to complete
      PyType type = qualifier.getType(TypeEvalContext.fast());
      if (type != null) return type.getCompletionVariants(myElement, new ProcessingContext());
      else return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    else {
      // complete to possible modules
      List<Object> variants = new ArrayList<Object>();
      PsiFile current_file = myElement.getContainingFile();
      if (current_file != null) current_file = current_file.getOriginalFile();
      int relative_level = 0;
      final Set<String> names_already = new HashSet<String>(); // don't propose already imported names
      String ref_name = myElement.getName();
      Condition<PsiElement> node_filter = new PyResolveUtil.FilterNameNotIn(names_already);
      Condition<String> underscore_filter = new PyUtil.UnderscoreFilter(PyUtil.getInitialUnderscores(ref_name));
      boolean is_importing_from_a_module = false;

      // are we in "import _" or "from foo import _"?
      PyFromImportStatement from_import = PsiTreeUtil.getParentOfType(myElement, PyFromImportStatement.class);
      if (from_import != null && myElement.getParent() != from_import) { // in "from foo import _"
        PyReferenceExpression src = from_import.getImportSource();
        if (src != null) {
          PsiElement mod_candidate = src.getReference().resolve();
          if (mod_candidate instanceof PyExpression) {
            addImportedNames(from_import.getImportElements(), names_already, underscore_filter); // don't propose already imported items
            // collect what's within module file
            final VariantsProcessor processor = new VariantsProcessor(myElement, node_filter, underscore_filter);
            processor.setPlainNamesOnly(true); // we don't want parens after functions, etc
            PyResolveUtil.treeCrawlUp(processor, true, mod_candidate);
            variants.addAll(processor.getResultList());
            // try to collect submodules
            PyExpression module = (PyExpression)mod_candidate;
            PyType qualifierType = module.getType(TypeEvalContext.fast());
            if (qualifierType != null) {
              ProcessingContext ctx = new ProcessingContext();
              for (Object ex : variants) { // just in case: file's definitions shadow submodules
                if (ex instanceof PyReferenceExpression) {
                  names_already.add(((PyReferenceExpression)ex).getReferencedName());
                }
              }
              // collect submodules
              ctx.put(PyType.CTX_NAMES, names_already);
              Collections.addAll(variants, qualifierType.getCompletionVariants(myElement, ctx));
            }
            return variants.toArray();
          }
        }
        else { // null source, must be a "from ... import"
          relative_level = from_import.getRelativeLevel();
          if (relative_level > 0) {
            PsiDirectory relative_dir = ResolveImportUtil.stepBackFrom(current_file, relative_level);
            if (relative_dir != null) {
              addImportedNames(from_import.getImportElements(), names_already, underscore_filter);
              fillFromDir(relative_dir, current_file, underscore_filter, variants, null);
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
          addImportedNames(from_import.getImportElements(), names_already, underscore_filter);
          is_importing_from_a_module = true;
        }
        else {
          names_already.add(PyNames.FUTURE_MODULE); // never add it to "import ..."
          PyImportStatement import_stmt = PsiTreeUtil.getParentOfType(myElement, PyImportStatement.class);
          if (import_stmt != null) {
            addImportedNames(import_stmt.getImportElements(), names_already, underscore_filter);
          }
        }
        // look at dir by level
        if (current_file != null && (relative_level > 0 || ! ResolveImportUtil.isAbsoluteImportEnabledFor(current_file))) {
          fillFromDir(
            ResolveImportUtil.stepBackFrom(current_file, relative_level),
            current_file, underscore_filter, variants,
            is_importing_from_a_module? ImportKeywordHandler.INSTANCE : null
          );
        }
      }
      if (relative_level == 0) {
        // look in SDK
        final CollectingRootVisitor visitor = new CollectingRootVisitor(((PyReferenceExpression)myElement).getManager());
        final Module module = ModuleUtil.findModuleForPsiElement(myElement);
        if (module != null) {
          ModuleRootManager.getInstance(module).processOrder(new ResolveImportUtil.SdkRootVisitingPolicy(visitor), null);
          for (ModuleResult result : visitor.getResult()) { // to thwart stuff like "__phello__.foo"
            String name = result.getName();
            if (PyNames.isIdentifier(name) && underscore_filter.value(name)) {
              if (is_importing_from_a_module) {
                LookupElementBuilder lookup_item = LookupElementBuilder.create(name);
                if (! result.hasSubmodules()) lookup_item = lookup_item.setInsertHandler(ImportKeywordHandler.INSTANCE);
                variants.add(lookup_item);
              }
              else variants.add(name);
            }
          }
        }
      }


      return ArrayUtil.toObjectArray(variants);
    }
  }

  private static void addImportedNames(PyImportElement[] import_elts, Collection<String> collected_names, Condition<String> filter) {
    if (import_elts != null && collected_names != null) {
      for (PyImportElement ielt : import_elts) {
        String s;
        PyReferenceExpression ref = ielt.getImportReference();
        if (ref != null) {
          s = ref.getReferencedName();
          if (s != null && filter.value(s)) collected_names.add(s);
        }
      }
    }
  }

  // adds variants found under given dir
  private static void fillFromDir(
    PsiDirectory target_dir,
    PsiFile source_file,
    Condition<String> filter,
    @NotNull List<Object> variants,
    @Nullable InsertHandler<LookupElement> handler
  ) {
    if (target_dir != null) {
      for (PsiElement dir_item : target_dir.getChildren()) {
        if (dir_item != source_file) {
          if (dir_item instanceof PsiDirectory) {
            final PsiDirectory dir = (PsiDirectory)dir_item;
            if (dir.findFile(PyNames.INIT_DOT_PY) != null) {
              final String name = dir.getName();
              if (PyNames.isIdentifier(name) && filter.value(name)) {
                variants.add(LookupElementBuilder.create(name).setInsertHandler(handler));
              }
            }
          }
          else if (dir_item instanceof PsiFile) { // plain file
            String filename = ((PsiFile)dir_item).getName();
            if (!PyNames.INIT_DOT_PY.equals(filename) && filename.endsWith(PyNames.DOT_PY)) {
              final String name = filename.substring(0, filename.length() - PyNames.DOT_PY.length());
              if (PyNames.isIdentifier(name) && filter.value(name)) {
                variants.add(LookupElementBuilder.create(name).setInsertHandler(handler));
              }
            }
          }
        }
      }
    }
  }

  private static class ModuleResult extends Pair<String, Boolean> {
    public ModuleResult(String name, Boolean hasSubmodules) {
      super(name, hasSubmodules);
    }

    public boolean hasSubmodules() {
      return getSecond();
    }

    public String getName() {
      return getFirst();
    }
  }

  private static class CollectingRootVisitor implements SdkRootVisitor {
    Set<ModuleResult> result;
    PsiManager psimgr;

    static String cutExt(String name) {
      return name.substring(0, Math.max(name.length() - PyNames.DOT_PY.length(), 0));
    }

    public CollectingRootVisitor(PsiManager psimgr) {
      result = new com.intellij.util.containers.HashSet<ModuleResult>();
      this.psimgr = psimgr;
    }

    public boolean visitRoot(final VirtualFile root) {
      for (VirtualFile vfile : root.getChildren()) {
        if (vfile.getName().endsWith(PyNames.DOT_PY)) {
          PsiFile pfile = psimgr.findFile(vfile);
          if (pfile != null) result.add(new ModuleResult(cutExt(pfile.getName()), false));
        }
        else if (vfile.isDirectory() && (vfile.findChild(PyNames.INIT_DOT_PY) != null)) {
          PsiDirectory pdir = psimgr.findDirectory(vfile);
          if (pdir != null) {
            result.add(new ModuleResult(pdir.getName(), true));
            // we might check if submodules actually exist, but this would be far more expensive: a disk op.
          }
        }
      }
      return true; // continue forever
    }

    public Collection<ModuleResult> getResult() {
      return result;
    }
  }

  /** Adds ' import ' text after the item. */
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
