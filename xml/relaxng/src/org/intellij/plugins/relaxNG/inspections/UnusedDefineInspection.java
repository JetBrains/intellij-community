/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.intellij.plugins.relaxNG.RelaxNgMetaDataContributor;
import org.intellij.plugins.relaxNG.RelaxngBundle;
import org.intellij.plugins.relaxNG.compact.psi.RncDefine;
import org.intellij.plugins.relaxNG.compact.psi.RncElementVisitor;
import org.intellij.plugins.relaxNG.compact.psi.RncGrammar;
import org.intellij.plugins.relaxNG.compact.psi.impl.RncDefineImpl;
import org.intellij.plugins.relaxNG.model.resolve.RelaxIncludeIndex;
import org.intellij.plugins.relaxNG.xml.dom.RngGrammar;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.doIfNotNull;

public class UnusedDefineInspection extends BaseInspection {

  @Override
  public @NotNull RncElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new MyElementVisitor(holder);
  }

  private static final class MyElementVisitor extends RncElementVisitor {
    private final ProblemsHolder myHolder;

    private final XmlElementVisitor myXmlVisitor = new XmlElementVisitor() {
      @Override
      public void visitXmlTag(@NotNull XmlTag tag) {
        MyElementVisitor.this.visitXmlTag(tag);
      }
    };

    MyElementVisitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    protected void superVisitElement(PsiElement element) {
      element.accept(myXmlVisitor);
    }

    @Override
    public void visitDefine(RncDefine pattern) {
      final RncGrammar grammar = PsiTreeUtil.getParentOfType(pattern, RncGrammar.class);
      final PsiFile file = pattern.getContainingFile();
      if (grammar != null) {
        if (processRncUsages(pattern, new LocalSearchScope(grammar))) return;
      } else {
        if (processRncUsages(pattern, new LocalSearchScope(file))) return;
      }

      final PsiElementProcessor.CollectElements<XmlFile> collector = new PsiElementProcessor.CollectElements<>();
      RelaxIncludeIndex.processBackwardDependencies((XmlFile)file, collector);

      if (processRncUsages(pattern, new LocalSearchScope(collector.toArray()))) return;

      final ASTNode astNode = ((RncDefineImpl)pattern).getNameNode();
      myHolder.registerProblem(astNode.getPsi(), RelaxngBundle.message("relaxng.inspection.unused-define.message"), ProblemHighlightType.LIKE_UNUSED_SYMBOL, new MyFix<>());
    }

    private static boolean processRncUsages(PsiElement tag, LocalSearchScope scope) {
      final Query<PsiReference> query = ReferencesSearch.search(tag, scope);
      for (PsiReference reference : query) {
        final PsiElement e = reference.getElement();
        final RncDefine t = PsiTreeUtil.getParentOfType(e, RncDefine.class, false);
        if (t == null || !PsiTreeUtil.isAncestor(tag, t, true)) {
          return true;
        }
      }
      return false;
    }

    public void visitXmlTag(XmlTag tag) {
      final PsiFile file = tag.getContainingFile();
      if (file.getFileType() != XmlFileType.INSTANCE) {
        return;
      }
      if (!tag.getLocalName().equals("define")) {
        return;
      }
      if (!tag.getNamespace().equals(RelaxNgMetaDataContributor.RNG_NAMESPACE)) {
        return;
      }
      if (tag.getAttribute("combine") != null) {
        return; // ?
      }

      final XmlAttribute attr = tag.getAttribute("name");
      if (attr == null) return;

      final XmlAttributeValue value = attr.getValueElement();
      if (value == null) return;

      final String s = value.getValue();
      if (s.length() == 0) {
        return;
      }
      final PsiElement parent = value.getParent();
      if (!(parent instanceof XmlAttribute)) {
        return;
      }
      if (!"name".equals(((XmlAttribute)parent).getName())) {
        return;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof XmlTag)) {
        return;
      }

      final DomElement element = DomManager.getDomManager(tag.getProject()).getDomElement(tag);
      if (element == null) {
        return;
      }

      final RngGrammar rngGrammar = element.getParentOfType(RngGrammar.class, true);
      if (rngGrammar != null) {
        if (processUsages(tag, value, new LocalSearchScope(rngGrammar.getXmlTag()))) return;
      } else {
        if (processUsages(tag, value, new LocalSearchScope(file))) return;
      }

      final PsiElementProcessor.CollectElements<XmlFile> collector = new PsiElementProcessor.CollectElements<>();
      RelaxIncludeIndex.processBackwardDependencies((XmlFile)file, collector);

      if (processUsages(tag, value, new LocalSearchScope(collector.toArray()))) return;

      myHolder.registerProblem(value, RelaxngBundle.message("relaxng.inspection.unused-define.message"), ProblemHighlightType.LIKE_UNUSED_SYMBOL, new MyFix<>());
    }

    private static boolean processUsages(PsiElement tag, XmlAttributeValue value, LocalSearchScope scope) {
      final Query<PsiReference> query = ReferencesSearch.search(tag, scope, true);
      for (PsiReference reference : query) {
        final PsiElement e = reference.getElement();
        if (e != value) {
          final XmlTag t = PsiTreeUtil.getParentOfType(e, XmlTag.class);
          if (t != null && !PsiTreeUtil.isAncestor(tag, t, true)) {
            return true;
          }
        }
      }
      return false;
    }

    private static class MyFix<T extends PsiElement> implements LocalQuickFix {
      @Override
      public @NotNull String getFamilyName() {
        return RelaxngBundle.message("relaxng.quickfix.remove-define");
      }

      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement myTag = doIfNotNull(descriptor.getPsiElement(), PsiElement::getParent);
        try {
          if (myTag instanceof RncDefine && myTag.isValid()) {
            myTag.delete();
          }
        } catch (IncorrectOperationException e) {
          Logger.getInstance(UnusedDefineInspection.class.getName()).error(e);
        }
      }
    }
  }
}
