package com.intellij.codeInsight.editorActions;

import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RawText;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.impl.source.xml.behavior.EncodeEachSymbolPolicy;
import com.intellij.psi.impl.source.tree.TreeUtil;

/**
 * @author Dmitry Avdeev
 */
public class XmlCopyPastePreProcessor implements CopyPastePreProcessor {

  private static final EncodeEachSymbolPolicy ENCODE_EACH_SYMBOL_POLICY = new EncodeEachSymbolPolicy();

  public String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return text;
  }

  public String preprocessOnPaste(Project project, PsiFile file, Editor editor, String text, RawText rawText) {
    final Document document = editor.getDocument();
    PsiDocumentManager.getInstance(project).commitDocument(document);
    int caretOffset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(caretOffset);
    if (element != null && element.getLanguage() instanceof XMLLanguage) {
      if (!(element instanceof XmlAttributeValue) && (text.indexOf('>') >= 0 || text.indexOf('<') >= 0)) {
        return text;
      }
      ASTNode cdata = TreeUtil.findParent(element.getNode(), XmlElementType.XML_CDATA);
      if (cdata == null) {
        ASTNode astNode = ENCODE_EACH_SYMBOL_POLICY.encodeXmlTextContents(text, element);
        return astNode.getTreeParent().getText();
      }
    }
    return text;
  }
}
