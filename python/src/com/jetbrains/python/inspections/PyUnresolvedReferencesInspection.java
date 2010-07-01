package com.jetbrains.python.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.*;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.actions.*;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.patterns.Matcher;
import com.jetbrains.python.psi.patterns.ParentMatcher;
import com.jetbrains.python.psi.patterns.SyntaxMatchers;
import com.jetbrains.python.psi.resolve.CollectProcessor;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Marks references that fail to resolve. Also tracks unused imports and provides "optimize imports" support.
 * User: dcheryasov
 * Date: Nov 15, 2008
 */
public class PyUnresolvedReferencesInspection extends PyInspection {
  private final ThreadLocal<Visitor> myLastVisitor = new ThreadLocal<Visitor>();
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unresolved.refs");
  }

  private static Condition<PsiElement> IS_IMPORT_STATEMENT = new Condition<PsiElement>() {
    public boolean value(PsiElement psiElement) {
      return psiElement instanceof PyImportStatement;
    }
  };

  private static Condition<PsiElement> IS_FROM_IMPORT_STATEMENT = new Condition<PsiElement>() {
    public boolean value(PsiElement psiElement) {
      return psiElement instanceof PyFromImportStatement;
    }
  };

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final Visitor visitor = new Visitor(holder);
    myLastVisitor.set(visitor);
    return visitor;
  }

  @Override
  public void inspectionFinished(LocalInspectionToolSession session) {
    final Visitor visitor = myLastVisitor.get();
    assert visitor != null;
    if (PyCodeInsightSettings.getInstance().HIGHLIGHT_UNUSED_IMPORTS) {
      visitor.highlightUnusedImports();
    }
    myLastVisitor.remove();
  }

  public static class Visitor extends PyInspectionVisitor {
    private Set<NameDefiner> myUsedImports = Collections.synchronizedSet(new HashSet<NameDefiner>());
    private Set<NameDefiner> myAllImports = Collections.synchronizedSet(new HashSet<NameDefiner>());

    public Visitor(final ProblemsHolder holder) {
      super(holder);
    }

    @NotNull
    static Collection<LocalQuickFix> proposeImportFixes(final PyElement node, String ref_text) {
      PsiFile exisitng_import_file = null; // if there's a matching existing import, this it the file it imports
      ImportFromExistingFix fix = null;
      Collection<LocalQuickFix> fixes = new HashSet<LocalQuickFix>(2);
      Set<String> seen_file_names = new HashSet<String>(); // true import names
      // maybe the name is importable via some existing 'import foo' statement, and only needs a qualifier.
      // walk up collecting all such statements and analyzing
      CollectProcessor import_prc = new CollectProcessor(IS_IMPORT_STATEMENT);
      PyResolveUtil.treeCrawlUp(import_prc, node);
      List<PsiElement> result = import_prc.getResult();
      if (result.size() > 0) {
        fix = new ImportFromExistingFix(node, ref_text, true); // initially it is almost as lightweight as a plain list
        for (PsiElement stmt : import_prc.getResult()) {
          for (PyImportElement ielt : ((PyImportStatement)stmt).getImportElements()) {
            final PyReferenceExpression src = ielt.getImportReference();
            if (src != null) {
              PsiElement dst = src.getReference().resolve();
              if (dst instanceof PyFile) {
                PyFile dst_file = (PyFile)dst;
                String name = ielt.getImportReference().getReferencedName(); // ref is ok or matching would fail
                seen_file_names.add(name);
                PsiElement res = dst_file.findExportedName(ref_text);
                if (res != null && !(res instanceof PyFile)) {
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
      CollectProcessor from_import_prc = new CollectProcessor(IS_FROM_IMPORT_STATEMENT);
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
              PsiElement dst = src.getReference().resolve();
              if (dst instanceof PyFile) {
                PyFile dst_file = (PyFile)dst;
                String name = from_stmt.getImportSource().getReferencedName(); // source is ok, else it won't match and we'd not be adding it
                seen_file_names.add(name);
                PsiElement res = dst_file.findExportedName(ref_text);
                if (res != null && !(res instanceof PyFile)) {
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
      ProgressManager.checkCanceled(); // before expensive index searches
      // NOTE: current indices have limitations, only finding direct definitions of classes and functions.
      Project project = node.getProject();
      GlobalSearchScope scope = ProjectScope.getAllScope(project);
      List<PsiElement> symbols = new ArrayList<PsiElement>();
      symbols.addAll(PyClassNameIndex.find(ref_text, project, scope));
      symbols.addAll(PyFunctionNameIndex.find(ref_text, project));
      // NOTE: possible CPU hog 
      if (symbols.size() > 0) {
        if (fix == null) { // it might have been created in the previous scan, or not.
          fix = new ImportFromExistingFix(node, ref_text, !PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT);
        }
        for (PsiElement symbol : symbols) {
          if (isTopLevel(symbol)) { // we only want top-level symbols
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
        fix.sortCandidates();
        if (!fixes.contains(fix)) {
          fixes.add(fix);
        }
      }
      return fixes;
    }

    private static boolean isTopLevel(PsiElement symbol) {
      if (symbol instanceof PyClass) {
        return ((PyClass) symbol).isTopLevel();
      }
      if (symbol instanceof PyFunction) {
        return ((PyFunction) symbol).isTopLevel();
      }
      return false;
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
    public void visitPyTargetExpression(PyTargetExpression node) {
      final PyExpression qualifier = node.getQualifier();
      if (qualifier != null) {
        qualifier.accept(this);
      }
    }

    @Override
    public void visitPyImportElement(PyImportElement node) {
      super.visitPyImportElement(node);
      final PyFromImportStatement fromImport = PsiTreeUtil.getParentOfType(node, PyFromImportStatement.class);
      if (fromImport == null || !fromImport.isFromFuture()) {
        myAllImports.add(node);
      }
    }

    @Override
    public void visitPyStarImportElement(PyStarImportElement node) {
      super.visitPyStarImportElement(node);
      myAllImports.add(node);
    }

    @Override
    public void visitPyElement(final PyElement node) {
      super.visitPyElement(node);
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
          final ResolveResult[] resolveResults = poly.multiResolve(false);
          unresolved = (resolveResults.length == 0);
          for (ResolveResult resolveResult : resolveResults) {
            if (resolveResult instanceof ImportedResolveResult) {
              myUsedImports.addAll(((ImportedResolveResult)resolveResult).getNameDefiners());
            }
          }
        }
        else {
          unresolved = (reference.resolve() == null);
        }
        if (unresolved) {
          registerUnresolvedReferenceProblem(node, reference, severity);
          // don't highlight unresolved imports as unused
          if (node.getParent() instanceof PyImportElement) {
            myAllImports.remove(node.getParent());
          }
        }
      }
    }

    private static final Matcher IN_GLOBAL = new ParentMatcher(PyGlobalStatement.class).limitBy(PyStatement.class);

    private void registerUnresolvedReferenceProblem(PyElement node, PsiReference reference, HighlightSeverity severity) {
      final StringBuilder description_buf = new StringBuilder(""); // TODO: clear description_buf logic. maybe a flag is needed instead.
      final String text = reference.getElement().getText();
      final String ref_text = reference.getRangeInElement().substring(text); // text of the part we're working with
      final PsiElement ref_element = reference.getElement();
      final boolean ref_is_importable = SyntaxMatchers.IN_IMPORT.search(ref_element) == null && IN_GLOBAL.search(ref_element) == null;
      final List<LocalQuickFix> actions = new ArrayList<LocalQuickFix>(2);
      HintAction hint_action = null;
      if (ref_text.length() <= 0) return; // empty text, nothing to highlight
      if (reference.getElement() instanceof PyReferenceExpression) {
        PyReferenceExpression refex = (PyReferenceExpression)reference.getElement();
        String refname = refex.getReferencedName();
        if (refex.getQualifier() != null) {
          final PyClassType object_type = PyBuiltinCache.getInstance(node).getObjectType();
          if ((object_type != null) && object_type.getPossibleInstanceMembers().contains(refname)) return;

        }
        // unqualified:
        // may be module's
        if (PyModuleType.getPossibleInstanceMembers().contains(refname)) return;
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
        if (ref_is_importable) {
          Collection<LocalQuickFix> import_fixes = proposeImportFixes(node, ref_text);
          if (import_fixes.size() > 0) {
            actions.addAll(import_fixes);
            Object first_action = import_fixes.iterator().next();
            if (first_action instanceof HintAction) {
              hint_action = ((HintAction)first_action);
            }
          }
        }
      }
      if (reference instanceof PsiReferenceEx) {
        final String s = ((PsiReferenceEx)reference).getUnresolvedDescription();
        if (s != null) description_buf.append(s);
      }
      if (description_buf.length() == 0) {
        boolean marked_qualified = false;
        if (reference.getElement() instanceof PyQualifiedExpression) {
          final PyExpression qexpr = ((PyQualifiedExpression)reference.getElement()).getQualifier();
          if (qexpr != null) {
            PyType qtype = qexpr.getType(TypeEvalContext.fast());
            if (qtype != null) {
              if (qtype instanceof PyNoneType || qtype instanceof PyTypeReference) {
                // this almost always means that we don't know the type, so don't show an error in this case
                return;
              }
              if (qtype instanceof PyClassType) {
                PyClass cls = ((PyClassType)qtype).getPyClass();
                if (cls != null) {
                  if (overridesGetAttr(cls)) {
                    return;
                  }
                  if (cls.findProperty(ref_text) != null) {
                    return; // a property exists but accessor is not found; other inspections handle this
                  }
                  if (! PyBuiltinCache.getInstance(node).hasInBuiltins(cls)) {
                    if (reference.getElement().getParent() instanceof PyCallExpression) {
                      actions.add(new AddMethodQuickFix(ref_text, (PyClassType)qtype));
                    }
                    else actions.add(new AddFieldQuickFix(ref_text, cls));
                  }
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
          // add import hint; the rest of action will fend for itself.
          if (ref_element != null && ref_is_importable && hint_action == null) {
            final AddImportAction addImportAction = new AddImportAction(reference);
            if (addImportAction.hasSomethingToImport(ref_element.getContainingFile())) {
              actions.add(addImportAction);
            }
          }
          if (ref_text.length() > 2 && Character.isUpperCase(ref_text.charAt(0)) && !Character.isUpperCase(ref_text.charAt(1))) {
            actions.add(new CreateClassQuickFix(ref_text, reference.getElement()));
          }
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

      if (GenerateBinaryStubsFix.isApplicable(reference)) {
        actions.add(new GenerateBinaryStubsFix(reference));
      }
      addPluginQuickFixes(reference, actions);

      PsiElement point = node.getLastChild(); // usually the identifier at the end of qual ref
      if (point == null) point = node;
      registerProblem(point, description, hl_type, null, actions.toArray(new LocalQuickFix[actions.size()]));
    }

    private static boolean overridesGetAttr(PyClass cls) {
      PyFunction method = cls.findMethodByName(PyNames.GETATTR, true);
      if (method != null) {
        return true;
      }
      method = cls.findMethodByName(PyNames.GETATTRIBUTE, true);
      if (method != null && !PyBuiltinCache.getInstance(cls).hasInBuiltins(method)) {
        return true;
      }
      return false;
    }

    private static void addPluginQuickFixes(PsiReference reference, final List<LocalQuickFix> actions) {
      for(PyUnresolvedReferenceQuickFixProvider provider: Extensions.getExtensions(PyUnresolvedReferenceQuickFixProvider.EP_NAME)) {
        provider.registerQuickFixes(reference, new Consumer<LocalQuickFix>() {
          public void consume(LocalQuickFix localQuickFix) {
            actions.add(localQuickFix);
          }
        });
      }
    }

    public void highlightUnusedImports() {
      final List<PsiElement> unused = collectUnusedImportElements();
      for (PsiElement element : unused) {
        if (element.getTextLength() > 0) {
          registerProblem(element, "Unused import statement", ProblemHighlightType.LIKE_UNUSED_SYMBOL, null, new OptimizeImportsQuickFix());
        }
      }
    }

    private List<PsiElement> collectUnusedImportElements() {
      List<PsiElement> result = new ArrayList<PsiElement>();

      Set<NameDefiner> unusedImports = new HashSet<NameDefiner>(myAllImports);
      unusedImports.removeAll(myUsedImports);

      Set<PyImportStatementBase> unusedStatements = new HashSet<PyImportStatementBase>();
      for (NameDefiner unusedImport : unusedImports) {
        PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(unusedImport, PyImportStatementBase.class);
        if (importStatement != null && !unusedStatements.contains(importStatement)) {
          // don't remove as unused imports in try/except statements
          if (PsiTreeUtil.getParentOfType(importStatement, PyTryExceptStatement.class) != null) {
            continue;            
          }
          if (unusedImport instanceof PyStarImportElement || areAllImportsUnused(importStatement, unusedImports)) {
            unusedStatements.add(importStatement);
            result.add(importStatement);
          }
          else {
            result.add(unusedImport);
          }
        }
      }
      return result;
    }

    private static boolean areAllImportsUnused(PyImportStatementBase importStatement, Set<NameDefiner> unusedImports) {
      final PyImportElement[] elements = importStatement.getImportElements();
      for (PyImportElement element : elements) {
        if (!unusedImports.contains(element)) {
          return false;
        }
      }
      return true;
    }

    public void optimizeImports() {
      final List<PsiElement> elementsToDelete = collectUnusedImportElements();
      for (PsiElement element : elementsToDelete) {
        element.delete();
      }
    }
  }
}
