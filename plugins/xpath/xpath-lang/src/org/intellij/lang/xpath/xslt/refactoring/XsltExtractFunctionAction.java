/*
 * Copyright 2005 Sascha Weinreuter
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
package org.intellij.lang.xpath.xslt.refactoring;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.psi.*;
import org.intellij.lang.xpath.psi.impl.XPathChangeUtil;
import org.intellij.lang.xpath.validation.ExpectedTypeUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltVariable;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;

import javax.xml.namespace.QName;
import java.util.List;
import java.util.Set;

@SuppressWarnings({"ComponentNotRegistered"})
public class XsltExtractFunctionAction extends BaseIntroduceAction<RefactoringOptions> {

  public String getRefactoringName() {
    return "Extract Function";
  }

  protected String getCommandName() {
    return "Extract XSLT Function";
  }

  @Override
  protected boolean actionPerformedImpl(PsiFile file, Editor editor, XmlAttribute context, int offset) {
    if (file.getLanguage() != XPathFileType.XPATH2.getLanguage()) return false;

    return super.actionPerformedImpl(file, editor, context, offset);
  }

  protected boolean extractImpl(XPathExpression expression, Set<XPathExpression> matchingExpressions, List<XmlTag> otherMatches, RefactoringOptions dlg) {
    final XmlAttribute attribute = PsiTreeUtil.getContextOfType(expression, XmlAttribute.class, true);
    assert attribute != null;

    try {
      final String name = dlg.getName();
      final XmlTag rootTag = ((XmlFile)attribute.getParent().getContainingFile()).getRootTag();
      final XmlTag[] templates = rootTag.findSubTags("template", XsltSupport.XSLT_NS);
      final XmlTag insertionPoint = templates.length > 0 ? templates[0] : rootTag.getSubTags()[0];

      final XmlTag parentTag = insertionPoint.getParentTag();
      assert parentTag != null : "Could not locate position to create function at";

      final XmlTag xmlTag = parentTag.createChildTag("function", XsltSupport.XSLT_NS, null, false);
      xmlTag.setAttribute("name", name);

      final XPathType type = ExpectedTypeUtil.mapType(expression, expression.getType());
      xmlTag.setAttribute("as", prefixedName(type, insertionPoint));

      final StringBuilder argList = new StringBuilder();
      final List<XPathVariableReference> references = RefactoringUtil.collectVariableReferences(expression);
      for (XPathVariableReference reference : references) {
        final XPathVariable variable = reference.resolve();
        if (variable instanceof XsltVariable) {
          // don't pass through global parameters and variables
          if (XsltCodeInsightUtil.getTemplateTag(variable, false) != null) {
            final XmlTag param = parentTag.createChildTag("param", XsltSupport.XSLT_NS, null, false);
            param.setAttribute("name", variable.getName());
            if (!variable.getType().isAbstract()) {
              param.setAttribute("as", prefixedName(ExpectedTypeUtil.mapType(expression, variable.getType()), parentTag));
            }
            RefactoringUtil.addParameter(xmlTag, param);

            if (argList.length() > 0) {
              argList.append(", ");
            }
            argList.append("$").append(variable.getName());
          }
        }
      }

      final XmlTag seqTag = parentTag.createChildTag("sequence", XsltSupport.XSLT_NS, null, false);
      seqTag.setAttribute("select", expression.getText());
      xmlTag.add(seqTag);

      // TODO: revisit the formatting
      final PsiElement element = parentTag.addBefore(xmlTag, insertionPoint);
      final ASTNode node1 = parentTag.getNode();
      assert node1 != null;
      final ASTNode node2 = element.getNode();
      assert node2 != null;
      CodeStyleManager.getInstance(xmlTag.getManager().getProject()).reformatNewlyAddedElement(node1, node2);

      final XPathExpression var = XPathChangeUtil.createExpression(expression, name + "(" + argList + ")");
      expression.replace(var);

      return true;
    } catch (IncorrectOperationException e) {
      Logger.getInstance(getClass().getName()).error(e);
      return false;
    }
  }

  private static String prefixedName(XPathType type, XmlTag context) {
    if (type instanceof XPath2Type) {
      final QName name = ((XPath2Type)type).getQName();
      final String uri = name.getNamespaceURI();
      if (uri.length() > 0) {
        final String prefix = context.getPrefixByNamespace(uri);
        if (prefix != null) {
          return (prefix + ":" + name.getLocalPart());
        }
      }
    }
    return type.getName();
  }

  protected RefactoringOptions getSettings(XPathExpression expression, Set<XPathExpression> matchingExpressions) {
    final String name = Messages.showInputDialog(expression.getProject(), "Function Name: ", getRefactoringName(), Messages.getQuestionIcon());
    final boolean[] b = new boolean[]{false};
    if (name != null) {
      final String[] parts = name.split(":", 2);
      if (parts.length < 2) {
        Messages.showMessageDialog(expression.getProject(), "Custom functions require a prefixed name", "Error", Messages.getErrorIcon());
        b[0] = true;
      }
      final XmlElement context = PsiTreeUtil.getContextOfType(expression, XmlElement.class);
      final NamespaceContext namespaceContext = expression.getXPathContext().getNamespaceContext();
      if (namespaceContext != null && context != null && namespaceContext.resolve(parts[0], context) == null) {
        Messages.showMessageDialog(expression.getProject(), "Prefix '" + parts[0] + "' is not defined", "Error", Messages.getErrorIcon());
        b[0] = true;
      }
    }
    return new RefactoringOptions() {
      @Override
      public boolean isCanceled() {
        return b[0];
      }

      @Override
      public String getName() {
        return name;
      }
    };
  }
}
