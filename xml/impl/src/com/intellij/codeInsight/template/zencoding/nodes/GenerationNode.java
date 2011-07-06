/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.zencoding.nodes;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.LiveTemplateBuilder;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.zencoding.ZenCodingTemplate;
import com.intellij.codeInsight.template.zencoding.ZenCodingUtil;
import com.intellij.codeInsight.template.zencoding.generators.XmlZenCodingGenerator;
import com.intellij.codeInsight.template.zencoding.generators.XmlZenCodingGeneratorImpl;
import com.intellij.codeInsight.template.zencoding.filters.ZenCodingFilter;
import com.intellij.codeInsight.template.zencoding.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.zencoding.tokens.TemplateToken;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.IntArrayList;
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class GenerationNode {
  private final TemplateToken myTemplateToken;
  private final List<GenerationNode> myChildren;
  private final int myNumberInIteration;
  private String mySurroundedText;
  private final boolean myInsertSurroundedTextAtTheEnd;

  private boolean myContainsSurroundedTextMarker = false;

  public GenerationNode(TemplateToken templateToken,
                        List<GenerationNode> children,
                        int numberInIteration,
                        String surroundedText,
                        boolean insertSurroundedTextAtTheEnd) {
    myTemplateToken = templateToken;
    myChildren = children;
    myNumberInIteration = numberInIteration;
    mySurroundedText = surroundedText;
    myInsertSurroundedTextAtTheEnd = insertSurroundedTextAtTheEnd;
  }

  public List<GenerationNode> getChildren() {
    return myChildren;
  }

  public void addChildren(Collection<GenerationNode> child) {
    myChildren.addAll(child);
  }

  public boolean isLeaf() {
    return myChildren.size() == 0;
  }

  private boolean isBlockTag() {
    if (myTemplateToken != null) {
      XmlFile xmlFile = myTemplateToken.getFile();
      XmlDocument document = xmlFile.getDocument();
      if (document != null) {
        XmlTag tag = document.getRootTag();
        if (tag != null) {
          return HtmlUtil.isHtmlBlockTagL(tag.getName());
        }
      }
    }
    return false;
  }

  @NotNull
  public TemplateImpl generate(@NotNull CustomTemplateCallback callback,
                                @Nullable ZenCodingGenerator generator,
                                @NotNull Collection<ZenCodingFilter> filters,
                                boolean insertSurroundedText) {
    myContainsSurroundedTextMarker = !(insertSurroundedText && myInsertSurroundedTextAtTheEnd);

    GenerationNode generationNode = this;
    for (ZenCodingFilter filter : filters) {
      generationNode = filter.filterNode(generationNode);
    }

    if (generationNode != this) {
      return generationNode.generate(callback, generator, Collections.<ZenCodingFilter>emptyList(), insertSurroundedText);
    }

    LiveTemplateBuilder builder = new LiveTemplateBuilder();
    int end = -1;

    boolean hasChildren = myChildren.size() > 0;

    TemplateImpl parentTemplate;
    Map<String, String> predefinedValues;
    if (myTemplateToken instanceof TemplateToken && generator instanceof XmlZenCodingGenerator) {
      TemplateToken xmlTemplateToken = myTemplateToken;
      List<Pair<String, String>> attr2value = new ArrayList<Pair<String, String>>(xmlTemplateToken.getAttribute2Value());
      parentTemplate = invokeXmlTemplate(xmlTemplateToken, callback, generator, hasChildren, attr2value);
      predefinedValues =
        buildPredefinedValues(attr2value, (XmlZenCodingGenerator)generator, hasChildren);
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
    parentTemplate = expandTemplate(parentTemplate, predefinedValues, txt);

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

    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(callback.getProject());
    String tabStr;
    if (callback.isInInjectedFragment()) {
      tabStr = "";
    }
    else if (settings.useTabCharacter(callback.getFileType())) {
      tabStr = "\t";
    }
    else {
      StringBuilder tab = new StringBuilder();
      int tabSize = settings.getTabSize(callback.getFileType());
      while (tabSize-- > 0) {
        tab.append(' ');
      }
      tabStr = tab.toString();
    }

    for (int i = 0, myChildrenSize = myChildren.size(); i < myChildrenSize; i++) {
      GenerationNode child = myChildren.get(i);
      TemplateImpl childTemplate = child.generate(callback, generator, filters, !myContainsSurroundedTextMarker);

      boolean blockTag = child.isBlockTag();

      if (blockTag && !isNewLineBefore(builder.getText(), offset)) {
        builder.insertText(offset, "\n" + tabStr, false);
        offset += tabStr.length() + 1;
      }

      int e = builder.insertTemplate(offset, childTemplate, null);
      offset = marker != null ? marker.getEndOffset() : builder.length();

      if (blockTag && !isNewLineAfter(builder.getText(), offset)) {
        builder.insertText(offset, "\n" + tabStr, false);
        offset += tabStr.length() + 1;
      }

      if (end == -1 && e < offset) {
        end = e;
      }
    }
    /*if (end != -1) {
      builder.insertVariableSegment(end, TemplateImpl.END);
    }*/
    return builder.buildTemplate();
  }

  private static TemplateImpl invokeTemplate(TemplateToken token,
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
                                                final boolean hasChildren,
                                                final List<Pair<String, String>> attr2value) {
    /*assert generator == null || generator instanceof XmlZenCodingGenerator :
      "The generator cannot process TemplateToken because it doesn't inherit XmlZenCodingGenerator";*/

    TemplateImpl template = token.getTemplate();
    assert template != null;

    final XmlFile xmlFile = token.getFile();
    XmlDocument document = xmlFile.getDocument();
    if (document != null) {
      final XmlTag tag = document.getRootTag();
      if (tag != null) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            XmlTag tag1 = hasChildren ? expandEmptyTagIfNeccessary(tag) : tag;
            setAttributeValues(tag1, attr2value);
            token.setFile((XmlFile)tag1.getContainingFile());
          }
        });
      }
    }
    template =
      (generator != null ? generator : XmlZenCodingGeneratorImpl.INSTANCE).generateTemplate(token, hasChildren, callback.getContext());
    removeVariablesWhichHasNoSegment(template);
    return template;
  }

  @NotNull
  private static TemplateImpl expandTemplate(@NotNull TemplateImpl template,
                                             Map<String, String> predefinedVarValues,
                                             String surroundedText) {
    LiveTemplateBuilder builder = new LiveTemplateBuilder();
    if (predefinedVarValues == null && surroundedText == null) {
      return template;
    }
    int offset = builder.insertTemplate(0, template, predefinedVarValues);
    if (surroundedText != null) {
      builder.insertText(offset, surroundedText, true);
      /*if (offset < builder.length()) {
        builder.insertVariableSegment(offset, TemplateImpl.END);
      }*/
    }
    return builder.buildTemplate();
  }

  @NotNull
  private static XmlTag expandEmptyTagIfNeccessary(@NotNull XmlTag tag) {
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
      final XmlTag tag1 = XmlElementFactory.getInstance(tag.getProject()).createTagFromText(builder.toString(), XMLLanguage.INSTANCE);
      return tag1;
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
    Set<String> segments = new HashSet<String>();
    for (int i = 0; i < template.getSegmentsCount(); i++) {
      segments.add(template.getSegmentName(i));
    }
    IntArrayList varsToRemove = new IntArrayList();
    for (int i = 0; i < template.getVariableCount(); i++) {
      String varName = template.getVariableNameAt(i);
      if (!segments.contains(varName)) {
        varsToRemove.add(i);
      }
    }
    for (int i = 0; i < varsToRemove.size(); i++) {
      template.removeVariable(varsToRemove.get(i));
    }
  }

  @Nullable
  private Map<String, String> buildPredefinedValues(List<Pair<String, String>> attribute2value,
                                                    @Nullable XmlZenCodingGenerator generator,
                                                    boolean hasChildren) {
    if (generator == null) {
      return Collections.emptyMap();
    }

    for (Pair<String, String> pair : attribute2value) {
      if (ZenCodingUtil.containsSurroundedTextMarker(pair.second)) {
        myContainsSurroundedTextMarker = true;
        break;
      }
    }

    String attributes = generator.buildAttributesString(attribute2value, hasChildren, myNumberInIteration, mySurroundedText);
    attributes = attributes.length() > 0 ? ' ' + attributes : null;
    Map<String, String> predefinedValues = null;
    if (attributes != null) {
      predefinedValues = new HashMap<String, String>();
      predefinedValues.put(ZenCodingTemplate.ATTRS, attributes);
    }
    return predefinedValues;
  }

  private void setAttributeValues(XmlTag tag, List<Pair<String, String>> attr2value) {
    for (Iterator<Pair<String, String>> iterator = attr2value.iterator(); iterator.hasNext();) {
      Pair<String, String> pair = iterator.next();
      if (tag.getAttribute(pair.first) != null) {
        if (ZenCodingUtil.containsSurroundedTextMarker(pair.second)) {
          myContainsSurroundedTextMarker = true;
        }
        tag.setAttribute(pair.first, ZenCodingUtil.getValue(pair.second, myNumberInIteration, mySurroundedText));
        iterator.remove();
      }
    }
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
}
