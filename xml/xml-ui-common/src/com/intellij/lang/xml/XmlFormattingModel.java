// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.xml;

import com.intellij.formatting.Block;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class XmlFormattingModel extends PsiBasedFormattingModel {
  private static final Logger LOG =
      Logger.getInstance(XmlFormattingModel.class);

  public XmlFormattingModel(final PsiFile file,
                                                     final Block rootBlock,
                                                     final FormattingDocumentModelImpl documentModel) {
    super(file, rootBlock, documentModel);
  }

  @Override
  protected String replaceWithPsiInLeaf(final TextRange textRange, String whiteSpace, ASTNode leafElement) {
     if (!myCanModifyAllWhiteSpaces) {
       if (leafElement.getElementType() == TokenType.WHITE_SPACE) return null;
       LOG.assertTrue(leafElement.getPsi().isValid());
       ASTNode prevNode = TreeUtil.prevLeaf(leafElement);

       if (prevNode != null) {
         IElementType type = prevNode.getElementType();
         if(type == TokenType.WHITE_SPACE) {
           final String text = prevNode.getText();

           final @NonNls String cdataStartMarker = "<![CDATA[";
           final int cdataPos = text.indexOf(cdataStartMarker);
           if (cdataPos != -1 && !whiteSpace.contains(cdataStartMarker)) {
             whiteSpace = mergeWsWithCdataMarker(whiteSpace, text, cdataPos);
             if (whiteSpace == null) return null;
           }

           prevNode = TreeUtil.prevLeaf(prevNode);
           type = prevNode != null ? prevNode.getElementType():null;
         }

         final @NonNls String cdataEndMarker = "]]>";
         if(type == XmlTokenType.XML_CDATA_END && !whiteSpace.contains(cdataEndMarker)) {
           final ASTNode at = findElementAt(prevNode.getStartOffset());

           if (at != null && at.getPsi() instanceof PsiWhiteSpace) {
             final String s = at.getText();
             final int cdataEndPos = s.indexOf(cdataEndMarker);
             whiteSpace = mergeWsWithCdataMarker(whiteSpace, s, cdataEndPos);
             leafElement = at;
           } else {
             whiteSpace = null;
           }
           if (whiteSpace == null) return null;
         }
       }
     }
     FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, TokenType.WHITE_SPACE, textRange);
     return whiteSpace;
   }

   private static @Nullable String mergeWsWithCdataMarker(String whiteSpace, final String s, final int cdataPos) {
     final int firstCrInGeneratedWs = whiteSpace.indexOf('\n');
     final int secondCrInGeneratedWs = firstCrInGeneratedWs != -1 ? whiteSpace.indexOf('\n', firstCrInGeneratedWs + 1):-1;
     final int firstCrInPreviousWs = s.indexOf('\n');
     final int secondCrInPreviousWs = firstCrInPreviousWs != -1 ? s.indexOf('\n', firstCrInPreviousWs + 1):-1;

     boolean knowHowToModifyCData = false;

     if (secondCrInPreviousWs != -1 && secondCrInGeneratedWs != -1 && cdataPos > firstCrInPreviousWs && cdataPos < secondCrInPreviousWs ) {
       whiteSpace = whiteSpace.substring(0, secondCrInGeneratedWs) + s.substring(firstCrInPreviousWs + 1, secondCrInPreviousWs) + whiteSpace.substring(secondCrInGeneratedWs);
       knowHowToModifyCData = true;
     }
     if (!knowHowToModifyCData) whiteSpace = null;
     return whiteSpace;
   }
}
