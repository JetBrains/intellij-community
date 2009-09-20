package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.actions.AddFieldQuickFix;
import com.jetbrains.python.actions.AddMethodQuickFix;
import com.jetbrains.python.actions.ImportFromExistingFix;
import com.jetbrains.python.actions.AddImportAction;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.CollectProcessor;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Marks references that fail to resolve.
 * User: dcheryasov
 * Date: Nov 15, 2008
 */
public class PyUnresolvedReferencesInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unresolved.refs");
  }

  @NotNull
  public String getShortName() {
    return "PyUnresolvedReferencesInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.WARNING;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new Visitor(holder);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    static String getFirstQualifier(String name) {
      // foo.bar.baz -> foo
      int pos = name.indexOf('.');
      if (pos > 0)  return name.substring(0, pos);
      else return name;
    }

    static LocalQuickFix[] LQF_EMPTY = new LocalQuickFix[0];

    @NotNull
    static List<LocalQuickFix> proposeImportFixes(final PyElement node, String ref_text) {
      PsiFile exisitng_import_file = null; // if there's a matching existing import, this it the file it imports
      ImportFromExistingFix fix = null;
      List<LocalQuickFix> fixes = new ArrayList<LocalQuickFix>(2);
      Set<String> seen_file_names = new HashSet<String>(); // true import names
      // maybe the name is importable via some existing 'import foo' statement, and only needs a qualifier.
      // walk up collecting all such statements and analyzing
      CollectProcessor import_prc = new CollectProcessor(PyImportStatement.class);
      PyResolveUtil.treeCrawlUp(import_prc, node);
      List<PsiElement> result = import_prc.getResult();
      if (result.size() > 0) {
        fix = new ImportFromExistingFix(node, ref_text, true); // initially it is almost as lightweight as a plain list
        for (PsiElement stmt : import_prc.getResult()) {
          for (PyImportElement ielt : ((PyImportStatement)stmt).getImportElements()) {
            final PyReferenceExpression src = ielt.getImportReference();
            if (src != null) {
              PsiElement dst = src.resolve();
              if (dst instanceof PyFile) {
                PyFile dst_file = (PyFile)dst;
                String name = ielt.getImportReference().getReferencedName(); // ref is ok or matching would fail
                seen_file_names.add(name);
                PsiElement res = (dst_file).findExportedName(ref_text);
                if (res != null) {
                  exisitng_import_file = dst_file;
                  fix.addImport(res, dst_file, ielt);
                  fixes.add(fix);
                }
              }
            }
          }
        }
      }
      // maybe the name is importable via some existing 'from foo import ...' statement, and only needs another name to be imported.
      // walk up collecting all such statements and analyzing
      CollectProcessor from_import_prc = new CollectProcessor(PyFromImportStatement.class);
      PyResolveUtil.treeCrawlUp(from_import_prc, node);
      result = from_import_prc.getResult();
      if (result.size() > 0) {
        if (fix == null) fix = new ImportFromExistingFix(node, ref_text, false); // it could have been created before, or not
        for (PsiElement stmt : from_import_prc.getResult()) {
          PyFromImportStatement from_stmt = (PyFromImportStatement)stmt;
          PyImportElement[] ielts = from_stmt.getImportElements();
          if (ielts != null && ielts.length > 0) {
            final PyReferenceExpression src = from_stmt.getImportSource();
            if (src != null) {
              PsiElement dst = src.resolve();
              if (dst instanceof PyFile) {
                PyFile dst_file = (PyFile)dst;
                String name = from_stmt.getImportSource().getReferencedName(); // source is ok, else it won't match and we'd not be adding it
                seen_file_names.add(name);
                PsiElement res = (dst_file).findExportedName(ref_text);
                if (res != null) {
                  exisitng_import_file = dst_file;
                  fix.addImport(res, dst_file, ielts[ielts.length-1]); // last element; action expects to add to tail
                  fixes.add(fix);
                }
              }
            }
          }
        }
      }
      // maybe some unimported file has it, too
      ProgressManager.getInstance().checkCanceled(); // before expensive index searches
      // NOTE: current indices have limitations, only finding direct definitions of classes and functions.
      Project project = node.getProject();
      GlobalSearchScope scope = null; // GlobalSearchScope.projectScope(project);
      List<PsiElement> symbols = new ArrayList<PsiElement>();
      symbols.addAll(StubIndex.getInstance().get(PyClassNameIndex.KEY, ref_text, project, scope));
      symbols.addAll(StubIndex.getInstance().get(PyFunctionNameIndex.KEY, ref_text, project, scope));
      /* XXX CPU hog? */
      if (symbols.size() > 0) {
        if (fix == null) fix = new ImportFromExistingFix(node, ref_text, false); // it might have been created in the previous scan, or not.
        for (PsiElement symbol : symbols) {
          if (symbol.getParent() instanceof PsiFile) { // we only want top-level symbols
            PsiFile srcfile = symbol.getContainingFile();
            if (srcfile != null && srcfile != exisitng_import_file) {
              VirtualFile vfile = srcfile.getVirtualFile();
              if (vfile != null) {
                String import_path = ResolveImportUtil.findShortestImportableName(node, vfile);
                if (import_path != null && !seen_file_names.contains(import_path)) {
                  // a new, valid hit
                  fix.addImport(symbol, srcfile, null, import_path, proposeAsName(node.getContainingFile(), ref_text, import_path));
                  seen_file_names.add(import_path); // just in case, again
                }
              }
            }
          }
        }
        fixes.add(fix);
        fixes.add(fix.createDual());
      }
      return fixes;
    }


    private final static String[] AS_PREFIXES = {"other_", "one_more_", "different_", "pseudo_", "true_"};

    // a no-frills recursive accumulating scan
    private static void collectIdentifiers(PsiElement node, Collection<String> dst) {
      PsiElement seeker = node.getFirstChild();
      while (seeker != null) {
        if (seeker instanceof NameDefiner) {
          for (PyElement named : ((NameDefiner)seeker).iterateNames()) {
            if (named != null) dst.add(named.getName());
          }
        }
        else collectIdentifiers(seeker, dst);
        seeker = seeker.getNextSibling();
      }
    }

    // find an unique name that does not clash with anything in the file, using ref_name and import_path as hints
    private static String proposeAsName(PsiFile file, String ref_name, String import_path) {
      // a somehow brute-force approach: collect all identifiers wholesale and avoid clashes with any of them
      Set<String> ident_set = new HashSet<String>();
      collectIdentifiers(file, ident_set);
      // try the default, 'normal' name first; if it does not clash, propose no sustitute!
      if (! ident_set.contains(ref_name)) return null;
      // try flattened import path
      String path_name = import_path.replace('.', '_');
      if (! ident_set.contains(path_name)) return path_name;
      // ...with prefixes: a highly improbable situation already
      for (String prefix : AS_PREFIXES) {
        String variant = prefix + path_name;
        if (! ident_set.contains(variant)) return variant;
      }
      // if nothing helped, just bluntly add a number to the end. guaranteed to finish in ident_set.size()+1 iterations.
      int cnt = 1;
      while (cnt < Integer.MAX_VALUE) {
        String variant = path_name + Integer.toString(cnt);
        if (! ident_set.contains(variant)) return variant;
        cnt += 1;
      }
      return "SHOOSHPANCHICK"; // no, this cannot happen in a life-size file, just keeps inspections happy
    }

    @Override
    public void visitPyElement(final PyElement node) {
      super.visitPyElement(node);    //To change body of overridden methods use File | Settings | File Templates.
      for (final PsiReference reference : node.getReferences()) {
        if (reference.isSoft()) continue;
        HighlightSeverity severity = HighlightSeverity.ERROR;
        if (reference instanceof PsiReferenceEx) {
          severity = ((PsiReferenceEx) reference).getUnresolvedHighlightSeverity();
          if (severity == null) continue;
        }
        boolean unresolved;
        if (reference instanceof PsiPolyVariantReference) {
          final PsiPolyVariantReference poly = (PsiPolyVariantReference)reference;
          unresolved = (poly.multiResolve(false).length == 0);
        }
        else {
          unresolved = (reference.resolve() == null);
        }
        if (unresolved) {
          StringBuilder description_buf = new StringBuilder(""); // TODO: clear description_buf logic. maybe a flag is needed instead.
          String text = reference.getElement().getText();
          String ref_text = reference.getRangeInElement().substring(text); // text of the part we're working with
          List<LocalQuickFix> actions = new ArrayList<LocalQuickFix>(2);
          HintAction hint_action = null;
          if (ref_text.length() <= 0) return; // empty text, nothing to highlight
          if (reference instanceof PyReferenceExpression) {
            PyReferenceExpression refex = (PyReferenceExpression)reference;
            String refname = refex.getReferencedName();
            if (refex.getQualifier() != null) {
              final PyClassType object_type = PyBuiltinCache.getInstance(node.getProject()).getObjectType();
              if ((object_type != null) && object_type.getPossibleInstanceMembers().contains(refname)) continue;
            }
            // unqualified:
            // may be module's
            if (PyModuleType.getPossibleInstanceMembers().contains(refname)) continue;
            // may be a "try: import ..."; not an error not to resolve
            if ((
              PsiTreeUtil.getParentOfType(
                PsiTreeUtil.getParentOfType(node, PyImportElement.class), PyTryExceptStatement.class, PyIfStatement.class
              ) != null
            )) {
              severity = HighlightSeverity.INFO;
              String errmsg = PyBundle.message("INSP.module.$0.not.found", ref_text);
              description_buf.append(errmsg);
              // TODO: mark the node so that future references pointing to it won't result in a error, but in a warning
            }
            // look in other imported modules for this whole name
            List<LocalQuickFix> import_fixes = proposeImportFixes(node, ref_text);
            if (import_fixes.size() > 0) {
              actions.addAll(import_fixes);
              Object first_action = import_fixes.get(0);
              if (first_action instanceof HintAction) {
                hint_action = ((HintAction)first_action);
              }
            }
          }
          if (reference instanceof PsiReferenceEx) {
            final String s = ((PsiReferenceEx)reference).getUnresolvedDescription();
            if (s != null) description_buf.append(s);
          }
          if (description_buf.length() == 0) {
            boolean marked_qualified = false;
            if (reference instanceof PyQualifiedExpression) {
              final PyExpression qexpr = ((PyQualifiedExpression)reference).getQualifier();
              if (qexpr != null) {
                PyType qtype = qexpr.getType();
                if (qtype != null) {
                  if (qtype instanceof PyNoneType) {
                    // this almost always means that we don't know the type, so don't show an error in this case
                    continue;
                  }
                  if (qtype instanceof PyClassType) {
                    PyClass cls = ((PyClassType)qtype).getPyClass();
                    if (cls != null) {
                      if (reference.getElement().getParent() instanceof PyCallExpression) {
                        actions.add(new AddMethodQuickFix(ref_text, cls));
                      }
                      else actions.add(new AddFieldQuickFix(ref_text, cls));
                    }
                    description_buf.append(PyBundle.message("INSP.unresolved.ref.$0.for.class.$1", ref_text, qtype.getName()));
                    marked_qualified = true;
                  }
                  else {
                    description_buf.append(PyBundle.message("INSP.cannot.find.$0.in.$1", ref_text, qtype.getName()));
                    marked_qualified = true;
                  }
                }
              }
            }
            if (! marked_qualified) {
              description_buf.append(PyBundle.message("INSP.unresolved.ref.$0", ref_text));
              hint_action = new AddImportAction(reference); // unconditionally; action will fend for itself. 
            }
          }
          String description = description_buf.toString();
          ProblemHighlightType hl_type;
          if (severity == HighlightSeverity.WARNING) {
            hl_type = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
          }
          else {
            hl_type = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
          }
          PsiElement point = node.getLastChild(); // usually the identifier at the end of qual ref
          if (point == null) point = node;
          registerProblem(point, description, hl_type, hint_action, actions.toArray(LQF_EMPTY));
        }
      }
    }

  }
}
