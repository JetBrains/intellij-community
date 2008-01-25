/*
 * @author max
 */
package com.intellij.lang.xml;

import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormatterUtil;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.psi.formatter.XmlFormatterUtilHelper;
import com.intellij.psi.formatter.xml.XmlBlock;
import com.intellij.psi.formatter.xml.XmlPolicy;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

public class XmlFormattingModelBuilder implements FormattingModelBuilder {
  static {
    FormatterUtil.addHelper(new XmlFormatterUtilHelper());
  }
  
  @NotNull
  public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
    final ASTNode root = TreeUtil.getFileElement((TreeElement)SourceTreeToPsiMap.psiElementToTree(element));
    final FormattingDocumentModelImpl documentModel = FormattingDocumentModelImpl.createOn(element.getContainingFile());
    return new PsiBasedFormattingModel(element.getContainingFile(),
                                       new XmlBlock(root, null, null, new XmlPolicy(settings, documentModel), null, null),
                                       documentModel);
  }
}