package com.intellij.lang.properties;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author cdr
 */
public class UnusedPropertyInspection extends CustomSuppresableInspectionTool {
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

          final PsiReference usage = ReferencesSearch.search(property, searchScope, false).findFirst();
          if (usage == null) {
            final ASTNode propertyNode = property.getNode();
            assert propertyNode != null;

            ASTNode[] nodes = propertyNode.getChildren(null);
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


  public IntentionAction[] getSuppressActions(ProblemDescriptor context) {
    Property property = PsiTreeUtil.getParentOfType(context.getPsiElement(), Property.class, false);
    if (property == null) return new IntentionAction[] {new SuppressForFile()};
    return new IntentionAction[] {new SuppressSingleProperty(property), new SuppressForFile()};
  }

  public boolean isSuppressedFor(PsiElement element) {
    Property property = PsiTreeUtil.getParentOfType(element, Property.class, false);
    if (property == null) return false;

    PsiElement prev = property.getPrevSibling();
    while (prev instanceof PsiWhiteSpace || prev instanceof PsiComment) {
      if (prev instanceof PsiComment) {
        @NonNls String text = prev.getText();
        if (text.indexOf("suppress") >= 0 && text.indexOf("\"unused property\"") >= 0) return true;
      }
      prev = prev.getPrevSibling();
    }

    final PropertiesFile file = property.getContainingFile();
    PsiElement leaf = file.findElementAt(0);
    while (leaf instanceof PsiWhiteSpace) leaf = leaf.getNextSibling();

    while (leaf instanceof PsiComment) {
      @NonNls String text = leaf.getText();
      if (text.indexOf("suppress") >= 0 &&
          text.indexOf("\"unused property\"") >= 0 &&
          text.indexOf("file") >= 0
        ) {
        return true;
      }
      leaf = leaf.getNextSibling();
    }

    return false;
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


  private static class SuppressSingleProperty implements IntentionAction {
    @NotNull private final Property myProperty;

    public SuppressSingleProperty(@NotNull final Property property) {
      myProperty = property;
    }

    @NotNull
    public String getText() {
      return "Suppress for this property";
    }

    @NotNull
    public String getFamilyName() {
      return "Suppress for this property";
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
      return myProperty != null && myProperty.isValid();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);

      final int start = myProperty.getTextRange().getStartOffset();
      final int line = doc.getLineNumber(start);
      final int lineStart = doc.getLineStartOffset(line);

      doc.insertString(lineStart, "# suppress inspection \"unused property\"\n");
    }

    public boolean startInWriteAction() {
      return true;
    }
  }

  private static class SuppressForFile implements IntentionAction {
    @NotNull
    public String getText() {
      return "Suppress for whole file";
    }

    @NotNull
    public String getFamilyName() {
      return "Suppress for whole file";
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
      return file instanceof PropertiesFile && file.isValid();
    }

    public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
      final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);

      doc.insertString(0, "# suppress inspection \"unused property\" for whole file\n");
    }

    public boolean startInWriteAction() {
      return true;
    }
  }
}
