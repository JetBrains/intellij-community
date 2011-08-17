package org.intellij.plugins.relaxNG;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import org.intellij.plugins.relaxNG.compact.RncTokenTypes;
import org.intellij.plugins.relaxNG.compact.psi.RncFileReference;
import org.intellij.plugins.relaxNG.compact.psi.util.RenameUtil;

/**
* @author peter
*/
public class RncFileReferenceManipulator extends AbstractElementManipulator<RncFileReference> {
  public RncFileReference handleContentChange(RncFileReference element, TextRange range, String newContent) throws
                                                                                                            IncorrectOperationException {
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
