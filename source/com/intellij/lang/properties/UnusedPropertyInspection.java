package com.intellij.lang.properties;

import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
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
    return PropertiesBundle.message("properties.files.inspection.group.display.name");
  }

  public String getDisplayName() {
    return PropertiesBundle.message("properties.files.inspection.group.name");
  }

  public String getShortName() {
    return "UnusedProperty";
  }

  public ProblemDescriptor[] checkFile(PsiFile file, final InspectionManager manager, boolean isOnTheFly) {
    if (!(file instanceof PropertiesFile)) return null;
    final PsiSearchHelper searchHelper = file.getManager().getSearchHelper();
    final List<Property> properties = ((PropertiesFile)file).getProperties();
    Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) return null;
    final List<ProblemDescriptor> descriptors = new SmartList<ProblemDescriptor>();

    final GlobalSearchScope searchScope = GlobalSearchScope.moduleWithDependentsScope(module);
    final ProgressIndicator original = ProgressManager.getInstance().getProgressIndicator();
    final ProgressIndicator progress = original == null ? null : new ProgressWrapper(original);

    ProgressManager.getInstance().runProcess(new Runnable() {
      public void run() {
        for (Property property : properties) {
          if (original != null) {
            original.setText(PropertiesBundle.message("searching.for.property.key.progress.text", property.getKey()));
          }
          PsiReferenceProcessor.FindElement processor = new PsiReferenceProcessor.FindElement();
          searchHelper.processReferences(processor, property, searchScope, false);
          if (!processor.isFound()) {
            ASTNode[] nodes = property.getNode().getChildren(null);
            PsiElement key = nodes.length == 0 ? property : nodes[0].getPsi();
            String description = PropertiesBundle.message("unused.property.problem.descriptor.name");
            ProblemDescriptor descriptor = manager.createProblemDescriptor(key, description, QUICK_FIX, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
            descriptors.add(descriptor);
          }
        }
      }
    }, progress);
    return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
  }

  private static class ProgressWrapper extends ProgressIndicatorBase {
    private ProgressIndicator myOriginal;

    public ProgressWrapper(final ProgressIndicator original) {
      myOriginal = original;
    }

    public boolean isCanceled() {
      return myOriginal.isCanceled();
    }
  }

  private static class RemovePropertyLocalFix implements LocalQuickFix {
    public String getName() {
      return PropertiesBundle.message("remove.property.quick.fix.name");
    }

    public void applyFix(Project project, ProblemDescriptor descriptor) {
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

    public String getFamilyName() {
      return getName();
    }
  }
}
