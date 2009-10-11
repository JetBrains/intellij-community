/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.properties;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInspection.*;
import com.intellij.concurrency.JobUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author cdr
 */
public class UnusedPropertyInspection extends LocalInspectionTool implements CustomSuppressableInspectionTool {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.properties.UnusedPropertyInspection");
  @NotNull
  public String getGroupDisplayName() {
    return PropertiesBundle.message("properties.files.inspection.group.display.name");
  }

  @NotNull
  public String getDisplayName() {
    return PropertiesBundle.message("unused.property.inspection.display.name");
  }

  @NotNull
  public String getShortName() {
    return "UnusedProperty";
  }

  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull final InspectionManager manager, boolean isOnTheFly) {
    if (!(file instanceof PropertiesFile)) return null;
    final List<Property> properties = ((PropertiesFile)file).getProperties();
    Module module = ModuleUtil.findModuleForPsiElement(file);
    if (module == null) return null;
    final List<ProblemDescriptor> descriptors = new SmartList<ProblemDescriptor>();

    final GlobalSearchScope searchScope = GlobalSearchScope.moduleWithDependentsScope(module);
    final ProgressIndicator original = ProgressManager.getInstance().getProgressIndicator();
    JobUtil.invokeConcurrentlyUnderMyProgress(properties, new Processor<Property>() {
      public boolean process(final Property property) {
        if (original != null) {
          if (original.isCanceled()) return false;
          original.setText(PropertiesBundle.message("searching.for.property.key.progress.text", property.getUnescapedKey()));
        }

        final PsiReference usage = ReferencesSearch.search(property, searchScope, false).findFirst();
        if (usage == null) {
          final ASTNode propertyNode = property.getNode();
          assert propertyNode != null;

          ASTNode[] nodes = propertyNode.getChildren(null);
          PsiElement key = nodes.length == 0 ? property : nodes[0].getPsi();
          String description = PropertiesBundle.message("unused.property.problem.descriptor.name");
          ProblemDescriptor descriptor = manager.createProblemDescriptor(key, description, RemovePropertyLocalFix.INSTANCE, ProblemHighlightType.LIKE_UNUSED_SYMBOL);
          synchronized (descriptors) {
            descriptors.add(descriptor);
          }
        }

        return true;
      }
    }, "Searching properties usages");

    synchronized (descriptors) {
      return descriptors.toArray(new ProblemDescriptor[descriptors.size()]);
    }
  }


  public SuppressIntentionAction[] getSuppressActions(final PsiElement element) {
    return new SuppressIntentionAction[] {new SuppressSinglePropertyFix(), new SuppressForFile()};
  }

  public boolean isSuppressedFor(PsiElement element) {
    Property property = PsiTreeUtil.getParentOfType(element, Property.class, false);
    PropertiesFile file;
    if (property == null) {
      PsiFile containingFile = element.getContainingFile();
      if (containingFile instanceof PropertiesFile) {
        file = (PropertiesFile)containingFile;
      }
      else {
        return false;
      }
    }
    else {
      PsiElement prev = property.getPrevSibling();
      while (prev instanceof PsiWhiteSpace || prev instanceof PsiComment) {
        if (prev instanceof PsiComment) {
          @NonNls String text = prev.getText();
          if (text.contains("suppress") && text.contains("\"unused property\"")) return true;
        }
        prev = prev.getPrevSibling();
      }
      file = property.getContainingFile();
    }
    PsiElement leaf = file.findElementAt(0);
    while (leaf instanceof PsiWhiteSpace) leaf = leaf.getNextSibling();

    while (leaf instanceof PsiComment) {
      @NonNls String text = leaf.getText();
      if (text.contains("suppress") && text.contains("\"unused property\"") && text.contains("file")) {
        return true;
      }
      leaf = leaf.getNextSibling();
    }

    return false;
  }

  private static class SuppressSinglePropertyFix extends SuppressIntentionAction {

    @NotNull
    public String getText() {
      return PropertiesBundle.message("unused.property.suppress.for.property");
    }

    @NotNull
    public String getFamilyName() {
      return PropertiesBundle.message("unused.property.suppress.for.property");
    }

    public boolean isAvailable(@NotNull final Project project, final Editor editor, @Nullable final PsiElement element) {
      final Property property = PsiTreeUtil.getParentOfType(element, Property.class);
      return property != null && property.isValid();
    }

    public void invoke(final Project project, final Editor editor, final PsiElement element) throws IncorrectOperationException {
      final PsiFile file = element.getContainingFile();
      if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

      final Property property = PsiTreeUtil.getParentOfType(element, Property.class);
      LOG.assertTrue(property != null);
      final int start = property.getTextRange().getStartOffset();

      @NonNls final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
      LOG.assertTrue(doc != null);
      final int line = doc.getLineNumber(start);
      final int lineStart = doc.getLineStartOffset(line);

      doc.insertString(lineStart, "# suppress inspection \"unused property\"\n");
    }
  }

  private static class SuppressForFile extends SuppressIntentionAction {
    @NotNull
    public String getText() {
      return PropertiesBundle.message("unused.property.suppress.for.file");
    }

    @NotNull
    public String getFamilyName() {
      return PropertiesBundle.message("unused.property.suppress.for.file");
    }

    public boolean isAvailable(@NotNull final Project project, final Editor editor, @Nullable final PsiElement element) {
      return element != null && element.isValid() && element.getContainingFile() instanceof PropertiesFile;
    }

    public void invoke(final Project project, final Editor editor, final PsiElement element) throws IncorrectOperationException {
      final PsiFile file = element.getContainingFile();
      if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

      @NonNls final Document doc = PsiDocumentManager.getInstance(project).getDocument(file);

      doc.insertString(0, "# suppress inspection \"unused property\" for whole file\n");
    }
  }
}
