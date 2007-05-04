/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.xml.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Jul 21, 2005
 * Time: 7:42:23 PM
 * To change this template use File | Settings | File Templates.
 */

public class ImportsFormatter extends AbstractPostFormatProcessor {

  private FormattingDocumentModelImpl myDocumentModel;
  private final CodeStyleSettings.IndentOptions myIndentOptions;
  private static final @NonNls String PAGE_DIRECTIVE = "page";
  private static final @NonNls String IMPORT_ATT = "import";

  public ImportsFormatter(final CodeStyleSettings settings, PsiFile file) {
    super(settings);
    myDocumentModel = FormattingDocumentModelImpl.createOn(file);
    myIndentOptions = settings.getIndentOptions(file.getFileType());
  }

  public void visitXmlTag(XmlTag tag) {
    if (checkElementContainsRange(tag)) {
      super.visitXmlTag(tag);
    }
  }

  private boolean isPageDirectiveTag(final XmlTag tag) {
    return PAGE_DIRECTIVE.equals(tag.getName());
  }

  public void visitXmlText(XmlText text) {

  }

  public void visitXmlAttribute(XmlAttribute attribute) {
    if (isPageDirectiveTag(attribute.getParent())) {
      final XmlAttributeValue valueElement = attribute.getValueElement();
      if (valueElement != null && checkRangeContainsElement(attribute) && isImportAttribute(attribute) && isMultiline(valueElement)) {
        final int oldLength = attribute.getTextLength();
        ASTNode valueToken = findValueToken(valueElement.getNode());
        if (valueToken != null) {
          String newAttributeValue = formatImports(valueToken.getStartOffset(), attribute.getValue());
          try {
            attribute.setValue(newAttributeValue);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
          finally {
            updateResultRange(oldLength, attribute.getTextLength());
          }
        }
      }
    }
  }

  private String formatImports(final int startOffset, final String value) {
    final StringBuffer result = new StringBuffer();
    String offset = calcOffset(startOffset);
    final String[] imports = value.split(",");
    if (imports.length >=1) {
      result.append(imports[0]);
      for (int i = 1; i < imports.length; i++) {
        String anImport = imports[i];
        result.append(',');
        result.append('\n');
        result.append(offset);
        result.append(anImport.trim());
      }
    }
    return result.toString();
  }

  private String calcOffset(final int startOffset) {
    final StringBuffer result = new StringBuffer();

    final int lineStartOffset = myDocumentModel.getLineStartOffset(myDocumentModel.getLineNumber(startOffset));
    final int emptyLineEnd = CharArrayUtil.shiftForward(myDocumentModel.getDocument().getText(), lineStartOffset, " \t");
    final CharSequence spaces = myDocumentModel.getText(new TextRange(lineStartOffset, emptyLineEnd));

    if (spaces != null) {
      result.append(spaces.toString());
    }

    appendSpaces(result, startOffset - emptyLineEnd);

    return result.toString();
  }

  private void appendSpaces(final StringBuffer result, final int count) {
    if (myIndentOptions.USE_TAB_CHARACTER && ! myIndentOptions.SMART_TABS) {
      int tabsCount = count / myIndentOptions.TAB_SIZE;
      int spaceCount = count - tabsCount * myIndentOptions.TAB_SIZE;
      StringUtil.repeatSymbol(result, '\t', tabsCount);
      StringUtil.repeatSymbol(result, ' ', spaceCount);
    } else {
      StringUtil.repeatSymbol(result, ' ', count);
    }
  }

  private ASTNode findValueToken(final ASTNode node) {
    ASTNode child = node.getFirstChildNode();
    while (child != null){
      if (child.getElementType() == XmlElementType.XML_ATTRIBUTE_VALUE_TOKEN) return child;
      child = child.getTreeNext();
    }
    return null;
  }

  private boolean isImportAttribute(final XmlAttribute attribute) {
    return IMPORT_ATT.equals(attribute.getName());
  }

}
