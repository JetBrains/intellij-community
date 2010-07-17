package com.jetbrains.python.inspections;

import com.intellij.codeInspection.*;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.*;
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
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.validation.PythonReferenceImporter;
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

    @Override
    public void visitPyTargetExpression(PyTargetExpression node) {
      final PyExpression qualifier = node.getQualifier();
      if (qualifier != null) {
        qualifier.accept(this);
        checkSlots(node);
      }
    }

    private void checkSlots(PyQualifiedExpression node) {
      final PyExpression qualifier = node.getQualifier();
      if (qualifier != null) {
        final PyType type = qualifier.getType(TypeEvalContext.fast());
        if (type instanceof PyClassType) {
          final PyClass pyClass = ((PyClassType)type).getPyClass();
          if (pyClass != null && pyClass.isNewStyleClass()) {
            final List<String> slots = pyClass.getSlots();
            if (slots != null && !slots.contains(node.getReferencedName())) {
              registerProblem(node, "'" + pyClass.getName() + "' object has no attribute '" + node.getReferencedName() + "'");
            }
          }
        }
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
          LocalQuickFix import_fix = PythonReferenceImporter.proposeImportFix(node, ref_text);
          if (import_fix != null) {
            actions.add(import_fix);
            if (import_fix instanceof HintAction) {
              hint_action = ((HintAction)import_fix);
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
              if (qtype instanceof PyNoneType || qtype instanceof PyTypeReference ||
                  (qtype instanceof PyUnionType && ((PyUnionType) qtype).isWeak())) {
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
