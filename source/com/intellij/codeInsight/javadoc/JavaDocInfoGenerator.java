package com.intellij.codeInsight.javadoc;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ArrayUtil;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class JavaDocInfoGenerator {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.javadoc.JavaDocInfoGenerator");

  private static final Pattern ourNotDot = Pattern.compile("[^.]");
  private static final Pattern ourWhitespaces = Pattern.compile("[ \\n\\r\\t]+");
  private static final Matcher ourNotDotMatcher = ourNotDot.matcher("");
  private static final Matcher ourWhitespacesMatcher = ourWhitespaces.matcher("");

  private Project myProject;
  private PsiElement myElement;
  private JavaDocManager.DocumentationProvider myProvider;

  interface InheritDocProvider <T> {
    Pair<T, InheritDocProvider<T>> getInheritDoc();

    PsiClass getElement();
  }

  private static final InheritDocProvider<PsiDocTag> ourEmptyProvider = new InheritDocProvider<PsiDocTag>() {
    public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
      return null;
    }

    public PsiClass getElement() {
      return null;
    }
  };

  private static final InheritDocProvider<PsiElement[]> ourEmptyElementsProvider = mapProvider(ourEmptyProvider, false);

  private static InheritDocProvider<PsiElement[]> mapProvider(final InheritDocProvider<PsiDocTag> i,
                                                              final boolean dropFirst) {
    return new InheritDocProvider<PsiElement[]>() {
      public Pair<PsiElement[], InheritDocProvider<PsiElement[]>> getInheritDoc() {
        Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair = i.getInheritDoc();

        if (pair == null) {
          return null;
        }

        PsiElement[] elements;
        PsiElement[] rawElements = pair.first.getDataElements();

        if (dropFirst && rawElements != null && rawElements.length > 0) {
          elements = new PsiElement[rawElements.length - 1];

          for (int i = 0; i < elements.length; i++) {
            elements[i] = rawElements[i + 1];
          }
        }
        else {
          elements = rawElements;
        }

        return new Pair<PsiElement[], InheritDocProvider<PsiElement[]>>(elements, mapProvider(pair.second, dropFirst));
      }

      public PsiClass getElement() {
        return i.getElement();
      }
    };
  }

  interface DocTagLocator <T> {
    T find(PsiDocComment comment);
  }

  private DocTagLocator<PsiDocTag> parameterLocator(final String name) {
    return new DocTagLocator<PsiDocTag>() {
      public PsiDocTag find(PsiDocComment comment) {
        if (comment == null) {
          return null;
        }

        PsiDocTag[] tags = comment.findTagsByName("param");

        for (int i = 0; i < tags.length; i++) {
          PsiDocTag tag = tags[i];
          PsiDocTagValue value = tag.getValueElement();

          if (value != null) {
            String text = value.getText();

            if (text != null && text.equals(name)) {
              return tag;
            }
          }
        }

        return null;
      }
    };
  }

  private DocTagLocator<PsiDocTag> exceptionLocator(final String name) {
    return new DocTagLocator<PsiDocTag>() {
      public PsiDocTag find(PsiDocComment comment) {
        if (comment == null) {
          return null;
        }

        PsiDocTag[] tags = getThrowsTags(comment);

        for (int i = 0; i < tags.length; i++) {
          PsiDocTag tag = tags[i];
          PsiDocTagValue value = tag.getValueElement();

          if (value != null) {
            String text = value.getText();

            if (text != null && areWeakEqual(text, name)) {
              return tag;
            }
          }
        }

        return null;
      }
    };
  }

  public JavaDocInfoGenerator(Project project, PsiElement element,JavaDocManager.DocumentationProvider _provider) {
    myProject = project;
    myElement = element;
    myProvider = _provider;
  }

  public String generateDocInfo() {
    StringBuffer buffer = new StringBuffer();
    if (myElement instanceof PsiClass) {
      generateClassJavaDoc(buffer, (PsiClass)myElement);
    }
    else if (myElement instanceof PsiMethod) {
      generateMethodJavaDoc(buffer, (PsiMethod)myElement);
    }
    else if (myElement instanceof PsiField) {
      generateFieldJavaDoc(buffer, (PsiField)myElement);
    }
    else if (myElement instanceof PsiVariable) {
      generateVariableJavaDoc(buffer, (PsiVariable)myElement);
    }
    else if (myElement instanceof PsiFile) {
      generateFileJavaDoc(buffer, (PsiFile)myElement); //used for Ctrl-Click
    }
    else {
      if (myProvider!=null) {
        return myProvider.generateDoc(myElement,myElement.getUserData(JavaDocManager.ORIGINAL_ELEMENT_KEY));
      }
      return null;
    }
    String text = buffer.toString();
    if (text.length() == 0) {
      return null;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Generated JavaDoc:");
      LOG.debug(text);
    }

    text = StringUtil.replace(text, "/>", ">");

    return text;
  }

  private void generateClassJavaDoc(StringBuffer buffer, PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) return;
    PsiManager manager = aClass.getManager();
    generatePrologue(buffer);

    PsiFile file = aClass.getContainingFile();
    if (file instanceof PsiJavaFile) {
      String packageName = ((PsiJavaFile)file).getPackageName();
      if (packageName.length() > 0) {
        buffer.append("<font size=\"-1\"><b>");
        buffer.append(packageName);
        buffer.append("</b></font>");
        //buffer.append("<br>");
      }
    }

    buffer.append("<PRE>");
    String modifiers = PsiFormatUtil.formatModifiers(aClass, PsiFormatUtil.JAVADOC_MODIFIERS_ONLY);
    if (modifiers.length() > 0) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
    buffer.append(aClass.isInterface() ? "interface" : "class");
    buffer.append(" ");
    String refText = JavaDocUtil.getReferenceText(myProject, aClass);
    if (refText == null) {
      buffer.setLength(0);
      return;
    }
    String labelText = JavaDocUtil.getLabelText(myProject, manager, refText, aClass);
    buffer.append("<b>");
    buffer.append(labelText);
    buffer.append("</b>");

    buffer.append(generateTypeParameters(aClass));

    buffer.append("\n");

    PsiClassType[] refs = JavaDocUtil.getExtendsList(aClass);

    String qName = aClass.getQualifiedName();

    if (refs.length > 0 || !aClass.isInterface() && (qName == null || !qName.equals("java.lang.Object"))) {
      buffer.append("extends ");
      if (refs.length == 0) {
        generateLink(buffer, "java.lang.Object", null, aClass, false);
      }
      else {
        for (int i = 0; i < refs.length; i++) {
          generateType(buffer, refs[i], aClass);
          if (i < refs.length - 1) {
            buffer.append(",&nbsp;");
          }
        }
      }
      buffer.append("\n");
    }

    refs = JavaDocUtil.getImplementsList(aClass);

    if (refs.length > 0) {
      buffer.append("implements ");
      for (int i = 0; i < refs.length; i++) {
        generateType(buffer, refs[i], aClass);
        if (i < refs.length - 1) {
          buffer.append(",&nbsp;");
        }
      }
      buffer.append("\n");
    }
    if (buffer.charAt(buffer.length() - 1) == '\n') {
      buffer.setLength(buffer.length() - 1);
    }
    buffer.append("</PRE>");
    //buffer.append("<br>");

    PsiDocComment comment = aClass.getDocComment();
    if (comment != null) {
      generateDescription(buffer, comment);
      generateDeprecatedSection(buffer, comment);
      generateSinceSection(buffer, comment);
      generateSeeAlsoSection(buffer, comment);
    }

    generateEpilogue(buffer);
  }

  private void generateFieldJavaDoc(StringBuffer buffer, PsiField field) {
    generatePrologue(buffer);

    PsiClass parentClass = field.getContainingClass();
    if (parentClass != null) {
      String qName = parentClass.getQualifiedName();
      if (qName != null) {
        buffer.append("<font size=\"-1\"><b>");
        //buffer.append(qName);
        generateLink(buffer, qName, qName, field, false);
        buffer.append("</b></font>");
        //buffer.append("<br>");
      }
    }

    buffer.append("<PRE>");
    String modifiers = PsiFormatUtil.formatModifiers(field, PsiFormatUtil.JAVADOC_MODIFIERS_ONLY);
    if (modifiers.length() > 0) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
    generateType(buffer, field.getType(), field);
    buffer.append(" ");
    buffer.append("<b>");
    buffer.append(field.getName());
    appendInitializer(buffer, field);
    buffer.append("</b>");
    buffer.append("</PRE>");
    //buffer.append("<br>");

    PsiDocComment comment = field.getDocComment();
    if (comment != null) {
      generateDescription(buffer, comment);
      generateDeprecatedSection(buffer, comment);
      generateSinceSection(buffer, comment);
      generateSeeAlsoSection(buffer, comment);
    }

    generateEpilogue(buffer);
  }

  // not a javadoc in fact..
  private void generateVariableJavaDoc(StringBuffer buffer, PsiVariable variable) {
    generatePrologue(buffer);

    buffer.append("<PRE>");
    String modifiers = PsiFormatUtil.formatModifiers(variable, PsiFormatUtil.JAVADOC_MODIFIERS_ONLY);
    if (modifiers.length() > 0) {
      buffer.append(modifiers);
      buffer.append(" ");
    }
    generateType(buffer, variable.getType(), variable);
    buffer.append(" ");
    buffer.append("<b>");
    buffer.append(variable.getName());
    appendInitializer(buffer, variable);
    buffer.append("</b>");
    buffer.append("</PRE>");
    //buffer.append("<br>");

    generateEpilogue(buffer);
  }

  // not a javadoc in fact..
  private void generateFileJavaDoc(StringBuffer buffer, PsiFile file) {
    generatePrologue(buffer);
    buffer.append(file.getVirtualFile().getPresentableUrl());
    generateEpilogue(buffer);
  }

  private void appendInitializer(StringBuffer buffer, PsiVariable variable) {
    PsiExpression initializer = variable.getInitializer();
    if (initializer != null) {
      String text = initializer.getText();
      text = text.trim();
      int index1 = text.indexOf('\n');
      if (index1 < 0) index1 = text.length();
      int index2 = text.indexOf('\r');
      if (index2 < 0) index2 = text.length();
      int index = Math.min(index1, index2);
      boolean trunc = index < text.length();
      text = text.substring(0, index);
      buffer.append(" = ");
      text = StringUtil.replace(text, "<", "&lt;");
      text = StringUtil.replace(text, ">", "&gt;");
      buffer.append(text);
      if (trunc) {
        buffer.append("...");
      }
    }
  }

  private void generateMethodJavaDoc(StringBuffer buffer, PsiMethod method) {
    generatePrologue(buffer);

    PsiClass parentClass = method.getContainingClass();
    if (parentClass != null) {
      String qName = parentClass.getQualifiedName();
      if (qName != null) {
        buffer.append("<font size=\"-1\"><b>");
        generateLink(buffer, qName, qName, method, false);
        //buffer.append(qName);
        buffer.append("</b></font>");
        //buffer.append("<br>");
      }
    }

    buffer.append("<PRE>");
    int indent = 0;
    String modifiers = PsiFormatUtil.formatModifiers(method, PsiFormatUtil.JAVADOC_MODIFIERS_ONLY);
    if (modifiers.length() > 0) {
      buffer.append(modifiers);
      buffer.append("&nbsp;");
      indent += modifiers.length() + 1;
    }

    PsiTypeParameter[] params = method.getTypeParameterList().getTypeParameters();

    if (params.length > 0) {
      buffer.append("&lt;");
      for (int i = 0; i < params.length; i++) {
        PsiTypeParameter param = params[i];

        buffer.append(param.getName());

        PsiClassType[] extendees = JavaDocUtil.getExtendsList(param);

        if (extendees.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < extendees.length; j++) {
            generateType(buffer, extendees[j], method);

            if (j < extendees.length - 1) {
              buffer.append(" & ");
            }
          }
        }

        if (i < params.length - 1) {
          buffer.append(", ");
        }
      }
      buffer.append("&gt; ");
    }

    if (method.getReturnType() != null) {
      indent += generateType(buffer, method.getReturnType(), method);
      buffer.append("&nbsp;");
      indent++;
    }
    buffer.append("<b>");
    String name = method.getName();
    buffer.append(name);
    buffer.append("</b>");
    indent += name.length();

    buffer.append("(");
    indent++;
    indent--;//???
    PsiParameter[] parms = method.getParameterList().getParameters();
    for (int i = 0; i < parms.length; i++) {
      PsiParameter parm = parms[i];
      generateType(buffer, parm.getType(), method);
      buffer.append("&nbsp;");
      if (parm.getName() != null) {
        buffer.append(parm.getName());
      }
      if (i < parms.length - 1) {
        buffer.append(",\n");
        for (int j = 0; j < indent; j++) {
          buffer.append(" ");
        }
      }
    }
    buffer.append(")");

    PsiClassType[] refs = method.getThrowsList().getReferencedTypes();
    if (refs.length > 0) {
      buffer.append("\n");
      indent -= "throws".length() + 1;
      for (int i = 0; i < indent; i++) {
        buffer.append(" ");
      }
      indent += "throws".length() + 1;
      buffer.append("throws&nbsp;");
      for (int i = 0; i < refs.length; i++) {
        generateLink(buffer, refs[i].getCanonicalText(), null, method, false);
        if (i < refs.length - 1) {
          buffer.append(",\n");
          for (int j = 0; j < indent; j++) {
            buffer.append(" ");
          }
        }
      }
    }

    buffer.append("</PRE>");
    //buffer.append("<br>");

    PsiDocComment comment = method.getDocComment();

    generateMethodDescription(buffer, method);

    generateSuperMethodsSection(buffer, method, false);
    generateSuperMethodsSection(buffer, method, true);

    if (comment != null) {
      generateDeprecatedSection(buffer, comment);
    }

    generateParametersSection(buffer, method);
    generateReturnsSection(buffer, method);
    generateThrowsSection(buffer, method);

    if (comment != null) {
      generateSinceSection(buffer, comment);
      generateSeeAlsoSection(buffer, comment);
    }

    generateEpilogue(buffer);
  }

  private void generatePrologue(StringBuffer buffer) {
    buffer.append("<html><body>");
  }

  private void generateEpilogue(StringBuffer buffer) {
    while (true) {
      if (buffer.length() < "<br>".length()) break;
      char c = buffer.charAt(buffer.length() - 1);
      if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
        buffer.setLength(buffer.length() - 1);
        continue;
      }
      String tail = buffer.substring(buffer.length() - "<br>".length());
      if (tail.equalsIgnoreCase("<br>")) {
        buffer.setLength(buffer.length() - "<br>".length());
        continue;
      }
      break;
    }
    buffer.append("</body></html>");
  }

  private void generateDescription(StringBuffer buffer, PsiDocComment comment) {
    PsiElement[] elements = comment.getDescriptionElements();
    generateValue(buffer, elements, ourEmptyElementsProvider);
  }

  private boolean isEmptyDescription(PsiDocComment comment) {
    PsiElement[] description = comment.getDescriptionElements();

    for (int i = 0; i < description.length; i++) {
      String text = description[i].getText();

      if (text != null) {
        if (ourWhitespacesMatcher.reset(text).replaceAll("").length() != 0) {
          return false;
        }
      }
    }

    return true;
  }

  private void generateMethodDescription(StringBuffer buffer, final PsiMethod method) {
    final DocTagLocator<PsiElement[]> descriptionLocator = new DocTagLocator<PsiElement[]>() {
      public PsiElement[] find(PsiDocComment comment) {
        if (comment == null) {
          return null;
        }

        if (isEmptyDescription(comment)) {
          return null;
        }

        return comment.getDescriptionElements();
      }
    };

    PsiDocComment comment = method.getDocComment();

    if (comment != null) {
      if (!isEmptyDescription(comment)) {
        generateValue
          (buffer, comment.getDescriptionElements(),
           new InheritDocProvider<PsiElement[]>() {
             public Pair<PsiElement[], InheritDocProvider<PsiElement[]>> getInheritDoc() {
               return findInheritDocTag(method, descriptionLocator);
             }

             public PsiClass getElement() {
               return method.getContainingClass();
             }
           });
        return;
      }
    }

    Pair<PsiElement[], InheritDocProvider<PsiElement[]>> pair = findInheritDocTag(method, descriptionLocator);

    if (pair != null) {
      PsiElement[] elements = pair.first;
      PsiClass extendee = pair.second.getElement();

      if (elements != null) {
        buffer.append("<DD><DL>");
        buffer.append("<DT><b>");
        buffer.append("Description copied from " + (extendee.isInterface() ? "interface: " : "class: "));
        buffer.append("</b>");
        generateLink(buffer, extendee, JavaDocUtil.getShortestClassName(extendee, method));
        buffer.append("<br>");
        generateValue(buffer, elements, pair.second);
        buffer.append("</DD></DL></DD>");
      }
    }
  }

  private void generateValue(StringBuffer buffer, PsiElement[] elements, InheritDocProvider<PsiElement[]> provider) {
    generateValue(buffer, elements, 0, provider);
  }

  private String getDocRoot() {
    PsiClass aClass = null;

    if (myElement instanceof PsiClass) {
      aClass = (PsiClass)myElement;
    }
    else if (myElement instanceof PsiMember) {
      aClass = ((PsiMember)myElement).getContainingClass();
    }
    else {
      LOG.error("Class or member expected but found " + myElement.getClass().getName());
    }

    String qName = aClass.getQualifiedName();

    if (qName == null) {
      return "";
    }

    return "../" + ourNotDotMatcher.reset(qName).replaceAll("").replaceAll(".", "../");
  }

  private void generateValue(StringBuffer buffer,
                             PsiElement[] elements,
                             int startIndex,
                             InheritDocProvider<PsiElement[]> provider) {
    int predictOffset =
      startIndex < elements.length ?
      elements[startIndex].getTextOffset() + elements[startIndex].getText().length() :
      0;

    for (int i = startIndex; i < elements.length; i++) {
      if (elements[i].getTextOffset() > predictOffset) buffer.append(" ");
      predictOffset = elements[i].getTextOffset() + elements[i].getText().length();
      PsiElement element = elements[i];
      if (element instanceof PsiInlineDocTag) {
        PsiInlineDocTag tag = (PsiInlineDocTag)element;
        if (tag.getName().equals("link")) {
          generateLinkValue(tag, buffer, false);
        }
        else if (tag.getName().equals("literal")) {
          generateLiteralValue(tag, buffer);
        }
        else if (tag.getName().equals("code")) {
          generateCodeValue(tag, buffer);
        }
        else if (tag.getName().equals("linkplain")) {
          generateLinkValue(tag, buffer, true);
        }
        else if (tag.getName().equals("inheritDoc")) {
          Pair<PsiElement[], InheritDocProvider<PsiElement[]>> inheritInfo = provider.getInheritDoc();

          if (inheritInfo != null) {
            generateValue(buffer, inheritInfo.first, inheritInfo.second);
          }
        }
        else if (tag.getName().equals("docRoot")) {
          buffer.append(getDocRoot());
        }
        else {
          buffer.append(element.getText());
        }
      }
      else {
        buffer.append(element.getText());
      }
    }
  }

  private void generateCodeValue(PsiInlineDocTag tag, StringBuffer buffer) {
    buffer.append("<code>");
    generateLiteralValue(tag, buffer);
    buffer.append("</code>");
  }

  private void generateLiteralValue(PsiInlineDocTag tag, StringBuffer buffer) {
    PsiElement[] elements = tag.getDataElements();

    for (int i = 0; i < elements.length; i++) {
      String text = elements[i].getText();

      text = text.replaceAll("<", "&lt;");
      text = text.replaceAll(">", "&gt;");

      buffer.append(text);
    }
  }

  private void generateLinkValue(PsiInlineDocTag tag, StringBuffer buffer, boolean plainLink) {
    PsiElement[] tagElements = tag.getDataElements();
    int predictOffset = tagElements.length > 0
                        ? tagElements[0].getTextOffset() + tagElements[0].getText().length()
                        : 0;
    StringBuffer buffer1 = new StringBuffer();
    for (int j = 0; j < tagElements.length; j++) {
      PsiElement tagElement = tagElements[j];

      if (tagElement.getTextOffset() > predictOffset) buffer1.append(" ");
      predictOffset = tagElement.getTextOffset() + tagElement.getText().length();

      buffer1.append(tagElement.getText());

      if (j < tagElements.length - 1) {
        buffer1.append(" ");
      }
    }
    String text = buffer1.toString().trim();
    if (text.length() > 0) {
      int index = JavaDocUtil.extractReference(text);
      String refText = text.substring(0, index).trim();
      String label = text.substring(index).trim();
      if (label.length() == 0) {
        label = null;
      }
      generateLink(buffer, refText, label, tagElements[0], plainLink);
    }
  }

  private void generateDeprecatedSection(StringBuffer buffer, PsiDocComment comment) {
    PsiDocTag tag = comment.findTagByName("deprecated");
    if (tag != null) {
      buffer.append("<DD><DL>");
      buffer.append("<B>Deprecated.</B>&nbsp;");
      buffer.append("<I>");
      generateValue(buffer, tag.getDataElements(), ourEmptyElementsProvider);
      buffer.append("</I>");
      buffer.append("</DL></DD>");
    }
  }

  private void generateSinceSection(StringBuffer buffer, PsiDocComment comment) {
    PsiDocTag tag = comment.findTagByName("since");
    if (tag != null) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>Since:</b>");
      buffer.append("<DD>");
      generateValue(buffer, tag.getDataElements(), ourEmptyElementsProvider);
      buffer.append("</DD></DL></DD>");
    }
  }

  private void generateSeeAlsoSection(StringBuffer buffer, PsiDocComment comment) {
    PsiDocTag[] tags = comment.findTagsByName("see");
    if (tags.length > 0) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>See Also:</b>");
      buffer.append("<DD>");
      for (int i = 0; i < tags.length; i++) {
        PsiDocTag tag = tags[i];
        PsiElement[] elements = tag.getDataElements();
        if (elements.length > 0) {
          String text = elements[0].getText();
          if (StringUtil.startsWithChar(text, '\"') || StringUtil.startsWithChar(text, '<')) {
            buffer.append(text);
          }
          else {
            int index = JavaDocUtil.extractReference(text);
            String refText = text.substring(0, index).trim();
            String label = text.substring(index).trim();
            if (label.length() == 0) {
              label = null;
            }
            generateLink(buffer, refText, label, comment, false);
          }
          generateValue(buffer, elements, 1, ourEmptyElementsProvider);
        }
        if (i < tags.length - 1) {
          buffer.append(",\n");
        }
      }
      buffer.append("</DD></DL></DD>");
    }
  }

  private void generateParametersSection(StringBuffer buffer, final PsiMethod method) {
    PsiDocComment comment = method.getDocComment();
    PsiParameter[] params = method.getParameterList().getParameters();
    PsiDocTag[] localTags = comment != null ? localTags = comment.findTagsByName("param") : PsiDocTag.EMPTY_ARRAY;

    LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>> collectedTags =
      new LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>>();

    for (int i = 0; i < params.length; i++) {
      final String paramName = params[i].getName();
      Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> parmTag = null;

      for (int j = 0; j < localTags.length; j++) {
        PsiDocTag localTag = localTags[j];
        PsiDocTagValue value = localTag.getValueElement();

        if (value != null) {
          String tagName = value.getText();

          if (tagName != null && tagName.equals(paramName)) {
            parmTag =
            new Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>
              (localTag,
               new InheritDocProvider<PsiDocTag>() {
                 public Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> getInheritDoc() {
                   return findInheritDocTag(method, parameterLocator(paramName));
                 }

                 public PsiClass getElement() {
                   return method.getContainingClass();
                 }
               });
            break;
          }
        }
      }

      if (parmTag == null) {
        parmTag = findInheritDocTag(method, parameterLocator(paramName));
      }

      if (parmTag != null) {
        collectedTags.addLast(parmTag);
      }
    }

    if (collectedTags.size() > 0) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>Parameters:</b>");
      for (Iterator<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>> i = collectedTags.iterator(); i.hasNext();) {
        Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag = i.next();
        PsiElement[] elements = tag.first.getDataElements();
        if (elements.length == 0) continue;
        String text = elements[0].getText();
        buffer.append("<DD>");
        int spaceIndex = text.indexOf(' ');
        if (spaceIndex < 0) {
          spaceIndex = text.length();
        }
        String parmName = text.substring(0, spaceIndex);
        buffer.append("<code>");
        buffer.append(parmName);
        buffer.append("</code>");
        buffer.append(" - ");
        buffer.append(text.substring(spaceIndex));
        generateValue(buffer, elements, 1, mapProvider(tag.second, true));
      }
      buffer.append("</DD></DL></DD>");
    }
  }

  private void generateReturnsSection(StringBuffer buffer, PsiMethod method) {
    PsiDocComment comment = method.getDocComment();
    PsiDocTag tag = comment == null ? null : comment.findTagByName("return");
    Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> pair =
      tag == null ? null : new Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>(tag, ourEmptyProvider);

    if (pair == null && myElement instanceof PsiMethod) {
      pair = findInheritDocTag(((PsiMethod)myElement), new DocTagLocator<PsiDocTag>() {
        public PsiDocTag find(PsiDocComment comment) {
          if (comment == null) {
            return null;
          }

          return comment.findTagByName("return");
        }
      });
    }

    if (pair != null) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>Returns:</b>");
      buffer.append("<DD>");
      generateValue(buffer, pair.first.getDataElements(), mapProvider(pair.second, false));
      buffer.append("</DD></DL></DD>");
    }
  }

  private PsiDocTag[] getThrowsTags(PsiDocComment comment) {
    if (comment == null) {
      return PsiDocTag.EMPTY_ARRAY;
    }

    PsiDocTag[] tags1 = comment.findTagsByName("throws");
    PsiDocTag[] tags2 = comment.findTagsByName("exception");

    return ArrayUtil.mergeArrays(tags1, tags2, PsiDocTag.class);
  }

  private static boolean areWeakEqual(String one, String two) {
    return one.equals(two) || one.endsWith(two) || two.endsWith(one);
  }

  private void generateThrowsSection(StringBuffer buffer, PsiMethod method) {
    PsiDocComment comment = method.getDocComment();
    PsiDocTag[] localTags = getThrowsTags(comment);

    LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>> collectedTags =
      new LinkedList<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>>();

    LinkedList<PsiClassType> holder = new LinkedList<PsiClassType>(Arrays.asList(method.getThrowsList().getReferencedTypes()));

    for (int i = localTags.length - 1; i > -1; i--) {
      try {
        PsiDocTagValue valueElement = localTags[i].getValueElement();

        if (valueElement != null) {
          PsiClassType t = (PsiClassType)method.getManager().getElementFactory().createTypeFromText(valueElement.getText(), method);

          if (!holder.contains(t)) {
            holder.addFirst(t);
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error("Incorrect operation exception.");
      }
    }

    PsiClassType[] trousers = holder.toArray(new PsiClassType[holder.size()]);

    for (int i = 0; i < trousers.length; i++) {
      if (trousers[i] != null) {
        String paramName = trousers[i].getCanonicalText();
        Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> parmTag = null;

        for (int j = 0; j < localTags.length; j++) {
          PsiDocTag localTag = localTags[j];
          PsiDocTagValue value = localTag.getValueElement();

          if (value != null) {
            String tagName = value.getText();

            if (tagName != null && areWeakEqual(tagName, paramName)) {
              parmTag = new Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>(localTag, ourEmptyProvider);
              break;
            }
          }
        }

        if (parmTag == null) {
          parmTag = findInheritDocTag(method, exceptionLocator(paramName));
        }

        if (parmTag != null) {
          collectedTags.addLast(parmTag);
        }
        else {
          try {
            final PsiDocTag tag = method.getManager().getElementFactory().createDocCommentFromText("/** @exception " + paramName + " */",
                                                                                                   method.getContainingFile()).getTags()[0];

            collectedTags.addLast(new Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>(tag, ourEmptyProvider));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }

    if (collectedTags.size() > 0) {
      buffer.append("<DD><DL>");
      buffer.append("<DT><b>Throws:</b>");
      for (Iterator<Pair<PsiDocTag, InheritDocProvider<PsiDocTag>>> i = collectedTags.iterator(); i.hasNext();) {
        Pair<PsiDocTag, InheritDocProvider<PsiDocTag>> tag = i.next();
        PsiElement[] elements = tag.first.getDataElements();
        if (elements.length == 0) continue;
        buffer.append("<DD>");
        String text = elements[0].getText();
        int index = JavaDocUtil.extractReference(text);
        String refText = text.substring(0, index).trim();
        generateLink(buffer, refText, null, method, false);
        String rest = text.substring(index);
        if (rest.length() > 0 || elements.length > 1) buffer.append(" - ");
        buffer.append(rest);
        generateValue(buffer, elements, 1, mapProvider(tag.second, true));
      }
      buffer.append("</DD></DL></DD>");
    }
  }

  private void generateSuperMethodsSection(StringBuffer buffer, PsiMethod method, boolean overrides) {
    PsiClass parentClass = method.getContainingClass();
    if (parentClass == null) return;
    if (parentClass.isInterface() && !overrides) return;
    PsiMethod[] supers = PsiSuperMethodUtil.findSuperMethods(method);
    if (supers.length == 0) return;
    boolean headerGenerated = false;
    for (int i = 0; i < supers.length; i++) {
      PsiMethod superMethod = supers[i];
      boolean isAbstract = superMethod.hasModifierProperty(PsiModifier.ABSTRACT);
      if (overrides) {
        if (parentClass.isInterface() ? !isAbstract : isAbstract) continue;
      }
      else {
        if (!isAbstract) continue;
      }
      PsiClass superClass = superMethod.getContainingClass();
      if (!headerGenerated) {
        buffer.append("<DD><DL>");
        buffer.append("<DT><b>");
        buffer.append(overrides ? "Overrides:" : "Specified by:");
        buffer.append("</b>");
        headerGenerated = true;
      }
      buffer.append("<DD>");

      generateLink(buffer, superMethod, superMethod.getName());
      buffer.append(" in ");
      buffer.append(superClass.isInterface() ? "interface " : "class ");
      generateLink(buffer, superClass, superClass.getName());
    }
    if (headerGenerated) {
      buffer.append("</DD></DL></DD>");
    }
  }

  private void generateLink(StringBuffer buffer, PsiElement element, String label) {
    String refText = JavaDocUtil.getReferenceText(myProject, element);
    if (refText != null) {
      JavaDocUtil.createHyperlink(buffer, refText,label,false);
      //return generateLink(buffer, refText, label, context, false);
    }
  }

  /**
   * @return Length of the generated label.
   */
  private int generateLink(StringBuffer buffer, String refText, String label, PsiElement context, boolean plainLink) {
    if (label == null) {
      PsiManager manager = PsiManager.getInstance(myProject);
      label = JavaDocUtil.getLabelText(myProject, manager, refText, context);
    }

    LOG.assertTrue(refText != null, "refText appears to be null.");

    boolean isBrokenLink = JavaDocUtil.findReferenceTarget(context.getManager(), refText, context) == null;
    if (isBrokenLink) {
      buffer.append("<font color=red>");
      buffer.append(label);
      buffer.append("</font>");
      return label.length();
    }


    JavaDocUtil.createHyperlink(buffer, refText,label,plainLink);
    return label.length();
  }

  /**
   * @return Length of the generated label.
   */
  private int generateType(StringBuffer buffer, PsiType type, PsiElement context) {
    if (type instanceof PsiPrimitiveType) {
      String text = type.getCanonicalText();
      buffer.append(text);
      return text.length();
    }

    if (type instanceof PsiArrayType) {
      int rest = generateType(buffer, ((PsiArrayType)type).getComponentType(), context);
      buffer.append("[]");
      return rest + 2;
    }

    if (type instanceof PsiWildcardType){
      PsiWildcardType wt = ((PsiWildcardType)type);

      buffer.append("?");

      PsiType bound = wt.getBound();

      if (bound != null){
        final String keyword = wt.isExtends() ? " extends " : " super "; 
        buffer.append(keyword);
        return generateType(buffer, bound, context) + 1 + keyword.length();
      }

      return 1;
    }

    if (type instanceof PsiClassType) {
      PsiClassType.ClassResolveResult result = ((PsiClassType)type).resolveGenerics();
      PsiClass psiClass = result.getElement();
      PsiSubstitutor psiSubst = result.getSubstitutor();

      if (psiClass == null) {
        String text = "<font color=red>" + type.getCanonicalText() + "</font>";
        buffer.append(text);
        return text.length();
      }

      String qName = psiClass.getQualifiedName();

      if (qName == null || psiClass instanceof PsiTypeParameter) {
        String text = type.getCanonicalText();
        buffer.append(text);
        return text.length();
      }

      int length = generateLink(buffer, qName, null, context, false);

      if (psiClass.hasTypeParameters()) {
        StringBuffer subst = new StringBuffer();
        boolean goodSubst = true;

        PsiTypeParameter[] params = psiClass.getTypeParameterList().getTypeParameters();

        subst.append("&lt;");
        for (int i = 0; i < params.length; i++) {
          PsiType t = psiSubst.substitute(params[i]);

          if (t == null) {
            goodSubst = false;
            break;
          }

          generateType(subst, t, context);

          if (i < params.length - 1) {
            subst.append(", ");
          }
        }

        if (goodSubst) {
          subst.append("&gt;");
          String text = subst.toString();

          buffer.append(text);
          length += text.length();
        }
      }

      return length;
    }

    return 0;
  }

  private String generateTypeParameters(PsiClass aClass) {
    if (aClass.hasTypeParameters()) {
      PsiTypeParameterList list = aClass.getTypeParameterList();

      if (list == null) return "";

      PsiTypeParameter[] parms = list.getTypeParameters();

      StringBuffer buffer = new StringBuffer();

      buffer.append("&lt;");

      for (int i = 0; i < parms.length; i++) {
        PsiTypeParameter p = parms[i];

        buffer.append(p.getName());

        PsiClassType[] refs = JavaDocUtil.getExtendsList(p);

        if (refs.length > 0) {
          buffer.append(" extends ");

          for (int j = 0; j < refs.length; j++) {
            generateType(buffer, refs[j], aClass);

            if (j < refs.length - 1) {
              buffer.append(" & ");
            }
          }
        }

        if (i < parms.length - 1) {
          buffer.append(", ");
        }
      }

      buffer.append("&gt;");

      return buffer.toString();
    }

    return "";
  }

  private <T> Pair<T, InheritDocProvider<T>> searchDocTagInOverridenMethod(PsiMethod method,
                                                                           final PsiClass extendee,
                                                                           final DocTagLocator<T> loc) {
    if (extendee != null) {
      final PsiMethod overriden = extendee.findMethodBySignature(method, false);

      if (overriden != null) {
        T tag = loc.find(overriden.getDocComment());

        if (tag != null) {
          return new Pair<T, InheritDocProvider<T>>
            (tag,
             new InheritDocProvider<T>() {
               public Pair<T, InheritDocProvider<T>> getInheritDoc() {
                 return findInheritDocTag(overriden, loc);
               }

               public PsiClass getElement() {
                 return extendee;
               }
             });
        }
      }
    }

    return null;
  }

  private <T> Pair<T, InheritDocProvider<T>> searchDocTagInExtendees(PsiClassType[] exts,
                                                                     PsiMethod method,
                                                                     DocTagLocator<T> loc,
                                                                     boolean deep) {
    for (int i = 0; i < exts.length; i++) {
      PsiClass extendee = exts[i].resolve();

      if (extendee != null) {
        Pair<T, InheritDocProvider<T>> tag = searchDocTagInOverridenMethod(method, extendee, loc);

        if (tag != null) {
          return tag;
        }

        if (deep) {
          tag = findInheritDocTagInClass(method, extendee, loc);

          if (tag != null) {
            return tag;
          }
        }
      }
    }

    return null;
  }

  private <T> Pair<T, InheritDocProvider<T>> findInheritDocTagInClass(PsiMethod aMethod,
                                                                      PsiClass aClass,
                                                                      DocTagLocator<T> loc) {
    if (aClass == null) {
      return null;
    }

    PsiClassType[] implementee = JavaDocUtil.getImplementsList(aClass);
    Pair<T, InheritDocProvider<T>> tag = searchDocTagInExtendees(implementee, aMethod, loc, false);

    if (tag != null) {
      return tag;
    }

    tag = searchDocTagInExtendees(implementee, aMethod, loc, true);

    if (tag != null) {
      return tag;
    }

    PsiClassType[] extendee = JavaDocUtil.getExtendsList(aClass);
    tag = searchDocTagInExtendees(extendee, aMethod, loc, false);

    if (tag != null) {
      return tag;
    }

    return searchDocTagInExtendees(extendee, aMethod, loc, true);
  }

  private <T> Pair<T, InheritDocProvider<T>> findInheritDocTag(PsiMethod method, DocTagLocator<T> loc) {
    PsiClass aClass = method.getContainingClass();

    if (aClass == null) {
      return null;
    }

    return findInheritDocTagInClass(method, aClass, loc);
  }
}
