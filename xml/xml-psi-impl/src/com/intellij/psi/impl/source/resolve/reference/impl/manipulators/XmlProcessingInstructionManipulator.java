/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.resolve.reference.impl.manipulators;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.xml.XmlProcessingInstruction;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import static com.intellij.xml.util.documentation.HtmlDescriptorsTable.LOG;

public class XmlProcessingInstructionManipulator extends AbstractElementManipulator<XmlProcessingInstruction> {

  @Override
  public XmlProcessingInstruction handleContentChange(@NotNull XmlProcessingInstruction element, @NotNull TextRange range, String newContent) throws IncorrectOperationException {
    CheckUtil.checkWritable(element);
    final CompositeElement attrNode = (CompositeElement)element.getNode();
    final ASTNode valueNode = attrNode.findLeafElementAt(range.getStartOffset());
    LOG.assertTrue(valueNode != null, "Leaf not found in " + attrNode + " at offset " + range.getStartOffset() + " in element " + element);
    final PsiElement elementToReplace = valueNode.getPsi();

    String text;
    try {
      text = elementToReplace.getText();
      final int offsetInParent = elementToReplace.getStartOffsetInParent();
      String textBeforeRange = text.substring(0, range.getStartOffset() - offsetInParent);
      String textAfterRange = text.substring(range.getEndOffset() - offsetInParent);
      newContent = element.getText().startsWith("'") || element.getText().endsWith("'") ?
                   newContent.replace("'", "&apos;") : newContent.replace("\"", "&quot;");
      text = textBeforeRange + newContent + textAfterRange;
    } catch(StringIndexOutOfBoundsException e) {
      LOG.error("Range: " + range + " in text: '" + element.getText() + "'", e);
      throw e;
    }
    final CharTable charTableByTree = SharedImplUtil.findCharTableByTree(attrNode);
    final LeafElement newValueElement = Factory.createSingleLeafElement(XmlTokenType.XML_TAG_CHARACTERS, text, charTableByTree, element.getManager());

    attrNode.replaceChildInternal(valueNode, newValueElement);
    return element;
  }
}
