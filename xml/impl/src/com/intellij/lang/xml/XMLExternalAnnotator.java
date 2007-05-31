package com.intellij.lang.xml;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlTagUtil;

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


  private void addMessagesForTag(XmlTag tag, String message, int type) {
    XmlToken childByRole = XmlTagUtil.getStartTagNameElement(tag);

    addMessagesForTreeChild(childByRole, type, message);

    childByRole = XmlTagUtil.getEndTagNameElement(tag);
    addMessagesForTreeChild(childByRole, type, message);
  }

  private void addMessagesForTreeChild(final XmlToken childByRole, final int type, final String message) {
    if (childByRole != null) {
      if (type == ERROR) {
        myHolder.createErrorAnnotation(childByRole, message);
      }
      else {
        myHolder.createWarningAnnotation(childByRole, message);
      }
    }
  }
}
