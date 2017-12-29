/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.template.emmet.nodes;

import com.google.common.base.Strings;
import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.LiveTemplateBuilder;
import com.intellij.codeInsight.template.emmet.XmlEmmetParser;
import com.intellij.codeInsight.template.emmet.ZenCodingUtil;
import com.intellij.codeInsight.template.emmet.filters.SingleLineEmmetFilter;
import com.intellij.codeInsight.template.emmet.filters.ZenCodingFilter;
import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGeneratorImpl;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

import static com.google.common.collect.Lists.newArrayList;

public class GenerationNode extends UserDataHolderBase {
  private final TemplateToken myTemplateToken;
  private final List<GenerationNode> myChildren = newArrayList();
  private final int myNumberInIteration;
  private final int myTotalIterations;
  private String mySurroundedText;
  private final boolean myInsertSurroundedTextAtTheEnd;

  private final boolean myInsertNewLineBetweenNodes;

  private GenerationNode myParent;
  private boolean myContainsSurroundedTextMarker = false;

  private static final Pattern ATTRIBUTE_VARIABLE_PATTERN = Pattern.compile("\\$[A-z_0-9]+\\$");
  private static final Pattern HREF_PATTERN = Pattern.compile("^(?:(?:https?|ftp|file)://|www\\.|ftp\\.)(?:\\([-A-Z0-9+&@#/%=~_|$?!:,.]*\\)|[-A-Z0-9+&@#/%=~_|$?!:,.])*(?:\\([-A-Z0-9+&@#/%=~_|$?!:,.]*\\)|[A-Z0-9+&@#/%=~_|$])",
                                                              Pattern.CASE_INSENSITIVE);
  private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-z0-9._%+-]+@[A-z0-9.-]+\\.[A-z]{2,5}$");
  private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^([A-z]+:)?//");

  public GenerationNode(TemplateToken templateToken,
                        int numberInIteration,
                        int totalIterations, String surroundedText,
                        boolean insertSurroundedTextAtTheEnd, GenerationNode parent) {
    this(templateToken, numberInIteration, totalIterations, surroundedText, insertSurroundedTextAtTheEnd, parent, false);
  }


  public GenerationNode(TemplateToken templateToken,
                        int numberInIteration,
                        int totalIterations, String surroundedText,
                        boolean insertSurroundedTextAtTheEnd, GenerationNode parent, boolean insertNewLineBetweenNodes) {
    myTemplateToken = templateToken;
    myNumberInIteration = numberInIteration;
    myTotalIterations = totalIterations;
    mySurroundedText = surroundedText;
    myInsertSurroundedTextAtTheEnd = insertSurroundedTextAtTheEnd;
    myInsertNewLineBetweenNodes = insertNewLineBetweenNodes;
    if(parent != null) {
      parent.addChild(this);
    }
  }

  public boolean isInsertNewLineBetweenNodes() {
    return myInsertNewLineBetweenNodes;
  }

  public List<GenerationNode> getChildren() {
    return myChildren;
  }

  public void addChild(GenerationNode child) {
    child.setParent(this);
    myChildren.add(child);
  }

  public void addChildren(Collection<GenerationNode> children) {
    for (GenerationNode child : children) {
      addChild(child);
    }
  }

  public boolean isLeaf() {
    return myChildren.size() == 0;
  }

  private boolean isBlockTag() {
    if (myTemplateToken != null) {
      XmlTag tag = myTemplateToken.getXmlTag();
      if (tag != null) {
        return HtmlUtil.isHtmlBlockTagL(tag.getName());
      }
    }
    return false;
  }

  @NotNull
  public TemplateImpl generate(@NotNull CustomTemplateCallback callback,
                               @Nullable ZenCodingGenerator generator,
                               @NotNull Collection<ZenCodingFilter> filters,
                               boolean insertSurroundedText, int segmentsLimit) {
    myContainsSurroundedTextMarker = !(insertSurroundedText && myInsertSurroundedTextAtTheEnd);

    GenerationNode generationNode = this;
    if (generationNode != this) {
      return generationNode.generate(callback, generator, Collections.emptyList(), insertSurroundedText, segmentsLimit);
    }

    boolean shouldNotReformatTemplate = false;
    boolean oneLineTemplateExpanding = false;
    for (ZenCodingFilter filter : filters) {
      generationNode = filter.filterNode(generationNode);
      if (filter instanceof SingleLineEmmetFilter) {
        shouldNotReformatTemplate = true;
        oneLineTemplateExpanding = true;
      }
    }

    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(callback.getProject());
    String indentStr;
    if (callback.isInInjectedFragment()) {
      Editor editor = callback.getEditor();
      Document document = editor.getDocument();
      if (document instanceof DocumentWindow && ((DocumentWindow)document).isOneLine()) {
        /* 
         * If document is one-line that in the moment of inserting text,
         * new line chars will be filtered (see DocumentWindowImpl#insertString).
         * So in this case we should filter text by SingleLineAvoid in order to avoid
         * inconsistency of template segments.
         */
        oneLineTemplateExpanding = true;
        filters.add(new SingleLineEmmetFilter());
      }
      indentStr = "";
    }
    else if (settings.useTabCharacter(callback.getFileType())) {
      indentStr = "\t";
    }
    else {
      int tabSize = settings.getTabSize(callback.getFileType());
      indentStr = StringUtil.repeatSymbol(' ', tabSize);
    }

    LiveTemplateBuilder builder = new LiveTemplateBuilder(EmmetOptions.getInstance().isAddEditPointAtTheEndOfTemplate(), segmentsLimit);
    int end = -1;
    boolean hasChildren = myChildren.size() > 0;

    TemplateImpl parentTemplate;
    Map<String, String> predefinedValues;
    if (generator instanceof XmlZenCodingGenerator) {
      TemplateToken xmlTemplateToken = myTemplateToken;
      parentTemplate = invokeXmlTemplate(xmlTemplateToken, callback, generator, hasChildren);
      predefinedValues = buildPredefinedValues(xmlTemplateToken.getAttributes(), (XmlZenCodingGenerator)generator, hasChildren);
    }
    else {
      parentTemplate = invokeTemplate(myTemplateToken, hasChildren, callback, generator);
      predefinedValues = null;
    }

    String s = parentTemplate.getString();
    for (ZenCodingFilter filter : filters) {
      s = filter.filterText(s, myTemplateToken);
    }
    parentTemplate = parentTemplate.copy();
    parentTemplate.setString(s);

    final String txt = hasChildren || myContainsSurroundedTextMarker ? null : mySurroundedText;
    parentTemplate = expandTemplate(parentTemplate, predefinedValues, txt, segmentsLimit);

    int offset = builder.insertTemplate(0, parentTemplate, null);
    int newOffset = gotoChild(callback.getProject(), builder.getText(), offset, 0, builder.length());
    if (offset < builder.length() && newOffset != offset) {
      end = offset;
    }
    offset = newOffset;
    if (end == -1 && offset < builder.length() && myChildren.size() == 0) {
      end = offset;
    }
    LiveTemplateBuilder.Marker marker = offset < builder.length() ? builder.createMarker(offset) : null;

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, myChildrenSize = myChildren.size(); i < myChildrenSize; i++) {
      GenerationNode child = myChildren.get(i);
      TemplateImpl childTemplate = child.generate(callback, generator, filters, !myContainsSurroundedTextMarker, segmentsLimit);

      boolean blockTag = child.isBlockTag();

      if (!oneLineTemplateExpanding && blockTag && !isNewLineBefore(builder.getText(), offset)) {
        builder.insertText(offset, "\n" + indentStr, false);
        offset += indentStr.length() + 1;
      }

      int e = builder.insertTemplate(offset, childTemplate, null);
      offset = marker != null ? marker.getEndOffset() : builder.length();

      if (!oneLineTemplateExpanding && ((blockTag && !isNewLineAfter(builder.getText(), offset)) || myInsertNewLineBetweenNodes)) {
        builder.insertText(offset, "\n" + indentStr, false);
        offset += indentStr.length() + 1;
      }

      if (end == -1 && e < offset) {
        end = e;
      }
    }
    if (shouldNotReformatTemplate) {
      builder.setIsToReformat(false);
    }
    return builder.buildTemplate();
  }

  private static TemplateImpl invokeTemplate(@NotNull TemplateToken token,
                                             boolean hasChildren,
                                             final CustomTemplateCallback callback,
                                             @Nullable ZenCodingGenerator generator) {
    TemplateImpl template = token.getTemplate();
    if (generator != null) {
      assert template != null;
      template = generator.generateTemplate(token, hasChildren, callback.getContext());
      removeVariablesWhichHasNoSegment(template);
    }

    return template;
  }

  private TemplateImpl invokeXmlTemplate(final TemplateToken token,
                                         CustomTemplateCallback callback,
                                         @Nullable ZenCodingGenerator generator,
                                         final boolean hasChildren) {
    ZenCodingGenerator zenCodingGenerator = ObjectUtils.notNull(generator, XmlZenCodingGeneratorImpl.INSTANCE);
    
    Map<String, String> attributes = token.getAttributes();
    TemplateImpl template = token.getTemplate();
    assert template != null;
    
    PsiFileFactory fileFactory = PsiFileFactory.getInstance(callback.getProject());
    PsiFile dummyFile = fileFactory.createFileFromText("dummy.html", callback.getFile().getLanguage(), token.getTemplateText(), false, true);
    XmlTag tag = PsiTreeUtil.findChildOfType(dummyFile, XmlTag.class);
    if (tag != null) {
      // autodetect href
      if (EmmetOptions.getInstance().isHrefAutoDetectEnabled() && StringUtil.isNotEmpty(mySurroundedText)) {
        final boolean isEmptyLinkTag = "a".equalsIgnoreCase(tag.getName()) && isEmptyValue(tag.getAttributeValue("href"));
        if (!hasChildren && isEmptyLinkTag) {
          if (HREF_PATTERN.matcher(mySurroundedText).matches()) {
            attributes.put("href", PROTOCOL_PATTERN.matcher(mySurroundedText).find()
                                   ? mySurroundedText.trim()
                                   : "http://" + mySurroundedText.trim());
          }
          else if (EMAIL_PATTERN.matcher(mySurroundedText).matches()) {
            attributes.put("href", "mailto:" + mySurroundedText.trim());
          }
        }
      }

      for (Map.Entry<String, String> attribute : attributes.entrySet()) {
        if (Strings.isNullOrEmpty(attribute.getValue())) {
          template.addVariable(prepareVariableName(attribute.getKey()), "", "", true);
        }
      }
      XmlTag tag1 = hasChildren ? expandEmptyTagIfNecessary(tag) : tag;
      setAttributeValues(tag1, attributes, callback, zenCodingGenerator.isHtml(callback));
      token.setTemplateText(tag1.getContainingFile().getText(), callback);
    }
    template = zenCodingGenerator.generateTemplate(token, hasChildren, callback.getContext());
    removeVariablesWhichHasNoSegment(template);
    return template;
  }

  private static String prepareVariableName(@NotNull String attributeName) {
    char[] toReplace = {'$', '-', '+', ':'};
    StringBuilder builder = new StringBuilder(attributeName.length());
    for (int i = 0; i < attributeName.length(); i++) {
      char c = attributeName.charAt(i);
      boolean replaced = false;
      for (char aToReplace : toReplace) {
        if (c == aToReplace) {
          builder.append('_');
          replaced = true;
          break;
        }
      }
      if (!replaced) {
        builder.append(c);
      }
    }
    return builder.toString();
  }

  @NotNull
  private static TemplateImpl expandTemplate(@NotNull TemplateImpl template,
                                             Map<String, String> predefinedVarValues,
                                             String surroundedText,
                                             int segmentsLimit) {
    LiveTemplateBuilder builder = new LiveTemplateBuilder(EmmetOptions.getInstance().isAddEditPointAtTheEndOfTemplate(), segmentsLimit);
    if (predefinedVarValues == null && surroundedText == null) {
      return template;
    }
    int offset = builder.insertTemplate(0, template, predefinedVarValues);
    if (surroundedText != null) {
      builder.insertText(offset, surroundedText, true);
      builder.setIsToReformat(true);
    }
    return builder.buildTemplate();
  }

  @NotNull
  private static XmlTag expandEmptyTagIfNecessary(@NotNull XmlTag tag) {
    StringBuilder builder = new StringBuilder();
    boolean flag = false;

    for (PsiElement child : tag.getChildren()) {
      if (child instanceof XmlToken && XmlTokenType.XML_EMPTY_ELEMENT_END.equals(((XmlToken)child).getTokenType())) {
        flag = true;
        break;
      }
      builder.append(child.getText());
    }

    if (flag) {
      builder.append("></").append(tag.getName()).append('>');
      return XmlElementFactory.getInstance(tag.getProject()).createTagFromText(builder.toString(), XMLLanguage.INSTANCE);
    }
    return tag;
  }

  private static int gotoChild(Project project, CharSequence text, int offset, int start, int end) {
    PsiFile file = PsiFileFactory.getInstance(project)
      .createFileFromText("dummy.xml", StdFileTypes.XML, text, LocalTimeCounter.currentTime(), false);

    PsiElement element = file.findElementAt(offset);
    if (offset < end && element instanceof XmlToken && ((XmlToken)element).getTokenType() == XmlTokenType.XML_END_TAG_START) {
      return offset;
    }

    int newOffset = -1;
    XmlTag tag = PsiTreeUtil.findElementOfClassAtRange(file, start, end, XmlTag.class);
    if (tag != null) {
      for (PsiElement child : tag.getChildren()) {
        if (child instanceof XmlToken && ((XmlToken)child).getTokenType() == XmlTokenType.XML_END_TAG_START) {
          newOffset = child.getTextOffset();
        }
      }
    }

    if (newOffset >= 0) {
      return newOffset;
    }

    return offset;
  }

  private static void removeVariablesWhichHasNoSegment(TemplateImpl template) {
    Set<String> segments = new HashSet<>();
    for (int i = 0; i < template.getSegmentsCount(); i++) {
      segments.add(template.getSegmentName(i));
    }
    for (int i = template.getVariableCount() - 1; i >= 0; i--) {
      String varName = template.getVariableNameAt(i);
      if (!segments.contains(varName)) {
        template.removeVariable(i);
      }
      else {
        segments.remove(varName);
      }
    }
  }

  @Nullable
  private Map<String, String> buildPredefinedValues(@NotNull Map<String, String> attributes,
                                                    @Nullable XmlZenCodingGenerator generator,
                                                    boolean hasChildren) {
    if (generator == null) {
      return Collections.emptyMap();
    }

    for (String value : attributes.values()) {
      if (ZenCodingUtil.containsSurroundedTextMarker(value)) {
        myContainsSurroundedTextMarker = true;
        break;
      }
    }

    String attributesString = generator.buildAttributesString(attributes, hasChildren, myNumberInIteration, myTotalIterations, mySurroundedText);
    attributesString = attributesString.length() > 0 ? ' ' + attributesString : null;
    Map<String, String> predefinedValues = null;
    if (attributesString != null) {
      predefinedValues = new HashMap<>();
      predefinedValues.put(TemplateToken.ATTRS, attributesString);
    }
    return predefinedValues;
  }

  private void setAttributeValues(@NotNull XmlTag tag,
                                  @NotNull final Map<String, String> attributes,
                                  @NotNull CustomTemplateCallback callback, 
                                  boolean isHtml) {
    // default and implied attributes
    final String defaultAttributeValue = attributes.get(XmlEmmetParser.DEFAULT_ATTRIBUTE_NAME);
    if (defaultAttributeValue != null) {
      attributes.remove(XmlEmmetParser.DEFAULT_ATTRIBUTE_NAME);

      // exclude user defined attributes
      final List<XmlAttribute> xmlAttributes = ContainerUtil.filter(tag.getAttributes(),
                                                                    attribute -> !attributes.containsKey(attribute.getLocalName()));
      XmlAttribute defaultAttribute = findImpliedAttribute(xmlAttributes);
      if (defaultAttribute == null) {
        defaultAttribute = findEmptyAttribute(xmlAttributes);
      }
      if (defaultAttribute != null) {
        String attributeName = defaultAttribute.getName();
        if (attributeName.length() > 1) {
          if (isImpliedAttribute(attributeName)) {
            defaultAttribute = (XmlAttribute)defaultAttribute.setName(attributeName.substring(1));
          }
          final String oldValue = defaultAttribute.getValue();
          if (oldValue != null && StringUtil.containsChar(oldValue, '|')) {
            defaultAttribute.setValue(StringUtil.replace(oldValue, "|", defaultAttributeValue));
          }
          else {
            defaultAttribute.setValue(defaultAttributeValue);
          }
        }
      }
    }

    // boolean attributes
    for (XmlAttribute xmlAttribute : tag.getAttributes()) {
      final String attributeName = xmlAttribute.getName();
      final XmlAttributeValue xmlAttributeValueElement = xmlAttribute.getValueElement();
      if ((xmlAttributeValueElement != null && !attributes.containsKey(attributeName)) || !ZenCodingUtil.isXML11ValidQName(attributeName)) {
        continue;
      }

      String attributeValue = StringUtil.notNullize(attributes.get(attributeName), StringUtil.notNullize(xmlAttribute.getValue()));
      if (ZenCodingUtil.containsSurroundedTextMarker(attributeValue)) {
        myContainsSurroundedTextMarker = true;
      }

      if (isHtml && isBooleanAttribute(attributeValue, xmlAttribute, callback)) {
        if (HtmlUtil.isShortNotationOfBooleanAttributePreferred()) {
          if (xmlAttributeValueElement != null) {
            final PsiElement prevSibling = xmlAttributeValueElement.getPrevSibling();
            if (prevSibling != null && prevSibling.textMatches("=")) {
              xmlAttribute.deleteChildRange(prevSibling, xmlAttributeValueElement);
            }
          }
        }
        else {
          if (xmlAttributeValueElement == null) {
            xmlAttribute.delete();
          }
          tag.setAttribute(attributeName, attributeName);
        }
      }
      else {
        if (xmlAttributeValueElement == null) {
          xmlAttribute.delete();
        }
        tag.setAttribute(attributeName, StringUtil.isEmpty(attributeValue)
                                        ? "$" + prepareVariableName(attributeName) + "$"
                                        : ZenCodingUtil.getValue(attributeValue, myNumberInIteration, myTotalIterations, mySurroundedText));
      }
    }

    // remove all implicit and default attributes
    for (XmlAttribute xmlAttribute : tag.getAttributes()) {
      final String xmlAttributeLocalName = xmlAttribute.getLocalName();
      if (xmlAttribute.getValue() != null && isImpliedAttribute(xmlAttributeLocalName)) {
        xmlAttribute.delete();
      }
    }
  }

  private static boolean isBooleanAttribute(@Nullable String attributeValue,
                                            @NotNull XmlAttribute xmlAttribute,
                                            @NotNull CustomTemplateCallback callback) {
    if (XmlEmmetParser.BOOLEAN_ATTRIBUTE_VALUE.equals(attributeValue)) {
      return true;
    }
    if (StringUtil.isEmpty(attributeValue)) {
      final XmlAttributeDescriptor descriptor = xmlAttribute.getDescriptor();
      return descriptor != null && HtmlUtil.isBooleanAttribute(descriptor, callback.getContext());
    }
    return false;
  }

  private static boolean isImpliedAttribute(String xmlAttributeLocalName) {
    return StringUtil.startsWithChar(xmlAttributeLocalName, '!');
  }

  private static boolean isEmptyValue(String attributeValue) {
    return attributeValue != null && (attributeValue.isEmpty() || ATTRIBUTE_VARIABLE_PATTERN.matcher(attributeValue).matches());
  }

  @Nullable
  private static XmlAttribute findImpliedAttribute(@NotNull List<XmlAttribute> attributes) {
    for (XmlAttribute attribute : attributes) {
      if (attribute.getValueElement() != null && isImpliedAttribute(attribute.getLocalName())) {
        return attribute;
      }
    }
    return null;
  }

  @Nullable
  private static XmlAttribute findEmptyAttribute(@NotNull List<XmlAttribute> attributes) {
    for (XmlAttribute attribute : attributes) {
      final String attributeValue = attribute.getValue();
      if (isEmptyValue(attributeValue)) {
        return attribute;
      }
    }
    return null;
  }

  private static boolean isNewLineBefore(CharSequence text, int offset) {
    int i = offset - 1;
    while (i >= 0 && Character.isWhitespace(text.charAt(i))) {
      if (text.charAt(i) == '\n') {
        return true;
      }
      i--;
    }
    return i < 0;
  }

  private static boolean isNewLineAfter(CharSequence text, int offset) {
    int i = offset;
    while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
      if (text.charAt(i) == '\n') {
        return true;
      }
      i++;
    }
    return i == text.length();
  }

  public TemplateToken getTemplateToken() {
    return myTemplateToken;
  }

  public String getSurroundedText() {
    return mySurroundedText;
  }

  public void setSurroundedText(String surroundedText) {
    mySurroundedText = surroundedText;
  }

  public GenerationNode getParent() {
    return myParent;
  }

  public void setParent(GenerationNode parent) {
    myParent = parent;
  }
}
