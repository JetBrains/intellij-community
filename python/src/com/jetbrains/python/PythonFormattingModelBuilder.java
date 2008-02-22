package com.jetbrains.python;

import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.formatting.FormattingModelProvider;
import com.intellij.formatting.Indent;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.openapi.util.TextRange;
import com.jetbrains.python.formatter.PyBlock;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonFormattingModelBuilder implements FormattingModelBuilder {
  private static boolean DUMP_FORMATTING_AST = false;

  @NotNull
  public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
    if (DUMP_FORMATTING_AST) {
      ASTNode fileNode = element.getContainingFile().getNode();
      System.out.println("AST tree for " + element.getContainingFile().getName() + ":");
      printAST(fileNode, 0);
    }
    final PyBlock block = new PyBlock((PythonLanguage)PythonFileType.INSTANCE.getLanguage(),
                                      element.getNode(), null, Indent.getNoneIndent(), null, settings);
    return FormattingModelProvider.createFormattingModelForPsiFile(element.getContainingFile(), block, settings);
  }

  public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return null;
  }

  private void printAST(ASTNode node, int indent) {
    while (node != null) {
      for (int i = 0; i < indent; i++) {
        System.out.print(" ");
      }
      System.out.println(node.toString() + " " + node.getTextRange().toString());
      printAST(node.getFirstChildNode(), indent + 2);
      node = node.getTreeNext();
    }
  }
}
