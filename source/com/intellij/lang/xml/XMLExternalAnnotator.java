package com.intellij.lang.xml;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlNSDescriptor;

/**
 * @author ven
 */
public class XMLExternalAnnotator implements ExternalAnnotator, Validator.ValidationHost {
  private AnnotationHolder myHolder;

  public void annotate(PsiFile file, AnnotationHolder holder) {
    myHolder = holder;
    final XmlDocument document = ((XmlFile)file).getDocument();
    if (document == null) return;
    XmlTag rootTag = document.getRootTag();
    XmlNSDescriptor nsDescriptor = rootTag == null ? null : rootTag.getNSDescriptor(rootTag.getNamespace(), false);

    final Project project = file.getProject();

    if (nsDescriptor instanceof Validator) {
      ((Validator)nsDescriptor).validate(document, this);
    }
  }

  public void addMessage(PsiElement context, String message, int type) {
    if (message != null && message.length() > 0) {
      if (context instanceof XmlTag) {
        addMessagesForTag((XmlTag)context, message, type);
      }
      else {
        if (type == Validator.ValidationHost.ERROR) {
          myHolder.createErrorAnnotation(context, message);
        } else {
          myHolder.createWarningAnnotation(context, message);
        }
      }
    }
  }


  private void addMessagesForTag(XmlTag tag,
                                 String message,
                                 int type) {
    ASTNode tagElement = SourceTreeToPsiMap.psiElementToTree(tag);
    ASTNode childByRole = XmlChildRole.START_TAG_NAME_FINDER.findChild(tagElement);

    addMessagesForTreeChild(childByRole, type, message);

    childByRole = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(tagElement);
    addMessagesForTreeChild(childByRole, type, message);
  }

  private void addMessagesForTreeChild(final ASTNode childByRole, final int type, final String message) {
    if (childByRole != null) {
      if (type == ERROR) {
        myHolder.createErrorAnnotation(childByRole.getPsi(), message);
      }
      else {
        myHolder.createWarningAnnotation(childByRole.getPsi(), message);
      }
    }
  }
}
