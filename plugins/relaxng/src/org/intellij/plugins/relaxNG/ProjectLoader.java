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

package org.intellij.plugins.relaxNG;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.highlighting.DomElementsAnnotator;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.RncFileReference;
import org.intellij.plugins.relaxNG.compact.psi.util.RenameUtil;
import org.intellij.plugins.relaxNG.model.annotation.ModelAnnotator;
import org.intellij.plugins.relaxNG.xml.dom.*;
import org.intellij.plugins.relaxNG.xml.dom.impl.RngDefineImpl;
import org.intellij.plugins.relaxNG.xml.dom.impl.RngGrammarImpl;
import org.intellij.plugins.relaxNG.xml.dom.impl.RngIncludeImpl;
import org.intellij.plugins.relaxNG.xml.dom.impl.RngRefImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 16.07.2007
 */
public class ProjectLoader implements ProjectComponent {
  public static final String RNG_NAMESPACE = "http://relaxng.org/ns/structure/1.0";

  private final Project myProject;

  public ProjectLoader(Project project) {
    myProject = project;
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "RELAX-NG";
  }

  @SuppressWarnings({ "deprecation" })
  public void initComponent() {
    final DomManager mgr = DomManager.getDomManager(myProject);
    mgr.registerFileDescription(new MyFileDescription<RngGrammar>(RngGrammar.class, "grammar"));
    mgr.registerFileDescription(new MyFileDescription<RngElement>(RngElement.class, "element"));
    mgr.registerFileDescription(new MyFileDescription<RngChoice>(RngChoice.class, "choice"));
    mgr.registerFileDescription(new MyFileDescription<RngGroup>(RngGroup.class, "group"));
    mgr.registerFileDescription(new MyFileDescription<RngInterleave>(RngInterleave.class, "interleave"));
  }

  public void disposeComponent() {
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  private static class MyFileDescription<T> extends DomFileDescription<T> {
    public MyFileDescription(Class<T> elementClass, String rootTagName) {
      super(elementClass, rootTagName);
      registerNamespacePolicy("RELAX-NG", RNG_NAMESPACE);
      registerImplementation(RngDefine.class, RngDefineImpl.class);
      registerImplementation(RngGrammar.class, RngGrammarImpl.class);
      registerImplementation(RngInclude.class, RngIncludeImpl.class);
      registerImplementation(RngRef.class, RngRefImpl.class);
    }

    public boolean isAutomaticHighlightingEnabled() {
      return true;
    }

    public DomElementsAnnotator createAnnotator() {
      return new ModelAnnotator();
    }
  }

  public static class RncFileReferenceManipulator extends AbstractElementManipulator<RncFileReference> {
    public RncFileReference handleContentChange(RncFileReference element, TextRange range, String newContent) throws IncorrectOperationException {
      final ASTNode node = element.getNode();
      assert node != null;

      final ASTNode literal = node.findChildByType(RncTokenTypes.LITERAL);
      if (literal != null) {
        assert range.equals(element.getReferenceRange());
        final PsiManager manager = PsiManager.getInstance(element.getProject());
        final ASTNode newChild = RenameUtil.createLiteralNode(manager, newContent);
        literal.getTreeParent().replaceChild(literal, newChild);
      }
      return element;
    }

    @Override
    public TextRange getRangeInElement(RncFileReference element) {
      return element.getReferenceRange();
    }
  }
}
