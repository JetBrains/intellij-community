package com.intellij.psi.impl.source.parsing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiLock;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.*;

/**
 *
 */
public class ChameleonTransforming implements Constants {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.impl.source.parsing.ChameleonTransforming");

  public static TreeElement transform(ChameleonElement chameleon) {
    synchronized (PsiLock.LOCK) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("\"transforming chameleon:\" + chameleon + \" in \" + chameleon.parent");
      }
      final CompositeElement parent = chameleon.getTreeParent();
      parent.getTextLength();
      PsiFileImpl file = (PsiFileImpl)SourceTreeToPsiMap.treeElementToPsi(parent).getContainingFile();
      if (file == null) return null;

      TreeElement newElement = chameleon.transform(file.getTreeElement().getCharTable(), file.createLexer());
      if (DebugUtil.CHECK) {
        if (newElement != null) {
          DebugUtil.checkTreeStructure(newElement);
        }

        String text1 = chameleon.getText();

        int length2 = 0;
        for (TreeElement element = newElement; element != null; element = element.getTreeNext()) {
          length2 += element.getTextLength();
        }
        char[] buffer = new char[length2];
        int offset = 0;
        for (TreeElement element = newElement; element != null; element = element.getTreeNext()) {
          offset = SourceUtil.toBuffer(element, buffer, offset);
        }
        String text2 = new String(buffer);

        if (!text1.equals(text2)) {
          LOG.error("Text changed after chameleon transformation!\nWas:\n" + text1 + "\nbecame:\n" + text2);
        }
      }
      TreeUtil.replace(chameleon, newElement);
      return newElement;
    }
  }

  public static void transformChildren(CompositeElement element) {
    transformChildren(element, false);
  }

  public static void transformChildren(CompositeElement element, boolean recursive) {
    synchronized (PsiLock.LOCK) {
      TreeElement child = element.firstChild;
      while (child != null) {
        if (child instanceof ChameleonElement) {
          TreeElement next = child.getTreeNext();
          child = transform((ChameleonElement)child);
          if (child == null) {
            child = next;
          }
          continue;
        }
        if (recursive && child instanceof CompositeElement) {
          transformChildren((CompositeElement)child, recursive);
        }
        child = child.getTreeNext();
      }
      ;
    }
  }
}
