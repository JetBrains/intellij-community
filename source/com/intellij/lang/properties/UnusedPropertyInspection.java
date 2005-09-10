package com.intellij.lang.properties;

import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiReferenceProcessor;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;

import java.util.List;

/**
 * @author cdr
 */
public class UnusedPropertyInspection extends LocalInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.properties.UnusedPropertyInspection");
  private static final RemovePropertyLocalFix QUICK_FIX = new RemovePropertyLocalFix();

  public String getGroupDisplayName() {
    return "Properties Files";
  }

  public String getDisplayName() {
    return "Unused Property";
  }

  public String getShortName() {
    return "UnusedProperty";
  }

  public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
    if (!(file instanceof PropertiesFile)) return null;
    List<ProblemDescriptor> descriptors = new SmartList<ProblemDescriptor>();
    PsiSearchHelper searchHelper = file.getManager().getSearchHelper();
    List<Property> properties = ((PropertiesFile)file).getProperties();
    Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) return null;
    final GlobalSearchScope searchScope = GlobalSearchScope.moduleWithDependentsScope(module);
    for (Property property : properties) {
      PsiReferenceProcessor.FindElement processor = new PsiReferenceProcessor.FindElement();
      searchHelper.processReferences(processor, property, searchScope, false);
      if (!processor.isFound()) {
        ASTNode[] nodes = property.getNode().getChildren(null);
        PsiElement key = nodes.length == 0 ? property : nodes[0].getPsi();
        ProblemDescriptor descriptor = manager.createProblemDescriptor(key, "Unused property", QUICK_FIX, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
        descriptors.add(descriptor);
      }
    }
    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  private static class RemovePropertyLocalFix implements LocalQuickFix {
    public String getName() {
      return "Remove Property";
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
      Property property = (Property)descriptor.getPsiElement();
      try {
        new RemovePropertyFix(property).invoke(project, null, property.getContainingFile());
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    public String getFamilyName() {
      return getName();
    }
  }
}
