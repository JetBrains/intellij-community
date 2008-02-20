package com.intellij.codeInsight.editorActions;

import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.jsp.jspJava.JspXmlTagBase;
import com.intellij.psi.impl.source.jsp.jspXml.JspXmlRootTag;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.xml.util.XmlUtil;

public class XmlSlashTypedHandler extends TypedHandlerDelegate {
  public boolean beforeCharTyped(final char c, final Project project, final Editor editor, final PsiFile editedFile, final FileType fileType) {
    if (editedFile instanceof XmlFile && c == '/') {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      XmlFile file = (XmlFile)PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      final int offset = editor.getCaretModel().getOffset();
      FileViewProvider provider = file.getViewProvider();
      PsiElement element = provider.findElementAt(offset, XMLLanguage.class);

      if (element instanceof XmlToken) {
        final IElementType tokenType = ((XmlToken)element).getTokenType();

        if (tokenType == XmlTokenType.XML_EMPTY_ELEMENT_END &&
            offset == element.getTextOffset()
           ) {
          editor.getCaretModel().moveToOffset(offset + 1);
          editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          return true;
        } else if (tokenType == XmlTokenType.XML_TAG_END &&
                   offset == element.getTextOffset()
                  ) {
          final ASTNode parentNode = element.getParent().getNode();
          final ASTNode child = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(parentNode);

          if (child != null && offset + 1 == child.getTextRange().getStartOffset()) {
            editor.getDocument().replaceString(offset + 1, parentNode.getTextRange().getEndOffset(),"");
          }
        }
      }

      return false;

    }
    return false;
  }

  public boolean charTyped(final char c, final Project project, final Editor editor, final PsiFile editedFile) {
    if (editedFile instanceof XmlFile && c == '/') {
      PsiDocumentManager.getInstance(project).commitAllDocuments();

      XmlFile file = (XmlFile)PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      FileViewProvider provider = file.getViewProvider();
      final int offset = editor.getCaretModel().getOffset();
      PsiElement element = provider.findElementAt(offset - 1, XMLLanguage.class);
      if (element == null) return false;
      if (!(element.getLanguage() instanceof XMLLanguage)) return false;

      ASTNode prevLeaf = element.getNode();
      final String prevLeafText = prevLeaf != null ? prevLeaf.getText():null;
      if (prevLeaf != null && !"/".equals(prevLeafText)) {
        if (!"/".equals(prevLeafText.trim())) return false;
      }
      while((prevLeaf = TreeUtil.prevLeaf(prevLeaf)) != null && prevLeaf.getElementType() == XmlTokenType.XML_WHITE_SPACE);
      if(prevLeaf instanceof OuterLanguageElement) {
        element = file.getDocument().findElementAt(offset - 1);
        prevLeaf = element.getNode();
        while((prevLeaf = TreeUtil.prevLeaf(prevLeaf)) != null && prevLeaf.getElementType() == XmlTokenType.XML_WHITE_SPACE);
      }
      if(prevLeaf == null) return false;

      XmlTag tag = PsiTreeUtil.getParentOfType(prevLeaf.getPsi(), XmlTag.class);
      if(tag == null) { // prevLeaf maybe in one tree and element in another
        PsiElement element2 = provider.findElementAt(prevLeaf.getStartOffset(), XMLLanguage.class);
        tag = PsiTreeUtil.getParentOfType(element2, XmlTag.class);
        if (tag == null) return false;
      }

      if (tag instanceof JspXmlTagBase || tag instanceof JspXmlRootTag) return false;
      if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_TAG_END) != null) return false;
      if (XmlUtil.getTokenOfType(tag, XmlTokenType.XML_EMPTY_ELEMENT_END) != null) return false;
      if (PsiTreeUtil.getParentOfType(element, XmlAttributeValue.class) != null) return false;

      EditorModificationUtil.insertStringAtCaret(editor, ">");
      return true;
    }
    return false;
  }
}