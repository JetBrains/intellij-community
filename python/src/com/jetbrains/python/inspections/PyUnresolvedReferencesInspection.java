package com.jetbrains.python.inspections;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.actions.*;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.imports.AutoImportHintAction;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.codeInsight.imports.OptimizeImportsQuickFix;
import com.jetbrains.python.codeInsight.imports.PythonReferenceImporter;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.types.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * Marks references that fail to resolve. Also tracks unused imports and provides "optimize imports" support.
 * User: dcheryasov
 * Date: Nov 15, 2008
 */
public class PyUnresolvedReferencesInspection extends PyInspection {
  private static Key<Visitor> KEY = Key.create("PyUnresolvedReferencesInspection.Visitor");

  public JDOMExternalizableStringList ignoredIdentifiers = new JDOMExternalizableStringList();

  public static PyUnresolvedReferencesInspection getInstance(PsiElement element) {
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(element.getProject()).getInspectionProfile();
    final LocalInspectionToolWrapper profileEntry =
      (LocalInspectionToolWrapper)inspectionProfile.getInspectionTool(PyUnresolvedReferencesInspection.class.getSimpleName(), element);
    return (PyUnresolvedReferencesInspection)profileEntry.getTool();
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unresolved.refs");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly, @NotNull final LocalInspectionToolSession session) {
    final Visitor visitor = new Visitor(holder, session, ignoredIdentifiers);
    // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
    final Visitor existingVisitor = session.getUserData(KEY);
    if (existingVisitor == null) {
      session.putUserData(KEY, visitor);
    }
    return visitor;
  }

  @Override
  public void inspectionFinished(LocalInspectionToolSession session) {
    final Visitor visitor = session.getUserData(KEY);
    assert visitor != null;
    if (PyCodeInsightSettings.getInstance().HIGHLIGHT_UNUSED_IMPORTS) {
      visitor.highlightUnusedImports();
    }
    session.putUserData(KEY, null);
  }

  @Override
  public JComponent createOptionsPanel() {
    ListEditForm form = new ListEditForm("Ignore identifiers", ignoredIdentifiers);
    return form.getContentPanel();
  }

  public static class Visitor extends PyInspectionVisitor {
    private Set<NameDefiner> myUsedImports = Collections.synchronizedSet(new HashSet<NameDefiner>());
    private Set<NameDefiner> myAllImports = Collections.synchronizedSet(new HashSet<NameDefiner>());
    private final ImmutableSet<String> myIgnoredIdentifiers;

    public Visitor(final ProblemsHolder holder, LocalInspectionToolSession session, List<String> ignoredIdentifiers) {
      super(holder, session);
      myIgnoredIdentifiers = ImmutableSet.copyOf(ignoredIdentifiers);
    }

    @Override
    public void visitPyTargetExpression(PyTargetExpression node) {
      checkSlots(node);
    }

    private void checkSlots(PyQualifiedExpression node) {
      final PyExpression qualifier = node.getQualifier();
      if (qualifier != null) {
        final PyType type = myTypeEvalContext.getType(qualifier);
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
      PsiFile file = node.getContainingFile();
      String fileName = FileUtil.getNameWithoutExtension(file.getName());
      if (fromImport == null && fileName.equals(node.getText())) {
        registerProblem(node, "Import resolves to its containing file.");
      }
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
          severity = ((PsiReferenceEx) reference).getUnresolvedHighlightSeverity(myTypeEvalContext);
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

    private void registerUnresolvedReferenceProblem(PyElement node, PsiReference reference, HighlightSeverity severity) {
      final StringBuilder description_buf = new StringBuilder(""); // TODO: clear description_buf logic. maybe a flag is needed instead.
      final String text = reference.getElement().getText();
      final String ref_text = reference.getRangeInElement().substring(text); // text of the part we're working with
      final PsiElement ref_element = reference.getElement();
      final boolean ref_is_importable = PythonReferenceImporter.isImportable(ref_element);
      final List<LocalQuickFix> actions = new ArrayList<LocalQuickFix>(2);
      HintAction hintAction = null;
      if (ref_text.length() <= 0) return; // empty text, nothing to highlight
      if (reference.getElement() instanceof PyReferenceExpression) {
        PyReferenceExpression refex = (PyReferenceExpression)reference.getElement();
        String refname = refex.getReferencedName();
        if (myIgnoredIdentifiers.contains(refname)) {
          return;
        }
        if (refex.getQualifier() != null) {
          final PyClassType object_type = PyBuiltinCache.getInstance(node).getObjectType();
          if ((object_type != null) && object_type.getPossibleInstanceMembers().contains(refname)) return;

        }
        else {
          if (LanguageLevel.forElement(node).isOlderThan(LanguageLevel.PYTHON26)) {
            if (refname.equals("with")) {
              actions.add(new UnresolvedRefAddFutureImportQuickFix(refex));
            }
          }
          PyClass containedClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
          if (containedClass != null) {
            for (PyTargetExpression target : containedClass.getInstanceAttributes()) {
              if (Comparing.strEqual(node.getName(), target.getName())) {
                actions.add(new UnresolvedReferenceAddSelfQuickFix(refex));
              }
            }
            for (PyStatement statement : containedClass.getStatementList().getStatements()) {
              if (statement instanceof PyAssignmentStatement) {
                if (((PyAssignmentStatement)statement).getLeftHandSideExpression().getText().equals(refex.getText())) {
                  PyExpression callexpr = ((PyAssignmentStatement)statement).getAssignedValue();
                  if (callexpr instanceof PyCallExpression) {
                    PyType type = myTypeEvalContext.getType(callexpr);
                    if (type != null && type instanceof PyClassType) {
                      String name = ((PyCallExpression)callexpr).getCallee().getText();
                      if (name != null && name.equals("property"))
                        actions.add(new UnresolvedReferenceAddSelfQuickFix(refex));
                    }
                  }
                }
              }
            }
            for (PyFunction method : containedClass.getMethods()) {
              Property property = method.getProperty();
              if (property != null && method.getName().equals(refex.getText())) {
                actions.add(new UnresolvedReferenceAddSelfQuickFix(refex));
              }
            }
          }
          PyCallExpression callExpression = PsiTreeUtil.getParentOfType(ref_element, PyCallExpression.class);
          if (callExpression != null)
            actions.add(new UnresolvedRefCreateFunctionQuickFix(callExpression, refex));
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
          severity = HighlightSeverity.WEAK_WARNING;
          String errmsg = PyBundle.message("INSP.module.$0.not.found", ref_text);
          description_buf.append(errmsg);
          // TODO: mark the node so that future references pointing to it won't result in a error, but in a warning
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
            PyType qtype = myTypeEvalContext.getType(qexpr);
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
                    else actions.add(new AddFieldQuickFix(ref_text, cls, "None"));
                  }
                }
                description_buf.append(PyBundle.message("INSP.unresolved.ref.$0.for.class.$1", ref_text, qtype.getName()));
                marked_qualified = true;
              }
              else if (qtype instanceof PyModuleType) {
                PsiFile file = ((PyModuleType)qtype).getModule();
                if (file instanceof PyFile) {
                  actions.add(new AddFunctionQuickFix(ref_text, (PyFile)file));
                }
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
          if (ref_text.equals("true") || ref_text.equals("false"))
            actions.add(new UnresolvedRefTrueFalseQuickFix(ref_element));

          // look in other imported modules for this whole name
          if (ref_is_importable) {
            AutoImportQuickFix importFix = PythonReferenceImporter.proposeImportFix(node, reference, ref_text);
            if (importFix != null) {
              // if the context doesn't look like a function call and we only found imports of functions, suggest auto-import
              // as a quickfix but no popup balloon (PY-2312)
              if ((isCall(node) || !importFix.hasOnlyFunctions()) && PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP) {
                final AutoImportHintAction autoImportHintAction = new AutoImportHintAction(importFix);
                actions.add(autoImportHintAction);
              }
              else {
                actions.add(importFix);
              }
            }
          }

          if (ref_text.length() > 2 && Character.isUpperCase(ref_text.charAt(0)) && !Character.isUpperCase(ref_text.charAt(1)) &&
              PsiTreeUtil.getParentOfType(ref_element, PyImportStatementBase.class) == null) {
            PsiElement anchor = reference.getElement();
            if (reference.getElement() instanceof PyQualifiedExpression) {
              final PyExpression qexpr = ((PyQualifiedExpression)reference.getElement()).getQualifier();
              if (qexpr != null) {
                final PyType type = myTypeEvalContext.getType(qexpr);
                if (type instanceof PyModuleType) {
                  anchor = ((PyModuleType) type).getModule();
                }
                else {
                  anchor = null;
                }
              }
              if (anchor != null) {
                actions.add(new CreateClassQuickFix(ref_text, anchor));
              }
            }
          }
        }
      }
      String description = description_buf.toString();
      ProblemHighlightType hl_type;
      if (severity == HighlightSeverity.WARNING) {
        hl_type = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      }
      /*
      else if (severity == HighlightSeverity.ERROR) {
        hl_type = ProblemHighlightType.ERROR;
      }
      */
      else {
        hl_type = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
      }

      if (GenerateBinaryStubsFix.isApplicable(reference)) {
        actions.add(new GenerateBinaryStubsFix(reference));
      }
      actions.add(new AddIgnoredIdentifierFix(ref_text));
      addPluginQuickFixes(reference, actions);

      PsiElement point = node.getLastChild(); // usually the identifier at the end of qual ref
      if (point == null) point = node;
      TextRange range = reference.getRangeInElement().shiftRight(-point.getStartOffsetInParent());
      registerProblem(point, description, hl_type, null, range, actions.toArray(new LocalQuickFix[actions.size()]));
    }

    private static boolean isCall(PyElement node) {
      final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(node, PyCallExpression.class);
      return callExpression != null && node == callExpression.getCallee();
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
      if (myAllImports.isEmpty()){
        return Collections.emptyList();
      }
      // PY-1315 Unused imports inspection shouldn't work in python repl console
      final NameDefiner first = myAllImports.iterator().next();
      if (first.getContainingFile() instanceof PyExpressionCodeFragment || PydevConsoleRunner.isInPydevConsole(first)){
        return Collections.emptyList();
      }
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
          if (unusedImport instanceof PyImportElement) {
            if (ResolveImportUtil.resolveImportElement((PyImportElement)unusedImport) == null) {
              continue;
            }
          }
          else {
            assert importStatement instanceof PyFromImportStatement;
            if (ResolveImportUtil.resolveFromImportStatementSource((PyFromImportStatement)importStatement) == null) {
              continue;
            }
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
