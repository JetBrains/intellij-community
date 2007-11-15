/*
 * @author max
 */
package com.intellij.lang.java;

import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.PsiBasedFormattingModel;
import com.intellij.psi.formatter.java.AbstractJavaBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

public class JavaFormattingModelBuilder implements FormattingModelBuilder {
  @NotNull
    public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
    final FileElement fileElement = TreeUtil.getFileElement((TreeElement)SourceTreeToPsiMap.psiElementToTree(element));
    return new PsiBasedFormattingModel(element.getContainingFile(), AbstractJavaBlock.createJavaBlock(fileElement,
                                                                                                      settings),
                                       FormattingDocumentModelImpl.createOn(element.getContainingFile()));
  }
}