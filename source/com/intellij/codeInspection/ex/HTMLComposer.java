/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 22, 2001
 * Time: 4:54:17 PM
 * To change template for new interface use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.export.HTMLExporter;
import com.intellij.codeInspection.reference.*;
import com.intellij.psi.*;

import java.util.Iterator;

/**
 * @author max
 */
public abstract class HTMLComposer {
  private HTMLExporter myExporter;
  private int[] myListStack;
  private int myListStackTop;

  protected HTMLComposer() {
    myListStack = new int[5];
    myListStackTop = -1;
  }

  public abstract void compose(StringBuffer buf, RefEntity refEntity);

  public void compose(StringBuffer buf, RefElement refElement, ProblemDescriptor descriptor) {}

  public void composeWithExporter(StringBuffer buf, RefEntity refEntity, HTMLExporter exporter) {
    myExporter = exporter;
    compose(buf, refEntity);
    myExporter = null;
  }

  protected void genPageHeader(final StringBuffer buf, RefEntity refEntity) {
    if (refEntity instanceof RefElement) {
      RefElement refElement = (RefElement)refEntity;

      appendHeading(buf, "Name");
      buf.append("<br>");
      appendAfterHeaderIndention(buf);
      appendAccessModifier(buf, refElement);
      appendShortName(buf, refElement);
      buf.append("<br><br>");

      appendHeading(buf, "Location");
      buf.append("<br>");
      appendAfterHeaderIndention(buf);
      appendLocation(buf, refElement);
      buf.append("<br><br>");
    }
  }

  public static void appendHeading(final StringBuffer buf, String name) {
    buf.append(
      "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<font style=\"font-family:verdana; font-weight:bold; color:#005555\"; size = \"3\">");
    buf.append(name);
    buf.append(":</font>");
  }

  private static void appendAccessModifier(final StringBuffer buf, RefElement refElement) {
    String modifier = refElement.getAccessModifier();
    if (modifier != null && modifier != PsiModifier.PACKAGE_LOCAL) {
      buf.append(modifier);
      buf.append("&nbsp;");
    }
  }

  private void appendLocation(final StringBuffer buf, final RefElement refElement) {
    RefEntity owner = refElement.getOwner();
    buf.append("<font style=\"font-family:verdana;\"");
    if (owner instanceof RefPackage) {
      buf.append("package&nbsp;<code>");
      buf.append(RefUtil.getPackageName(refElement));
      buf.append("</code>");
    }
    else if (owner instanceof RefMethod) {
      buf.append("method&nbsp;");
      appendElementReference(buf, (RefElement)owner);
    }
    else if (owner instanceof RefField) {
      buf.append("field&nbsp;");
      appendElementReference(buf, (RefElement)owner);
      buf.append("&nbsp;initializer");
    }
    else if (owner instanceof RefClass) {
      appendClassOrInterface(buf, (RefClass)owner, false);
      buf.append("&nbsp;");
      appendElementReference(buf, (RefElement)owner);
    }
    buf.append("</font>");
  }

  protected static void appendClassOrInterface(StringBuffer buf, RefClass refClass, boolean capitalizeFirstLetter) {
    if (refClass.isInterface()) {
      buf.append(capitalizeFirstLetter ? "Interface" : "interface");
    }
    else if (refClass.isAbstract()) {
      buf.append(capitalizeFirstLetter ? "Abstract&nbsp;class" : "abstract&nbsp;class");
    }
    else {
      buf.append(capitalizeFirstLetter ? "Class" : "class");
    }
  }

  private static void appendShortName(final StringBuffer buf, RefElement refElement) {
    refElement.accept(new RefVisitor() {
      public void visitClass(RefClass refClass) {
        if (refClass.isStatic()) {
          buf.append("static&nbsp;");
        }

        appendClassOrInterface(buf, refClass, false);
        buf.append("&nbsp;<b><code>");
        buf.append(refClass.getName());
        buf.append("</code></b>");
      }

      public void visitField(RefField field) {
        PsiField psiField = (PsiField)field.getElement();

        if (field.isStatic()) {
          buf.append("static&nbsp;");
        }

        buf.append("field&nbsp;<code>");

        buf.append(psiField.getType().getPresentableText());
        buf.append("&nbsp;<b>");
        buf.append(psiField.getName());
        buf.append("</b></code>");
      }

      public void visitMethod(RefMethod method) {
        PsiMethod psiMethod = (PsiMethod)method.getElement();
        PsiType returnType = psiMethod.getReturnType();

        if (method.isStatic()) {
          buf.append("static&nbsp;");
        }
        else if (method.isAbstract()) {
          buf.append("abstract&nbsp;");
        }

        buf.append(method.isConstructor() ? "constructor&nbsp;" : "method&nbsp;");
        buf.append("<code>");

        if (returnType != null) {
          buf.append(returnType.getPresentableText());
          buf.append("&nbsp;");
        }

        buf.append("<b>");
        buf.append(psiMethod.getName());
        buf.append("</b>");
        appendMethodParameters(buf, psiMethod, true);
        buf.append("</code>");
      }
    });
  }

  private static void appendMethodParameters(StringBuffer buf, PsiMethod method, boolean showNames) {
    PsiParameter[] params = method.getParameterList().getParameters();
    buf.append('(');
    for (int i = 0; i < params.length; i++) {
      if (i != 0) buf.append(", ");
      PsiParameter param = params[i];
      buf.append(param.getType().getPresentableText());
      if (showNames) {
        buf.append(' ');
        buf.append(param.getName());
      }
    }
    buf.append(')');
  }

  private static void appendQualifiedName(StringBuffer buf, RefEntity refEntity) {
    String qName = "";

    while (!(refEntity instanceof RefProject)) {
      if (qName.length() > 0) qName = "." + qName;

      final String name;
      if (refEntity instanceof RefMethod) {
        PsiMethod psiMethod = (PsiMethod)((RefMethod)refEntity).getElement();
        if (psiMethod != null) {
          name = psiMethod.getName();
        }
        else {
          name = refEntity.getName();
        }
      }
      else {
        name = refEntity.getName();
      }

      qName = name + qName;
      refEntity = refEntity.getOwner();
    }

    buf.append(qName);
  }

  private void appendElementReference(final StringBuffer buf, RefElement refElement) {
    appendElementReference(buf, refElement, true);
  }

  public void appendElementReference(final StringBuffer buf, RefElement refElement, String linkText, String frameName) {
    buf.append("<a HREF=\"");

    if (myExporter == null) {
      buf.append(refElement.getURL());
    }
    else {
      buf.append(myExporter.getURL(refElement));
    }

    if (frameName != null) {
      buf.append("\" target=\"");
      buf.append(frameName);
    }

    buf.append("\">");
    buf.append(linkText);
    buf.append("</a>");
  }

  protected void appendQuickFix(final StringBuffer buf, String text, int index) {
    if (myExporter == null) {
      buf.append("<font style=\"font-family:verdana;\"");
      buf.append("<a HREF=\"file://bred.txt#invoke:" + index);
      buf.append("\">");
      buf.append(text);
      buf.append("</a></font>");
    }
  }

  private void appendElementReference(final StringBuffer buf, RefElement refElement, boolean isPackageIncluded) {
    appendElementReference(buf, refElement, isPackageIncluded, null);
  }

  private void appendElementReference(final StringBuffer buf,
                                      RefElement refElement,
                                      boolean isPackageIncluded,
                                      String frameName) {
    if (refElement instanceof RefImplicitConstructor) {
      buf.append("implicit constructor of ");
      refElement = ((RefImplicitConstructor)refElement).getOwnerClass();
    }

    buf.append("<code>");
    if (refElement instanceof RefField) {
      RefField field = (RefField)refElement;
      PsiField psiField = (PsiField)field.getElement();
      buf.append(psiField.getType().getPresentableText());
      buf.append("&nbsp;");
    }
    else if (refElement instanceof RefMethod) {
      RefMethod method = (RefMethod)refElement;
      PsiMethod psiMethod = (PsiMethod)method.getElement();
      PsiType returnType = psiMethod.getReturnType();

      if (returnType != null) {
        buf.append(returnType.getPresentableText());
        buf.append("&nbsp;");
      }
    }

    buf.append("<a HREF=\"");

    if (myExporter == null) {
      buf.append(refElement.getURL());
    }
    else {
      buf.append(myExporter.getURL(refElement));
    }

    if (frameName != null) {
      buf.append("\" target=\"");
      buf.append(frameName);
    }

    buf.append("\">");

    if (RefUtil.isAnonymousClass(refElement)) {
      buf.append("anonymous");
    }
    else if (refElement instanceof RefMethod) {
      PsiMethod psiMethod = (PsiMethod)refElement.getElement();
      buf.append(psiMethod.getName());
    }
    else {
      buf.append(refElement.getName());
    }

    buf.append("</a>");

    if (refElement instanceof RefMethod) {
      PsiMethod psiMethod = (PsiMethod)refElement.getElement();
      appendMethodParameters(buf, psiMethod, false);
    }

    buf.append("</code>");

    if (RefUtil.isAnonymousClass(refElement)) {
      buf.append(" in ");
      appendElementReference(buf, ((RefElement)refElement.getOwner()), isPackageIncluded);
    }
    else if (isPackageIncluded) {
      buf.append(" <code><font style=\"font-family:verdana;color:#808080\">(");
      appendQualifiedName(buf, refElement.getOwner());
//      buf.append(RefUtil.getPackageName(refElement));
      buf.append(")</font></code>");
    }
  }

  protected static void appendNumereable(StringBuffer buf,
                                         int n,
                                         String statement,
                                         String singleEnding,
                                         String multipleEnding) {
    buf.append(n);
    buf.append(' ');
    buf.append(statement);

    if (n % 10 == 1 && n % 100 != 11) {
      buf.append(singleEnding);
    }
    else {
      buf.append(multipleEnding);
    }
  }

  protected void appendElementInReferences(StringBuffer buf, RefElement refElement) {
    if (refElement.getInReferences().size() > 0) {
      appendHeading(buf, "Used from");
      startList();
      for (Iterator<RefElement> iterator = refElement.getInReferences().iterator(); iterator.hasNext();) {
        RefElement refCaller = iterator.next();
        appendListItem(buf, refCaller);
      }
      doneList(buf);
    }
  }

  protected void appendElementOutReferences(StringBuffer buf, RefElement refElement) {
    if (refElement.getOutReferences().size() > 0) {
      appendHeading(buf, "Uses the following");
      startList();
      for (Iterator<RefElement> iterator = refElement.getOutReferences().iterator(); iterator.hasNext();) {
        RefElement refCallee = iterator.next();
        appendListItem(buf, refCallee);
      }
      doneList(buf);
    }
  }

  protected void appendListItem(StringBuffer buf, RefElement refElement) {
    startListItem(buf);
    buf.append("<font style=\"font-family:verdana;\"");
    appendElementReference(buf, refElement, true);
    appendAdditionalListItemInfo(buf, refElement);
    buf.append("</font>");
    doneListItem(buf);
  }

  protected void appendAdditionalListItemInfo(StringBuffer buf, RefElement refElement) {
    // Default appends nothing.
  }

  protected void appendClassExtendsImplements(StringBuffer buf, RefClass refClass) {
    if (refClass.getBaseClasses().size() > 0) {
      appendHeading(buf, "Extends/implements");
      startList();
      for (Iterator<RefClass> iterator = refClass.getBaseClasses().iterator(); iterator.hasNext();) {
        RefClass refBase = iterator.next();
        appendListItem(buf, refBase);
      }
      doneList(buf);
    }
  }

  protected void appendDerivedClasses(StringBuffer buf, RefClass refClass) {
    if (refClass.getSubClasses().size() > 0) {
      if (refClass.isInterface()) {
        appendHeading(buf, "Extended/implemented by");
      }
      else {
        appendHeading(buf, "Extended by");
      }

      startList();
      for (Iterator<RefClass> iterator = refClass.getSubClasses().iterator(); iterator.hasNext();) {
        RefClass refDerived = iterator.next();
        appendListItem(buf, refDerived);
      }
      doneList(buf);
    }
  }

  protected void appendLibraryMethods(StringBuffer buf, RefClass refClass) {
    if (refClass.getLibraryMethods().size() > 0) {
      appendHeading(buf, "Overrides library methods");

      startList();
      for (Iterator<RefMethod> iterator = refClass.getLibraryMethods().iterator(); iterator.hasNext();) {
        RefMethod refMethod = iterator.next();
        appendListItem(buf, refMethod);
      }
      doneList(buf);
    }
  }

  protected void appendSuperMethods(StringBuffer buf, RefMethod refMethod) {
    if (refMethod.getSuperMethods().size() > 0) {
      appendHeading(buf, "Overrides/implements");

      startList();
      for (Iterator<RefMethod> iterator = refMethod.getSuperMethods().iterator(); iterator.hasNext();) {
        RefMethod refSuper = iterator.next();
        appendListItem(buf, refSuper);
      }
      doneList(buf);
    }
  }

  protected void appendDerivedMethods(StringBuffer buf, RefMethod refMethod) {
    if (refMethod.getDerivedMethods().size() > 0) {
      appendHeading(buf, "Derived methods");

      startList();
      for (Iterator<RefMethod> iterator = refMethod.getDerivedMethods().iterator(); iterator.hasNext();) {
        RefMethod refDerived = iterator.next();
        appendListItem(buf, refDerived);
      }
      doneList(buf);
    }
  }

  protected void appendTypeReferences(StringBuffer buf, RefClass refClass) {
    if (refClass.getInTypeReferences().size() > 0) {
      appendHeading(buf, "The following uses this type");

      startList();
      for (Iterator iterator = refClass.getInTypeReferences().iterator(); iterator.hasNext();) {
        RefElement refElement = (RefElement)iterator.next();
        appendListItem(buf, refElement);
      }
      doneList(buf);
    }
  }

  protected void appendResolution(StringBuffer buf, InspectionTool tool, RefElement where) {
    QuickFixAction[] quickFixes = tool.getQuickFixes();
    if (quickFixes != null) {
      boolean listStarted = false;
      for (int i = 0; i < quickFixes.length; i++) {
        QuickFixAction quickFix = quickFixes[i];
        final String text = quickFix.getText(where);
        if (text == null) continue;
        if (!listStarted) {
          appendHeading(buf, "Problem resolution");
          startList();
          listStarted = true;
        }
        startListItem(buf);
        appendQuickFix(buf, text, i);
        doneListItem(buf);
      }

      if (listStarted) {
        doneList(buf);
      }
    }
  }

  protected void startList() {
    myListStackTop++;
    myListStack[myListStackTop] = 0;
  }

  protected void doneList(StringBuffer buf) {
    if (myListStack[myListStackTop] != 0) {
      buf.append("<table cellpadding=\"0\" border=\"0\" cellspacing=\"0\"><tr><td>&nbsp;</td></tr></table>");
    }
    myListStackTop--;
  }

  protected void startListItem(StringBuffer buf) {
    myListStack[myListStackTop]++;
    buf.append("<li>");
  }

  protected static void doneListItem(StringBuffer buf) {
    buf.append("</li>");
  }

  public static void appendAfterHeaderIndention(StringBuffer buf) {
    buf.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
  }

  protected static void appendNoProblems(StringBuffer buf) {
    buf.append("<br>");
    appendAfterHeaderIndention(buf);
    buf.append("<b>No problems found</b></br>");
  }
}
