/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: May 23, 2002
 * Time: 2:36:58 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.util;

import com.intellij.codeInspection.reference.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiFormatUtil;
import org.jdom.Element;

public class XMLExportUtl {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.util.XMLExportUtl");
  public static Element createElement(RefElement refElement, Element parentNode, int actualLine) {
    if (refElement instanceof RefImplicitConstructor) {
      return createElement((RefElement) refElement.getOwner(), parentNode, actualLine);
    }

    Element problem = new Element("problem");

    PsiElement psiElement = refElement.getElement();
    PsiFile psiFile = psiElement.getContainingFile();

    Element fileElement = new Element("file");
    Element lineElement = new Element("line");
    fileElement.addContent(psiFile.getVirtualFile().getUrl());

    if (actualLine == -1) {
      Document document = PsiDocumentManager.getInstance(refElement.getRefManager().getProject()).getDocument(psiFile);
      lineElement.addContent(String.valueOf(document.getLineNumber(psiElement.getTextOffset()) + 1));
    } else {
      lineElement.addContent(String.valueOf(actualLine));
    }

    problem.addContent(fileElement);
    problem.addContent(lineElement);


    if (refElement instanceof RefMethod) {
      RefMethod refMethod = (RefMethod) refElement;
      appendMethod(refMethod, problem);
    } else if (refElement instanceof RefField) {
      RefField refField = (RefField) refElement;
      appendField(refField, problem);
    } else if (refElement instanceof RefClass) {
      RefClass refClass = (RefClass)refElement;
      appendClass(refClass, problem);
    } else {
      LOG.error("Unknown refElement");
    }
    parentNode.addContent(problem);

    return problem;
  }

  private static void appendClass(RefClass refClass, Element parentNode) {
    PsiClass psiClass = (PsiClass) refClass.getElement();
    PsiDocComment psiDocComment = psiClass.getDocComment();

    PsiFile psiFile = psiClass.getContainingFile();

    if (psiFile instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile)psiFile).getPackageName();
      Element packageElement = new Element("package");
      packageElement.addContent(packageName.length() > 0 ? packageName : "<default>");
      parentNode.addContent(packageElement);
    }

    Element classElement = new Element("class");
    if (psiDocComment != null) {
      PsiDocTag[] tags = psiDocComment.getTags();
      for (int i = 0; i < tags.length; i++) {
        PsiDocTag tag = tags[i];
        if ("author".equals(tag.getName()) && tag.getValueElement() != null) {
          classElement.setAttribute("author", tag.getValueElement().getText());
        }
      }
    }

    String name = PsiFormatUtil.formatClass(psiClass, PsiFormatUtil.SHOW_NAME);
    Element nameElement = new Element("name");
    nameElement.addContent(name);
    classElement.addContent(nameElement);

    Element displayName = new Element("display_name");
    displayName.addContent(RefUtil.getQualifiedName(refClass));
    classElement.addContent(displayName);

    parentNode.addContent(classElement);

    RefClass topClass = RefUtil.getTopLevelClass(refClass);
    if (topClass != refClass) {
      appendClass(topClass, classElement);
    }
  }

  private static void appendMethod(final RefMethod refMethod, Element parentNode) {
    Element methodElement = new Element(refMethod.isConstructor() ? "constructor" : "method");

    PsiMethod psiMethod = (PsiMethod) refMethod.getElement();
    String name = PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME |
                                                        PsiFormatUtil.SHOW_FQ_NAME |
                                                        PsiFormatUtil.SHOW_TYPE |
                                                        PsiFormatUtil.SHOW_PARAMETERS,
                                             PsiFormatUtil.SHOW_NAME |
                                             PsiFormatUtil.SHOW_TYPE
    );

    Element shortNameElement = new Element("name");
    shortNameElement.addContent(name);
    methodElement.addContent(shortNameElement);

    Element displayName = new Element("display_name");
    displayName.addContent(RefUtil.getQualifiedName(refMethod));
    methodElement.addContent(displayName);

    appendClass(RefUtil.getTopLevelClass(refMethod), methodElement);

    parentNode.addContent(methodElement);
  }

  private static void appendField(final RefField refField, Element parentNode) {
    Element fieldElement = new Element("field");
    PsiField psiField = (PsiField) refField.getElement();
    String name = PsiFormatUtil.formatVariable(psiField, PsiFormatUtil.SHOW_NAME |
                                                         PsiFormatUtil.SHOW_TYPE,
        PsiSubstitutor.EMPTY);

    Element shortNameElement = new Element("name");
    shortNameElement.addContent(name);
    fieldElement.addContent(shortNameElement);

    Element displayName = new Element("display_name");
    displayName.addContent(RefUtil.getQualifiedName(refField));
    fieldElement.addContent(displayName);

    appendClass(RefUtil.getTopLevelClass(refField), fieldElement);

    parentNode.addContent(fieldElement);
  }
}
