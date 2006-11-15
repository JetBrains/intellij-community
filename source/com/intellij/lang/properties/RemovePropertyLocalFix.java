package com.intellij.lang.properties;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.lang.properties.psi.Property;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
public class RemovePropertyLocalFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.properties.RemovePropertyLocalFix");
  public static final RemovePropertyLocalFix INSTANCE = new RemovePropertyLocalFix();

  @NotNull
  public String getName() {
    return PropertiesBundle.message("remove.property.quick.fix.name");
  }

  public void applyFix(@NotNull Project project, ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    Property property = PsiTreeUtil.getParentOfType(element, Property.class, false);
    if (property == null) return;
    try {
      new RemovePropertyFix(property).invoke(project, null, property.getContainingFile());
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @NotNull
  public String getFamilyName() {
    return getName();
  }
}
