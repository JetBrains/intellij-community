package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import com.jetbrains.python.inspections.PyUnresolvedReferencesInspection;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AddIgnoredIdentifierQuickFix implements LocalQuickFix, LowPriorityAction {
  public static final String END_WILDCARD = ".*";

  @NotNull private final PyQualifiedName myIdentifier;
  private final boolean myIgnoreAllAttributes;

  public AddIgnoredIdentifierQuickFix(@NotNull PyQualifiedName identifier, boolean ignoreAllAttributes) {
    myIdentifier = identifier;
    myIgnoreAllAttributes = ignoreAllAttributes;
  }

  @NotNull
  @Override
  public String getName() {
    if (myIgnoreAllAttributes) {
      return "Mark all unresolved attributes of '" + myIdentifier + "' as ignored";
    }
    else {
      return "Ignore unresolved reference '" + myIdentifier + "'";
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Ignore unresolved reference";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement context = descriptor.getPsiElement();
    InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
    profile.modifyToolSettings(PyUnresolvedReferencesInspection.SHORT_NAME_KEY, context,  new Consumer<PyUnresolvedReferencesInspection>() {
      @Override
      public void consume(PyUnresolvedReferencesInspection inspection) {
        String name = myIdentifier.toString();
        if (myIgnoreAllAttributes) {
          name += END_WILDCARD;
        }
        if (!inspection.ignoredIdentifiers.contains(name)) {
          inspection.ignoredIdentifiers.add(name);
        }
      }
    });
  }
}
