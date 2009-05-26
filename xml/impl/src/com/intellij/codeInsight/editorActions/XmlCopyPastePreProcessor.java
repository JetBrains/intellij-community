package com.intellij.codeInsight.editorActions;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.xml.behavior.EncodeEachSymbolPolicy;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class XmlCopyPastePreProcessor implements CopyPastePreProcessor {

  private static final EncodeEachSymbolPolicy ENCODE_EACH_SYMBOL_POLICY = new EncodeEachSymbolPolicy();

  @Nullable
  public String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return null;
  }

  public String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    int caretOffset = editor.getCaretModel().getOffset();
    PsiElement element = PsiUtilBase.getElementAtOffset(file, caretOffset);

    ASTNode node = element.getNode();
    if (node != null) {
      boolean hasMarkup = text.indexOf('>') >= 0 || text.indexOf('<') >= 0;
      if (element.getTextOffset() == caretOffset &&
          node.getElementType() == XmlElementType.XML_END_TAG_START &&
          node.getTreePrev().getElementType() == XmlElementType.XML_TAG_END) {

         return hasMarkup ? text : encode(text, element);
      } else {
        XmlElement parent = PsiTreeUtil.getParentOfType(element, XmlText.class, XmlAttributeValue.class);
        if (parent != null) {
          if (parent instanceof XmlText && hasMarkup) {
            return text;
          }

          if (TreeUtil.findParent(node, XmlElementType.XML_CDATA) == null &&
              TreeUtil.findParent(node, XmlElementType.XML_COMMENT) == null) {
            return encode(text, element);
          }
        }
      }
    }
    return text;
  }

  private static String encode(String text, PsiElement element) {
    ASTNode astNode = ENCODE_EACH_SYMBOL_POLICY.encodeXmlTextContents(text, element);
    return astNode.getTreeParent().getText();
  }
}
