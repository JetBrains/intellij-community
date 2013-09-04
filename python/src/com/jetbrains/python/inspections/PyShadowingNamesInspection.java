package com.jetbrains.python.inspections;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.Consumer;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Set;

/**
 * Warns about shadowing names defined in outer scopes.
 *
 * @author vlan
 */
public class PyShadowingNamesInspection extends PyInspection {
  public JDOMExternalizableStringList ignoredNames = new JDOMExternalizableStringList();

  @NotNull
  @Override
  public String getDisplayName() {
    return "Shadowing names";
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListEditForm form = new ListEditForm("Ignore built-ins", ignoredNames);
    return form.getContentPanel();
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session, ignoredNames);
  }

  private static class Visitor extends PyInspectionVisitor {
    private final Set<String> myIgnoredNames;

    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session, @NotNull Collection<String> ignoredNames) {
      super(holder, session);
      myIgnoredNames = ImmutableSet.copyOf(ignoredNames);
    }

    @Override
    public void visitPyClass(@NotNull PyClass node) {
      processElement(node);
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      processElement(node);
    }

    @Override
    public void visitPyNamedParameter(@NotNull PyNamedParameter node) {
      if (node.isSelf()) {
        return;
      }
      processElement(node);
    }

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
      if (node.getQualifier() == null) {
        processElement(node);
      }
    }

    private void processElement(@NotNull PsiNameIdentifierOwner element) {
      final String name = element.getName();
      if (name != null && !myIgnoredNames.contains(name)) {
        final PsiElement identifier = element.getNameIdentifier();
        final PsiElement problemElement = identifier != null ? identifier : element;
        final PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
        final PsiElement builtin = builtinCache.getByName(name);
        if (builtin != null) {
          processBuiltin(element, name, problemElement, builtin);
        }
        else {
          processOuterScope(element, name, problemElement);
        }
      }
    }

    private void processBuiltin(@NotNull PsiNameIdentifierOwner element,
                                @NotNull String name,
                                @NotNull PsiElement problemElement,
                                @NotNull PsiElement builtin) {
      if (!PyUtil.inSameFile(builtin, element)) {
        registerProblem(problemElement, String.format("Shadows built-in name '%s'", name),
                        ProblemHighlightType.WEAK_WARNING, null, new PyRenameElementQuickFix(),
                        new PyIgnoreBuiltinQuickFix(name));
      }
    }

    private void processOuterScope(@NotNull PsiNameIdentifierOwner element, @NotNull String name, @NotNull PsiElement problemElement) {
      if ("_".equals(name)) {
        return;
      }
      final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
      if (owner != null) {
        final ScopeOwner nextOwner = ScopeUtil.getScopeOwner(owner);
        if (nextOwner != null) {
          final ResolveProcessor processor = new ResolveProcessor(name);
          PyResolveUtil.scopeCrawlUp(processor, nextOwner, null, name, null);
          final PsiElement resolved = processor.getResult();
          if (resolved != null) {
            registerProblem(problemElement, String.format("Shadows name '%s' from outer scope", name),
                            ProblemHighlightType.WEAK_WARNING, null, new PyRenameElementQuickFix());
          }
        }
      }
    }

    private static class PyIgnoreBuiltinQuickFix implements LocalQuickFix, LowPriorityAction {
      @NotNull private final String myName;

      private PyIgnoreBuiltinQuickFix(@NotNull String name) {
        myName = name;
      }

      @NotNull
      @Override
      public String getName() {
        return getFamilyName() + " \"" + myName + "\"";
      }

      @NotNull
      @Override
      public String getFamilyName() {
        return "Ignore shadowed built-in name";
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (element != null) {
          final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
          // For changing persistent inspection settings we should use the old serializer (put the inspection into
          // inspection-black-list.txt) and modify settings inside profile.modifyProfile()
          profile.modifyProfile(new Consumer<ModifiableModel>() {
            @Override
            public void consume(ModifiableModel model) {
              final String toolName = PyShadowingNamesInspection.class.getSimpleName();
              final PyShadowingNamesInspection inspection = (PyShadowingNamesInspection)model.getUnwrappedTool(toolName, element);
              if (inspection != null) {
                if (!inspection.ignoredNames.contains(myName)) {
                  inspection.ignoredNames.add(myName);
                }
              }
            }
          });
        }
      }
    }
  }
}

