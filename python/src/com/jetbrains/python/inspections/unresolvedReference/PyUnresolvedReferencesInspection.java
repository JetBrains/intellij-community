// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections.unresolvedReference;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.ControlFlowUtil;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.*;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyCustomType;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.codeInsight.PySubstitutionChunkReference;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.imports.AutoImportHintAction;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.codeInsight.imports.OptimizeImportsQuickFix;
import com.jetbrains.python.codeInsight.imports.PythonImportUtils;
import com.jetbrains.python.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.documentation.docstrings.DocStringParameterReference;
import com.jetbrains.python.documentation.docstrings.DocStringTypeReference;
import com.jetbrains.python.inspections.*;
import com.jetbrains.python.inspections.quickfix.*;
import com.jetbrains.python.packaging.PyPIPackageUtil;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.packaging.PyRequirement;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.references.PyImportReference;
import com.jetbrains.python.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

import static com.jetbrains.python.inspections.quickfix.AddIgnoredIdentifierQuickFix.END_WILDCARD;

/**
 * Marks references that fail to resolve. Also tracks unused imports and provides "optimize imports" support.
 * User: dcheryasov
 */
public class PyUnresolvedReferencesInspection extends PyInspection {
  private static final Key<Visitor> KEY = Key.create("PyUnresolvedReferencesInspection.Visitor");
  public static final Key<PyUnresolvedReferencesInspection> SHORT_NAME_KEY =
    Key.create(PyUnresolvedReferencesInspection.class.getSimpleName());

  public List<String> ignoredIdentifiers = new ArrayList<>();

  public static PyUnresolvedReferencesInspection getInstance(PsiElement element) {
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
    return (PyUnresolvedReferencesInspection)inspectionProfile.getUnwrappedTool(SHORT_NAME_KEY.toString(), element);
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.unresolved.refs");
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    final Visitor visitor = new Visitor(holder, session, ignoredIdentifiers);
    // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
    final Visitor existingVisitor = session.getUserData(KEY);
    if (existingVisitor == null) {
      session.putUserData(KEY, visitor);
    }
    return visitor;
  }

  @Override
  public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder holder) {
    final Visitor visitor = session.getUserData(KEY);
    assert visitor != null;
    if (PyCodeInsightSettings.getInstance().HIGHLIGHT_UNUSED_IMPORTS) {
      visitor.highlightUnusedImports();
    }
    visitor.highlightImportsInsideGuards();
    session.putUserData(KEY, null);
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListEditForm form = new ListEditForm("Ignore references", ignoredIdentifiers);
    return form.getContentPanel();
  }

  public static class Visitor extends PyInspectionVisitor {

    private Set<PyImportedNameDefiner> myImportsInsideGuard = Collections.synchronizedSet(new HashSet<PyImportedNameDefiner>());
    private Set<PyImportedNameDefiner> myUsedImports = Collections.synchronizedSet(new HashSet<PyImportedNameDefiner>());
    private Set<PyImportedNameDefiner> myAllImports = Collections.synchronizedSet(new HashSet<PyImportedNameDefiner>());
    private final ImmutableSet<String> myIgnoredIdentifiers;
    private volatile Boolean myIsEnabled = null;

    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session, List<String> ignoredIdentifiers) {
      super(holder, session);
      myIgnoredIdentifiers = ImmutableSet.copyOf(ignoredIdentifiers);
    }

    public boolean isEnabled(@NotNull PsiElement anchor) {
      if (myIsEnabled == null) {
        final boolean isPyCharm = PlatformUtils.isPyCharm();
        if (PySkeletonRefresher.isGeneratingSkeletons()) {
          myIsEnabled = false;
        }
        else if (isPyCharm) {
          myIsEnabled = PythonSdkType.findPythonSdk(anchor) != null || PyUtil.isInScratchFile(anchor);
        }
        else {
          myIsEnabled = true;
        }
      }
      return myIsEnabled;
    }

    @Override
    public void visitPyTargetExpression(PyTargetExpression node) {
      checkSlotsAndProperties(node);
    }

    private void checkSlotsAndProperties(PyQualifiedExpression node) {
      final PyExpression qualifier = node.getQualifier();
      if (qualifier != null) {
        final PyType type = myTypeEvalContext.getType(qualifier);
        if (type instanceof PyClassType) {
          final PyClass pyClass = ((PyClassType)type).getPyClass();
          if (pyClass.isNewStyleClass(myTypeEvalContext)) {
            if (pyClass.getOwnSlots() == null) {
              return;
            }
            final String attrName = node.getReferencedName();
            if (!canHaveAttribute(pyClass, attrName)) {
              for (PyClass ancestor : pyClass.getAncestorClasses(myTypeEvalContext)) {
                if (ancestor == null) {
                  return;
                }
                if (PyNames.OBJECT.equals(ancestor.getName())) {
                  break;
                }
                if (canHaveAttribute(ancestor, attrName)) {
                  return;
                }
              }
              final ASTNode nameNode = node.getNameElement();
              final PsiElement e = nameNode != null ? nameNode.getPsi() : node;
              registerProblem(e, "'" + pyClass.getName() + "' object has no attribute '" + attrName + "'");
            }
          }
        }
      }
    }

    private boolean canHaveAttribute(@NotNull PyClass cls, @Nullable String attrName) {
      final List<String> slots = cls.getOwnSlots();

      // Class instance can contain attributes with arbitrary names
      if (slots == null || slots.contains(PyNames.DICT)) {
        return true;
      }

      if (attrName != null && cls.findClassAttribute(attrName, true, myTypeEvalContext) != null) {
        return true;
      }

      return slots.contains(attrName) || cls.getProperties().containsKey(attrName);
    }

    @Override
    public void visitPyImportElement(PyImportElement node) {
      super.visitPyImportElement(node);
      final PyFromImportStatement fromImport = PsiTreeUtil.getParentOfType(node, PyFromImportStatement.class);
      if (isEnabled(node) && (fromImport == null || !fromImport.isFromFuture())) {
        myAllImports.add(node);
      }
    }

    @Override
    public void visitPyStarImportElement(PyStarImportElement node) {
      super.visitPyStarImportElement(node);
      if (isEnabled(node)) {
        myAllImports.add(node);
      }
    }

    @Nullable
    private static PyExceptPart getImportErrorGuard(PyElement node) {
      final PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(node, PyImportStatementBase.class);
      if (importStatement != null) {
        final PyTryPart tryPart = PsiTreeUtil.getParentOfType(node, PyTryPart.class);
        if (tryPart != null) {
          final PyTryExceptStatement tryExceptStatement = PsiTreeUtil.getParentOfType(tryPart, PyTryExceptStatement.class);
          if (tryExceptStatement != null) {
            for (PyExceptPart exceptPart : tryExceptStatement.getExceptParts()) {
              final PyExpression expr = exceptPart.getExceptClass();
              if (expr != null && "ImportError".equals(expr.getName())) {
                return exceptPart;
              }
            }
          }
        }
      }
      return null;
    }

    private static boolean isGuardedByHasattr(@NotNull final PyElement node, @NotNull final String name) {
      final String nodeName = node.getName();
      if (nodeName != null) {
        final ScopeOwner owner = ScopeUtil.getDeclarationScopeOwner(node, nodeName);
        PyElement e = PsiTreeUtil.getParentOfType(node, PyConditionalStatementPart.class, PyConditionalExpression.class);
        while (e != null && PsiTreeUtil.isAncestor(owner, e, true)) {
          final ArrayList<PyCallExpression> calls = new ArrayList<>();
          PyExpression cond = null;
          if (e instanceof PyConditionalStatementPart) {
            cond = ((PyConditionalStatementPart)e).getCondition();
          }
          else if (e instanceof PyConditionalExpression && PsiTreeUtil.isAncestor(((PyConditionalExpression)e).getTruePart(), node, true)) {
            cond = ((PyConditionalExpression)e).getCondition();
          }
          if (cond instanceof PyCallExpression) {
            calls.add((PyCallExpression)cond);
          }
          if (cond != null) {
            final PyCallExpression[] callExpressions = PsiTreeUtil.getChildrenOfType(cond, PyCallExpression.class);
            if (callExpressions != null) {
              calls.addAll(Arrays.asList(callExpressions));
            }
            for (PyCallExpression call : calls) {
              final PyExpression callee = call.getCallee();
              final PyExpression[] args = call.getArguments();
              // TODO: Search for `node` aliases using aliases analysis
              if (callee != null && "hasattr".equals(callee.getName()) && args.length == 2 &&
                  nodeName.equals(args[0].getName()) && args[1] instanceof PyStringLiteralExpression &&
                  ((PyStringLiteralExpression)args[1]).getStringValue().equals(name)) {
                return true;
              }
            }
          }
          e = PsiTreeUtil.getParentOfType(e, PyConditionalStatementPart.class);
        }
      }
      return false;
    }

    @Override
    public void visitComment(PsiComment comment) {
      super.visitComment(comment);
      if (comment instanceof PsiLanguageInjectionHost) {
        processInjection((PsiLanguageInjectionHost)comment);
      }
    }

    @Override
    public void visitPyElement(final PyElement node) {
      super.visitPyElement(node);
      final PsiFile file = node.getContainingFile();
      final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(node.getProject());
      if (injectedLanguageManager.isInjectedFragment(file)) {
        final PsiLanguageInjectionHost host = injectedLanguageManager.getInjectionHost(node);
        processInjection(host);
      }
      if (node instanceof PyReferenceOwner) {
        final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext);
        processReference(node, ((PyReferenceOwner)node).getReference(resolveContext));
      }
      else {
        if (node instanceof PsiLanguageInjectionHost) {
          processInjection((PsiLanguageInjectionHost)node);
        }
        for (final PsiReference reference : node.getReferences()) {
          processReference(node, reference);
        }
      }
    }

    private void processInjection(@Nullable PsiLanguageInjectionHost node) {
      if (node == null) return;
      final List<Pair<PsiElement, TextRange>> files = InjectedLanguageManager.getInstance(node.getProject()).getInjectedPsiFiles(node);
      if (files != null) {
        for (Pair<PsiElement, TextRange> pair : files) {
          new PyRecursiveElementVisitor() {
            @Override
            public void visitPyElement(PyElement element) {
              super.visitPyElement(element);
              if (element instanceof PyReferenceOwner) {
                final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(myTypeEvalContext);
                final PsiPolyVariantReference reference = ((PyReferenceOwner)element).getReference(resolveContext);
                markTargetImportsAsUsed(reference);
              }
            }
          }.visitElement(pair.getFirst());
        }
      }
    }

    private void markTargetImportsAsUsed(@NotNull PsiPolyVariantReference reference) {
      final ResolveResult[] resolveResults = reference.multiResolve(false);
      for (ResolveResult resolveResult : resolveResults) {
        if (resolveResult instanceof ImportedResolveResult) {
          final PyImportedNameDefiner definer = ((ImportedResolveResult)resolveResult).getDefiner();
          if (definer != null) {
            myUsedImports.add(definer);
          }
        }
      }
    }

    private void processReference(@NotNull PyElement node, @Nullable PsiReference reference) {
      if (!isEnabled(node) || reference == null || reference.isSoft()) {
        return;
      }
      final PyExceptPart guard = getImportErrorGuard(node);
      if (guard != null) {
        processReferenceInImportGuard(node, guard);
        return;
      }
      if (node instanceof PyQualifiedExpression) {
        final PyQualifiedExpression qExpr = (PyQualifiedExpression)node;
        final PyExpression qualifier = qExpr.getQualifier();
        final String name = node.getName();
        if (qualifier != null && name != null && isGuardedByHasattr(qualifier, name)) {
          return;
        }
      }
      PsiElement target = null;
      final boolean unresolved;
      if (reference instanceof PsiPolyVariantReference) {
        final PsiPolyVariantReference poly = (PsiPolyVariantReference)reference;
        final ResolveResult[] resolveResults = poly.multiResolve(false);
        unresolved = (resolveResults.length == 0);
        for (ResolveResult resolveResult : resolveResults) {
          if (target == null && resolveResult.isValidResult()) {
            target = resolveResult.getElement();
          }
          if (resolveResult instanceof ImportedResolveResult) {
            final PyImportedNameDefiner definer = ((ImportedResolveResult)resolveResult).getDefiner();
            if (definer != null) {
              myUsedImports.add(definer);
            }
          }
        }
      }
      else {
        target = reference.resolve();
        unresolved = (target == null);
      }
      if (unresolved) {
        boolean ignoreUnresolved = false;
        for (PyInspectionExtension extension : Extensions.getExtensions(PyInspectionExtension.EP_NAME)) {
          if (extension.ignoreUnresolvedReference(node, reference, myTypeEvalContext)) {
            ignoreUnresolved = true;
            break;
          }
        }
        if (!ignoreUnresolved) {
          final HighlightSeverity severity = reference instanceof PsiReferenceEx
                                             ? ((PsiReferenceEx)reference).getUnresolvedHighlightSeverity(myTypeEvalContext)
                                             : HighlightSeverity.ERROR;
          if (severity == null) return;
          registerUnresolvedReferenceProblem(node, reference, severity);
        }
        // don't highlight unresolved imports as unused
        if (node.getParent() instanceof PyImportElement) {
          myAllImports.remove(node.getParent());
        }
      }
      else if (reference instanceof PyImportReference &&
               target == reference.getElement().getContainingFile() &&
               !isContainingFileImportAllowed(node, (PsiFile)target)) {
        registerProblem(node, "Import resolves to its containing file");
      }
    }

    private static boolean isContainingFileImportAllowed(PyElement node, PsiFile target) {
      return PyImportStatementNavigator.getImportStatementByElement(node) == null && target.getName().equals(PyNames.INIT_DOT_PY);
    }

    private void processReferenceInImportGuard(@NotNull PyElement node, @NotNull PyExceptPart guard) {
      final PyImportElement importElement = PsiTreeUtil.getParentOfType(node, PyImportElement.class);
      if (importElement != null) {
        final String visibleName = importElement.getVisibleName();
        final ScopeOwner owner = ScopeUtil.getScopeOwner(importElement);
        if (visibleName != null && owner != null) {
          final Collection<PsiElement> allWrites = ScopeUtil.getReadWriteElements(visibleName, owner, false, true);
          final boolean hasWriteInsideGuard = allWrites.stream().anyMatch(e -> PsiTreeUtil.isAncestor(guard, e, false));
          if (!hasWriteInsideGuard && !shouldSkipMissingWriteInsideGuard(guard, visibleName)) {
            myImportsInsideGuard.add(importElement);
          }
        }
      }
    }

    private static boolean shouldSkipMissingWriteInsideGuard(@NotNull PyExceptPart guard, @NotNull String name) {
      return isDefinedInParentScope(name, guard) ||
             PyBuiltinCache.getInstance(guard).getByName(name) != null ||
             controlFlowAlwaysTerminatesInsideGuard(guard);
    }

    private static boolean isDefinedInParentScope(@NotNull String name, @NotNull PsiElement anchor) {
      return ScopeUtil.getDeclarationScopeOwner(ScopeUtil.getScopeOwner(anchor), name) != null;
    }

    private static boolean controlFlowAlwaysTerminatesInsideGuard(@NotNull PyExceptPart guard) {
      final ScopeOwner owner = ScopeUtil.getScopeOwner(guard);
      if (owner == null) return false;
      final ControlFlow flow = ControlFlowCache.getControlFlow(owner);
      final Instruction[] instructions = flow.getInstructions();
      final int start = ControlFlowUtil.findInstructionNumberByElement(instructions, guard.getExceptClass());
      if (start <= 0) return false;
      final Ref<Boolean> canEscapeGuard = Ref.create(false);
      ControlFlowUtil.process(instructions, start, instruction -> {
        final PsiElement e = instruction.getElement();
        if (e != null && !PsiTreeUtil.isAncestor(guard, e, true)) {
          canEscapeGuard.set(true);
          return false;
        }
        return true;
      });
      return !canEscapeGuard.get();
    }

    private void registerUnresolvedReferenceProblem(@NotNull PyElement node, @NotNull final PsiReference reference,
                                                    @NotNull HighlightSeverity severity) {
      if (reference instanceof DocStringTypeReference) {
        return;
      }
      String description = null;
      PsiElement element = reference.getElement();
      final String text = element.getText();
      TextRange rangeInElement = reference.getRangeInElement();
      String refText = text;  // text of the part we're working with
      if (rangeInElement.getStartOffset() > 0 && rangeInElement.getEndOffset() > 0) {
        refText = rangeInElement.substring(text);
      }

      final List<LocalQuickFix> actions = new ArrayList<>(2);
      final String refName = (element instanceof PyQualifiedExpression) ? ((PyQualifiedExpression)element).getReferencedName() : refText;
      // Empty text, nothing to highlight
      if (refName == null || refName.length() <= 0) {
        return;
      }

      final List<QualifiedName> qualifiedNames = getCanonicalNames(reference, myTypeEvalContext);
      for (QualifiedName name : qualifiedNames) {
        final String canonicalName = name.toString();
        for (String ignored : myIgnoredIdentifiers) {
          if (ignored.endsWith(END_WILDCARD)) {
            final String prefix = ignored.substring(0, ignored.length() - END_WILDCARD.length());
            if (canonicalName.startsWith(prefix)) {
              return;
            }
          }
          else if (canonicalName.equals(ignored)) {
            return;
          }
        }
      }
      // Legacy non-qualified ignore patterns
      if (myIgnoredIdentifiers.contains(refName)) {
        return;
      }

      if (element instanceof PyReferenceExpression) {
        PyReferenceExpression expr = (PyReferenceExpression)element;
        if (PyNames.COMPARISON_OPERATORS.contains(refName)) {
          return;
        }
        if (!expr.isQualified()) {
          if (PyUnreachableCodeInspection.hasAnyInterruptedControlFlowPaths(expr)) {
            return;
          }
          if (refText.equals("true") || refText.equals("false")) {
            actions.add(new UnresolvedRefTrueFalseQuickFix(element));
          }
          addAddSelfFix(node, expr, actions);
          PyCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PyCallExpression.class);
          if (callExpression != null && (!(callExpression.getCallee() instanceof PyQualifiedExpression) ||
                                         ((PyQualifiedExpression)callExpression.getCallee()).getQualifier() == null)) {
            actions.add(new UnresolvedRefCreateFunctionQuickFix(callExpression, expr));
          }
          final PyFunction parentFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
          final PyDecorator decorator = PsiTreeUtil.getParentOfType(element, PyDecorator.class);
          final PyAnnotation annotation = PsiTreeUtil.getParentOfType(element, PyAnnotation.class);
          final PyImportStatement importStatement = PsiTreeUtil.getParentOfType(element, PyImportStatement.class);
          if (parentFunction != null && decorator == null && annotation == null && importStatement == null) {
            actions.add(new UnresolvedReferenceAddParameterQuickFix(refName));
          }
          actions.add(new PyRenameUnresolvedRefQuickFix());
        }
        // unqualified:
        // may be module's
        if (PyModuleType.getPossibleInstanceMembers().contains(refName)) {
          return;
        }
        // may be a "try: import ..."; not an error not to resolve
        if ((
          PsiTreeUtil.getParentOfType(
            PsiTreeUtil.getParentOfType(node, PyImportElement.class), PyTryExceptStatement.class, PyIfStatement.class
          ) != null
        )) {
          severity = HighlightSeverity.WEAK_WARNING;
          description = PyBundle.message("INSP.module.$0.not.found", refText);
          // TODO: mark the node so that future references pointing to it won't result in a error, but in a warning
        }
      }
      if (reference instanceof PsiReferenceEx && description == null) {
        description = ((PsiReferenceEx)reference).getUnresolvedDescription();
      }
      if (description == null) {
        boolean markedQualified = false;
        if (element instanceof PyQualifiedExpression) {
          // TODO: Add __qualname__ for Python 3.3 to the skeleton of <class 'object'>, introduce a pseudo-class skeleton for
          // <class 'function'>
          if ("__qualname__".equals(refText) && LanguageLevel.forElement(element).isAtLeast(LanguageLevel.PYTHON33)) {
            return;
          }
          final PyQualifiedExpression expr = (PyQualifiedExpression)element;
          if (PyNames.COMPARISON_OPERATORS.contains(expr.getReferencedName())) {
            return;
          }
          final PyExpression qualifier = expr.getQualifier();
          if (qualifier != null) {
            PyType type = myTypeEvalContext.getType(qualifier);
            if (type != null) {
              if (ignoreUnresolvedMemberForType(type, reference, refName)) {
                return;
              }
              addCreateMemberFromUsageFixes(type, reference, refText, actions);
              if (type instanceof PyClassType) {
                final PyClassType classType = (PyClassType)type;
                if (reference instanceof PyOperatorReference) {
                  String className = type.getName();
                  if (classType.isDefinition()) {
                    final PyClassLikeType metaClassType = classType.getMetaClassType(myTypeEvalContext, true);
                    if (metaClassType != null) {
                      className = metaClassType.getName();
                    }
                  }
                  description = PyBundle.message("INSP.unresolved.operator.ref",
                                                 className, refName,
                                                 ((PyOperatorReference)reference).getReadableOperatorName());
                }
                else {
                  final List<String> slots = classType.getPyClass().getOwnSlots();

                  if (slots != null && slots.contains(refName)) {
                    return;
                  }

                  description = PyBundle.message("INSP.unresolved.ref.$0.for.class.$1", refText, type.getName());
                }
                markedQualified = true;
              }
              else {
                description = PyBundle.message("INSP.cannot.find.$0.in.$1", refText, type.getName());
                markedQualified = true;
              }
            }
          }
        }
        if (!markedQualified) {
          description = PyBundle.message("INSP.unresolved.ref.$0", refText);

          // look in other imported modules for this whole name
          if (PythonImportUtils.isImportable(element)) {
            addAutoImportFix(node, reference, actions);
          }

          addCreateClassFix(refText, element, actions);
        }
      }
      ProblemHighlightType hl_type;
      if (severity == HighlightSeverity.WARNING) {
        hl_type = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
      }
      else if (severity == HighlightSeverity.ERROR) {
        hl_type = ProblemHighlightType.GENERIC_ERROR;
      }
      else {
        hl_type = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
      }

      PyImportStatementBase importStatementBase = PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class);
      if ((importStatementBase != null) && GenerateBinaryStubsFix.isApplicable(importStatementBase)) {
        actions.addAll(GenerateBinaryStubsFix.generateFixes(importStatementBase));
      }
      if (qualifiedNames.size() == 1) {
        final QualifiedName qualifiedName = qualifiedNames.get(0);
        actions.add(new AddIgnoredIdentifierQuickFix(qualifiedName, false));
        if (qualifiedName.getComponentCount() > 1) {
          actions.add(new AddIgnoredIdentifierQuickFix(qualifiedName.removeLastComponent(), true));
        }
      }
      addPluginQuickFixes(reference, actions);

      if (reference instanceof PyImportReference) {
        // TODO: Ignore references in the second part of the 'from ... import ...' expression
        final QualifiedName qname = QualifiedName.fromDottedString(refName);
        final List<String> components = qname.getComponents();
        if (!components.isEmpty()) {
          final String packageName = components.get(0);
          final Module module = ModuleUtilCore.findModuleForPsiElement(node);
          final Sdk sdk = PythonSdkType.findPythonSdk(module);
          if (module != null && sdk != null && PyPackageUtil.packageManagementEnabled(sdk)) {
            StreamEx
              .of(packageName)
              .append(PyPIPackageUtil.PACKAGES_TOPLEVEL.getOrDefault(packageName, Collections.emptyList()))
              .filter(PyPIPackageUtil.INSTANCE::isInPyPI)
              .forEach(pkg -> addInstallPackageAction(actions, pkg, module, sdk));
          }
        }
      }

      if (reference instanceof PySubstitutionChunkReference) {
        return;
      }

      registerProblem(node, description, hl_type, null, rangeInElement, actions.toArray(new LocalQuickFix[actions.size()]));
    }

    private static void addInstallPackageAction(List<LocalQuickFix> actions, String packageName, Module module, Sdk sdk) {
      final List<PyRequirement> requirements = Collections.singletonList(new PyRequirement(packageName));
      final String name = "Install package " + packageName;
      actions.add(new PyPackageRequirementsInspection.PyInstallRequirementsFix(name, module, sdk, requirements));
    }

    /**
     * Return the canonical qualified names for a reference (even for an unresolved one).
     * If reference is qualified and its qualifier has union type, all possible canonical names will be returned.
     */
    @NotNull
    private static List<QualifiedName> getCanonicalNames(@NotNull PsiReference reference, @NotNull TypeEvalContext context) {
      final PsiElement element = reference.getElement();
      final List<QualifiedName> result = new SmartList<>();
      if (reference instanceof PyOperatorReference && element instanceof PyQualifiedExpression) {
        final PyExpression receiver = ((PyOperatorReference)reference).getReceiver();
        if (receiver != null) {
          final PyType type = context.getType(receiver);
          if (type instanceof PyClassType) {
            final String methodName = ((PyQualifiedExpression)element).getReferencedName();
            ContainerUtil.addIfNotNull(result, extractAttributeQNameFromClassType(methodName, (PyClassType)type));
          }
        }
      }
      else if (element instanceof PyReferenceExpression) {
        final PyReferenceExpression expr = (PyReferenceExpression)element;
        final PyExpression qualifier = expr.getQualifier();
        final String exprName = expr.getName();
        if (exprName != null) {
          if (qualifier != null) {
            final PyType type = context.getType(qualifier);
            if (type instanceof PyClassType) {
              ContainerUtil.addIfNotNull(result, extractAttributeQNameFromClassType(exprName, (PyClassType)type));
            }
            else if (type instanceof PyModuleType) {
              final PyFile file = ((PyModuleType)type).getModule();
              final QualifiedName name = QualifiedNameFinder.findCanonicalImportPath(file, element);
              if (name != null) {
                ContainerUtil.addIfNotNull(result, name.append(exprName));
              }
            }
            else if (type instanceof PyImportedModuleType) {
              final PyImportedModule module = ((PyImportedModuleType)type).getImportedModule();
              final PsiElement resolved = module.resolve();
              if (resolved != null) {
                final QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(resolved, element);
                if (path != null) {
                  ContainerUtil.addIfNotNull(result, path.append(exprName));
                }
              }
            }
            else if (type instanceof PyUnionType) {
              for (PyType memberType : ((PyUnionType)type).getMembers()) {
                if (memberType instanceof PyClassType) {
                  ContainerUtil.addIfNotNull(result, extractAttributeQNameFromClassType(exprName, (PyClassType)memberType));
                }
              }
            }
          }
          else {
            final PsiElement parent = element.getParent();
            if (parent instanceof PyImportElement) {
              final PyImportStatementBase importStmt = PsiTreeUtil.getParentOfType(parent, PyImportStatementBase.class);
              if (importStmt instanceof PyImportStatement) {
                ContainerUtil.addIfNotNull(result, QualifiedName.fromComponents(exprName));
              }
              else if (importStmt instanceof PyFromImportStatement) {
                final PsiElement resolved = ((PyFromImportStatement)importStmt).resolveImportSource();
                if (resolved != null) {
                  final QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(resolved, element);
                  if (path != null) {
                    ContainerUtil.addIfNotNull(result, path.append(exprName));
                  }
                }
              }
            }
            else {
              final QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(element, element);
              if (path != null) {
                ContainerUtil.addIfNotNull(result, path.append(exprName));
              }
            }
          }
        }
      }
      else if (reference instanceof DocStringParameterReference) {
        ContainerUtil.addIfNotNull(result, QualifiedName.fromDottedString(reference.getCanonicalText()));
      }
      return result;
    }

    private static QualifiedName extractAttributeQNameFromClassType(String exprName, PyClassType type) {
      final String name = type.getClassQName();
      if (name != null) {
        return QualifiedName.fromDottedString(name).append(exprName);
      }
      return null;
    }

    private boolean ignoreUnresolvedMemberForType(@NotNull PyType type, PsiReference reference, String name) {
      if (type instanceof PyNoneType || PyTypeChecker.isUnknown(type, myTypeEvalContext)) {
        // this almost always means that we don't know the type, so don't show an error in this case
        return true;
      }
      if (type instanceof PyStructuralType && ((PyStructuralType)type).isInferredFromUsages()) {
        return true;
      }
      if (type instanceof PyImportedModuleType) {
        PyImportedModule module = ((PyImportedModuleType)type).getImportedModule();
        if (module.resolve() == null) {
          return true;
        }
      }
      if (type instanceof PyCustomType) {
        // Skip custom member types that mimics another class with fuzzy parents
        for (final PyClassLikeType mimic : ((PyCustomType)type).getTypesToMimic()) {
          if (!(mimic instanceof PyClassType)) {
            continue;
          }
          if (PyUtil.hasUnresolvedAncestors(((PyClassType)mimic).getPyClass(), myTypeEvalContext)) {
            return true;
          }
        }
      }
      if (type instanceof PyClassType) {
        PyClass cls = ((PyClassType)type).getPyClass();
        if (PyTypeChecker.overridesGetAttr(cls, myTypeEvalContext)) {
          return true;
        }
        if (cls.findProperty(name, true, myTypeEvalContext) != null) {
          return true;
        }
        if (PyUtil.hasUnresolvedAncestors(cls, myTypeEvalContext)) {
          return true;
        }
        if (isDecoratedAsDynamic(cls, true)) {
          return true;
        }
        if (hasUnresolvedDynamicMember((PyClassType)type, reference, name, myTypeEvalContext)) return true;

        if (isAwaitOnGeneratorBasedCoroutine(name, reference, cls)) return true;
      }
      if (type instanceof PyFunctionTypeImpl) {
        final PyCallable callable = ((PyFunctionTypeImpl)type).getCallable();
        if (callable instanceof PyFunction && PyKnownDecoratorUtil.hasUnknownDecorator((PyFunction)callable, myTypeEvalContext)) {
          return true;
        }
      }
      if (type instanceof PyUnionType) {
        return ContainerUtil.exists(((PyUnionType)type).getMembers(), member -> ignoreUnresolvedMemberForType(member, reference, name));
      }
      for (PyInspectionExtension extension : Extensions.getExtensions(PyInspectionExtension.EP_NAME)) {
        if (extension.ignoreUnresolvedMember(type, name, myTypeEvalContext)) {
          return true;
        }
      }
      return false;
    }

    private static boolean hasUnresolvedDynamicMember(@NotNull final PyClassType type,
                                                      PsiReference reference,
                                                      @NotNull final String name, TypeEvalContext typeEvalContext) {

      final List<PyClassType> types = new ArrayList<>(Collections.singletonList(type));
      types.addAll(FluentIterable.from(type.getAncestorTypes(typeEvalContext)).filter(PyClassType.class).toList());


      for (final PyClassType typeToCheck : types) {
        for (PyClassMembersProvider provider : Extensions.getExtensions(PyClassMembersProvider.EP_NAME)) {
          final Collection<PyCustomMember> resolveResult = provider.getMembers(typeToCheck, reference.getElement(), typeEvalContext);
          for (PyCustomMember member : resolveResult) {
            if (member.getName().equals(name)) return true;
          }
        }
      }

      return false;
    }

    private boolean isDecoratedAsDynamic(@NotNull PyClass cls, boolean inherited) {
      if (inherited) {
        if (isDecoratedAsDynamic(cls, false)) {
          return true;
        }
        for (PyClass base : cls.getAncestorClasses(myTypeEvalContext)) {
          if (base != null && isDecoratedAsDynamic(base, false)) {
            return true;
          }
        }
      }
      else {
        if (PyKnownDecoratorUtil.hasUnknownDecorator(cls, myTypeEvalContext)) {
          return true;
        }
        final String docString = cls.getDocStringValue();
        if (docString != null && docString.contains("@DynamicAttrs")) {
          return true;
        }
      }
      return false;
    }

    private boolean isAwaitOnGeneratorBasedCoroutine(@NotNull String name, @NotNull PsiReference reference, @NotNull PyClass cls) {
      if (PyNames.DUNDER_AWAIT.equals(name) &&
          reference instanceof PyOperatorReference &&
          PyTypingTypeProvider.GENERATOR.equals(cls.getQualifiedName())) {
        final PyExpression receiver = ((PyOperatorReference)reference).getReceiver();

        if (receiver instanceof PyCallExpression) {
          final boolean resolvedToGeneratorBasedCoroutine = StreamEx
            .of(((PyCallExpression)receiver).multiResolveCalleeFunction(getResolveContext()))
            .select(PyFunction.class)
            .anyMatch(function -> PyKnownDecoratorUtil.hasGeneratorBasedCoroutineDecorator(function, myTypeEvalContext));

          if (resolvedToGeneratorBasedCoroutine) return true;
        }
      }

      return false;
    }

    private void addCreateMemberFromUsageFixes(PyType type, PsiReference reference, String refText, List<LocalQuickFix> actions) {
      PsiElement element = reference.getElement();
      if (type instanceof PyClassTypeImpl) {
        PyClass cls = ((PyClassType)type).getPyClass();
        if (!PyBuiltinCache.getInstance(element).isBuiltin(cls)) {
          if (element.getParent() instanceof PyCallExpression) {
            actions.add(new AddMethodQuickFix(refText, cls.getName(), true));
          }
          else if (!(reference instanceof PyOperatorReference)) {
            actions.add(new AddFieldQuickFix(refText, "None", type.getName(), true));
          }
        }
      }
      else if (type instanceof PyModuleType) {
        PyFile file = ((PyModuleType)type).getModule();
        actions.add(new AddFunctionQuickFix(refText, file.getName()));
        addCreateClassFix(refText, element, actions);
      }
    }

    private void addAddSelfFix(PyElement node, PyReferenceExpression expr, List<LocalQuickFix> actions) {
      final PyClass containedClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
      final PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
      if (containedClass != null && function != null) {
        final PyParameter[] parameters = function.getParameterList().getParameters();
        if (parameters.length == 0) return;
        final String qualifier = parameters[0].getText();
        final PyDecoratorList decoratorList = function.getDecoratorList();
        boolean isClassMethod = false;
        if (decoratorList != null) {
          for (PyDecorator decorator : decoratorList.getDecorators()) {
            final PyExpression callee = decorator.getCallee();
            if (callee != null && PyNames.CLASSMETHOD.equals(callee.getText())) {
              isClassMethod = true;
            }
          }
        }
        for (PyTargetExpression target : containedClass.getInstanceAttributes()) {
          if (!isClassMethod && Comparing.strEqual(node.getName(), target.getName())) {
            actions.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
          }
        }
        for (PyStatement statement : containedClass.getStatementList().getStatements()) {
          if (statement instanceof PyAssignmentStatement) {
            PyExpression lhsExpression = ((PyAssignmentStatement)statement).getLeftHandSideExpression();
            if (lhsExpression != null && lhsExpression.getText().equals(expr.getText())) {
              PyExpression assignedValue = ((PyAssignmentStatement)statement).getAssignedValue();
              if (assignedValue instanceof PyCallExpression) {
                PyType type = myTypeEvalContext.getType(assignedValue);
                if (type instanceof PyClassTypeImpl) {
                  if (((PyCallExpression)assignedValue).isCalleeText(PyNames.PROPERTY)) {
                    actions.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
                  }
                }
              }
            }
          }
        }
        for (PyFunction method : containedClass.getMethods()) {
          if (expr.getText().equals(method.getName())) {
            actions.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
          }
        }
      }
    }

    private static void addAutoImportFix(PyElement node, PsiReference reference, List<LocalQuickFix> actions) {
      final PsiFile file = InjectedLanguageManager.getInstance(node.getProject()).getTopLevelFile(node);
      if (!(file instanceof PyFile)) return;
      AutoImportQuickFix importFix = PythonImportUtils.proposeImportFix(node, reference);
      if (importFix != null) {
        if (!suppressHintForAutoImport(node, importFix) && PyCodeInsightSettings.getInstance().SHOW_IMPORT_POPUP) {
          final AutoImportHintAction autoImportHintAction = new AutoImportHintAction(importFix);
          actions.add(autoImportHintAction);
        }
        else {
          actions.add(importFix);
        }
        if (ScopeUtil.getScopeOwner(node) instanceof PyFunction) {
          actions.add(importFix.forLocalImport());
        }
      }
      else {
        final String refName = (node instanceof PyQualifiedExpression) ? ((PyQualifiedExpression)node).getReferencedName() : node.getText();
        if (refName == null) return;
        final QualifiedName qname = QualifiedName.fromDottedString(refName);
        final List<String> components = qname.getComponents();
        if (!components.isEmpty()) {
          final String packageName = components.get(0);
          final Module module = ModuleUtilCore.findModuleForPsiElement(node);
          if (PyPIPackageUtil.INSTANCE.isInPyPI(packageName) && PythonSdkType.findPythonSdk(module) != null) {
            actions.add(new PyPackageRequirementsInspection.InstallAndImportQuickFix(packageName, packageName, node));
          }
          else {
            final String packageAlias = PyPackageAliasesProvider.commonImportAliases.get(packageName);
            if (packageAlias != null && PyPIPackageUtil.INSTANCE.isInPyPI(packageName) && PythonSdkType.findPythonSdk(module) != null) {
              actions.add(new PyPackageRequirementsInspection.InstallAndImportQuickFix(packageAlias, packageName, node));
            }
          }
        }
      }
    }

    private static boolean suppressHintForAutoImport(PyElement node, AutoImportQuickFix importFix) {
      // if the context doesn't look like a function call and we only found imports of functions, suggest auto-import
      // as a quickfix but no popup balloon (PY-2312)
      if (!isCall(node) && importFix.hasOnlyFunctions()) {
        return true;
      }
      // if we're in a class context and the class defines a variable with the same name, offer auto-import only as quickfix,
      // not as popup
      PyClass containingClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
      if (containingClass != null && (containingClass.findMethodByName(importFix.getNameToImport(), true, null) != null ||
                                      containingClass.findInstanceAttribute(importFix.getNameToImport(), true) != null)) {
        return true;
      }
      return false;
    }

    private void addCreateClassFix(@NonNls String refText, PsiElement element, List<LocalQuickFix> actions) {
      if (refText.length() > 2 && Character.isUpperCase(refText.charAt(0)) && !refText.toUpperCase().equals(refText) &&
          PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class) == null) {
        PsiElement anchor = element;
        if (element instanceof PyQualifiedExpression) {
          final PyExpression expr = ((PyQualifiedExpression)element).getQualifier();
          if (expr != null) {
            final PyType type = myTypeEvalContext.getType(expr);
            if (type instanceof PyModuleType) {
              anchor = ((PyModuleType)type).getModule();
            }
            else {
              anchor = null;
            }
          }
          if (anchor != null) {
            actions.add(new CreateClassQuickFix(refText, anchor));
          }
        }
      }
    }

    private static boolean isCall(PyElement node) {
      final PyCallExpression callExpression = PsiTreeUtil.getParentOfType(node, PyCallExpression.class);
      return callExpression != null && node == callExpression.getCallee();
    }

    private static void addPluginQuickFixes(PsiReference reference, final List<LocalQuickFix> actions) {
      for (PyUnresolvedReferenceQuickFixProvider provider : Extensions.getExtensions(PyUnresolvedReferenceQuickFixProvider.EP_NAME)) {
        provider.registerQuickFixes(reference, localQuickFix -> actions.add(localQuickFix));
      }
    }

    public void highlightUnusedImports() {
      final PyInspectionExtension[] extensions = Extensions.getExtensions(PyInspectionExtension.EP_NAME);
      final List<PsiElement> unused = collectUnusedImportElements();
      for (PsiElement element : unused) {
        if (Arrays.stream(extensions).anyMatch(extension -> extension.ignoreUnused(element))) {
          continue;
        }
        if (element.getTextLength() > 0) {
          registerProblem(element, "Unused import statement", ProblemHighlightType.LIKE_UNUSED_SYMBOL, null, new OptimizeImportsQuickFix());
        }
      }
    }

    public void highlightImportsInsideGuards() {
      HashSet<PyImportedNameDefiner> usedImportsInsideImportGuards = Sets.newHashSet(myImportsInsideGuard);
      usedImportsInsideImportGuards.retainAll(myUsedImports);

      for (PyImportedNameDefiner definer : usedImportsInsideImportGuards) {

        PyImportElement importElement = PyUtil.as(definer, PyImportElement.class);
        if (importElement == null) {
          continue;
        }
        final PyTargetExpression asElement = importElement.getAsNameElement();
        final PyElement toHighlight = asElement != null ? asElement : importElement.getImportReferenceExpression();
        registerProblem(toHighlight,
                        PyBundle.message("INSP.try.except.import.error",
                                         importElement.getVisibleName()),
                        ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
      }
    }

    private List<PsiElement> collectUnusedImportElements() {
      if (myAllImports.isEmpty()) {
        return Collections.emptyList();
      }
      // PY-1315 Unused imports inspection shouldn't work in python REPL console
      final PyImportedNameDefiner first = myAllImports.iterator().next();
      if (first.getContainingFile() instanceof PyExpressionCodeFragment || PydevConsoleRunner.isInPydevConsole(first)) {
        return Collections.emptyList();
      }
      List<PsiElement> result = new ArrayList<>();

      Set<PyImportedNameDefiner> unusedImports = new HashSet<>(myAllImports);
      unusedImports.removeAll(myUsedImports);

      // Remove those unsed, that are reported to be skipped by extension points
      final Set<PyImportedNameDefiner> unusedImportToSkip = new HashSet<>();
      for (final PyImportedNameDefiner unusedImport : unusedImports) {
        if (importShouldBeSkippedByExtPoint(unusedImport)) { // Pass to extension points
          unusedImportToSkip.add(unusedImport);
        }
      }

      unusedImports.removeAll(unusedImportToSkip);

      Set<String> usedImportNames = new HashSet<>();
      for (PyImportedNameDefiner usedImport : myUsedImports) {
        for (PyElement e : usedImport.iterateNames()) {
          usedImportNames.add(e.getName());
        }
      }

      Set<PyImportStatementBase> unusedStatements = new HashSet<>();
      final PyUnresolvedReferencesInspection suppressableInspection = new PyUnresolvedReferencesInspection();
      QualifiedName packageQName = null;
      List<String> dunderAll = null;

      // TODO: Use strategies instead of pack of "continue"
      iterUnused:
      for (PyImportedNameDefiner unusedImport : unusedImports) {
        if (packageQName == null) {
          final PsiFile file = unusedImport.getContainingFile();
          if (file instanceof PyFile) {
            dunderAll = ((PyFile)file).getDunderAll();
          }
          if (file != null && PyUtil.isPackage(file)) {
            packageQName = QualifiedNameFinder.findShortestImportableQName(file);
          }
        }
        PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(unusedImport, PyImportStatementBase.class);
        if (importStatement != null && !unusedStatements.contains(importStatement) && !myUsedImports.contains(unusedImport)) {
          if (suppressableInspection.isSuppressedFor(importStatement)) {
            continue;
          }
          // don't remove as unused imports in try/except statements
          if (PsiTreeUtil.getParentOfType(importStatement, PyTryExceptStatement.class) != null) {
            continue;
          }
          // Don't report conditional imports as unused
          if (PsiTreeUtil.getParentOfType(unusedImport, PyIfStatement.class) != null) {
            for (PyElement e : unusedImport.iterateNames()) {
              if (usedImportNames.contains(e.getName())) {
                continue iterUnused;
              }
            }
          }
          PsiFileSystemItem importedElement;
          if (unusedImport instanceof PyImportElement) {
            final PyImportElement importElement = (PyImportElement)unusedImport;
            final PsiElement element = importElement.resolve();
            if (element == null) {
              if (importElement.getImportedQName() != null) {
                //Mark import as unused even if it can't be resolved
                if (areAllImportsUnused(importStatement, unusedImports)) {
                  result.add(importStatement);
                }
                else {
                  result.add(importElement);
                }
              }
              continue;
            }
            if (dunderAll != null && dunderAll.contains(importElement.getVisibleName())) {
              continue;
            }
            importedElement = element.getContainingFile();
          }
          else {
            assert importStatement instanceof PyFromImportStatement;
            importedElement = ((PyFromImportStatement)importStatement).resolveImportSource();
            if (importedElement == null) {
              continue;
            }
          }
          if (packageQName != null && importedElement != null) {
            final QualifiedName importedQName = QualifiedNameFinder.findShortestImportableQName(importedElement);
            if (importedQName != null && importedQName.matchesPrefix(packageQName)) {
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

    private static boolean areAllImportsUnused(PyImportStatementBase importStatement, Set<PyImportedNameDefiner> unusedImports) {
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
        PyPsiUtils.assertValid(element);
        element.delete();
      }
    }
  }

  /**
   * Checks if one or more extension points ask unused import to be skipped
   *
   * @param importNameDefiner unused import
   * @return true of one or more asks
   */
  private static boolean importShouldBeSkippedByExtPoint(@NotNull final PyImportedNameDefiner importNameDefiner) {
    for (final PyUnresolvedReferenceSkipperExtPoint skipper : PyUnresolvedReferenceSkipperExtPoint.EP_NAME.getExtensions()) {
      if (skipper.unusedImportShouldBeSkipped(importNameDefiner)) {
        return true;
      }
    }
    return false;
  }
}
