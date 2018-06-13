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
package org.intellij.lang.xpath.xslt.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.util.XsltCodeInsightUtil;
import org.jetbrains.annotations.NotNull;

public class CreateTemplateFix implements LocalQuickFix {
  private static final String DUMMY_NS = "urn:x__dummy__";
  private static final String DUMMY_TAG = "<dummy xmlns='" + DUMMY_NS + "' />";

  private final XmlTag myTag;
  private final String myName;

  public CreateTemplateFix(XmlTag tag, String name) {
    myTag = tag;
    myName = name;
  }

  @NotNull
  @Override
  public String getName() {
    return "Create Template '" + myName + "'";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Create Template";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final XmlTag tag = XsltCodeInsightUtil.getTemplateTag(myTag, false);
    if (tag == null) {
      return;
    }

    final XmlTag parentTag = tag.getParentTag();
    assert parentTag != null;

    XmlTag templateTag = parentTag.createChildTag("template", XsltSupport.XSLT_NS, DUMMY_TAG, false);
    templateTag.setAttribute("name", myName);

    final XmlTag[] arguments = myTag.findSubTags("with-param", XsltSupport.XSLT_NS);
    if (arguments.length > 0) {
      final XmlTag dummy = templateTag.findFirstSubTag("dummy");
      for (XmlTag arg : arguments) {
        final String argName = arg.getAttributeValue("name");
        if (argName != null) {
          final XmlTag paramTag = parentTag.createChildTag("param", XsltSupport.XSLT_NS, null, false);
          paramTag.setAttribute("name", argName);
          templateTag.addBefore(paramTag, dummy);
        }
      }
    }

    // TODO ensure we have line breaks before the new <xsl:template> and between its opening and closing tags

    XmlTag newTemplateTag = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(templateTag);

    Navigatable openFileDescriptor = PsiNavigationSupport.getInstance().createNavigatable(project,
                                                                                          myTag.getContainingFile()
                                                                                               .getVirtualFile(),
                                                                                          newTemplateTag
                                                                                            .getTextRange()
                                                                                            .getStartOffset());
    openFileDescriptor.navigate(true);
  }
}