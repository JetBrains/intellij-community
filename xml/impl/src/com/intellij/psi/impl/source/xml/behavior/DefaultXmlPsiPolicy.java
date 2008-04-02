package com.intellij.psi.impl.source.xml.behavior;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.source.DummyHolderFactory;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.xml.XmlPsiPolicy;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.CharTable;

public class DefaultXmlPsiPolicy implements XmlPsiPolicy{
  public ASTNode encodeXmlTextContents(String displayText, XmlText text, CharTable table) {
    final PsiFile containingFile = text.getContainingFile();
    final FileElement dummyParent = DummyHolderFactory.createHolder(text.getManager(), null, table).getTreeElement();
      final XmlTag rootTag =
        ((XmlFile)PsiFileFactory.getInstance(containingFile.getProject())
          .createFileFromText("a.xml", "<a>" + displayText + "</a>")).getDocument().getRootTag();

      assert rootTag != null;
      final XmlTagChild[] tagChildren = rootTag.getValue().getChildren();
      final XmlTagChild child = tagChildren.length > 0 ? tagChildren[0]:null;
      assert child != null;

      final TreeElement element = (TreeElement)child.getNode();
      TreeUtil.removeRange((TreeElement)tagChildren[tagChildren.length - 1].getNode().getTreeNext(), null);
      TreeUtil.addChildren(dummyParent, element);
      TreeUtil.clearCaches(dummyParent);
      return element.getFirstChildNode();
  }

}
