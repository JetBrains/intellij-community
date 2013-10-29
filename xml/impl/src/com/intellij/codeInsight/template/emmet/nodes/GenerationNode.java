/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.LiveTemplateBuilder;
import com.intellij.codeInsight.template.emmet.ZenCodingUtil;
import com.intellij.codeInsight.template.emmet.filters.SingleLineEmmetFilter;
import com.intellij.codeInsight.template.emmet.filters.ZenCodingFilter;
import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGeneratorImpl;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.injected.editor.DocumentWindowImpl;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.command.undo.UndoConstants;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
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
import com.intellij.xml.util.HtmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.google.common.collect.Lists.newArrayList;

/**
 * @author Eugene.Kudelevsky
 */
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
    if (generationNode != this) {
      return generationNode.generate(callback, generator, Collections.<ZenCodingFilter>emptyList(), insertSurroundedText);
    }
    
    boolean shouldNotReformatTemplate = false;
    boolean oneLineTemplateExpanding = false;
    for (ZenCodingFilter filter : filters) {
      generationNode = filter.filterNode(generationNode);
      if(filter instanceof SingleLineEmmetFilter) {
        shouldNotReformatTemplate = true;
        oneLineTemplateExpanding = true;
      }
    }

    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(callback.getProject());
    String indentStr;
    if (callback.isInInjectedFragment()) {
      Editor editor = callback.getEditor();
      Document document = editor.getDocument();
      if(document instanceof DocumentWindowImpl && ((DocumentWindowImpl)document).isOneLine()) {
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

    LiveTemplateBuilder builder = new LiveTemplateBuilder();
    int end = -1;
    boolean hasChildren = myChildren.size() > 0;

    TemplateImpl parentTemplate;
    Map<String, String> predefinedValues;
    if (myTemplateToken instanceof TemplateToken && generator instanceof XmlZenCodingGenerator) {
      TemplateToken xmlTemplateToken = myTemplateToken;
      List<Pair<String, String>> attr2value = new ArrayList<Pair<String, String>>(xmlTemplateToken.getAttribute2Value());
      parentTemplate = invokeXmlTemplate(xmlTemplateToken, callback, generator, hasChildren, attr2value);
      predefinedValues = buildPredefinedValues(attr2value, (XmlZenCodingGenerator)generator, hasChildren);
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

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0, myChildrenSize = myChildren.size(); i < myChildrenSize; i++) {
      GenerationNode child = myChildren.get(i);
      TemplateImpl childTemplate = child.generate(callback, generator, filters, !myContainsSurroundedTextMarker);

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
    if(shouldNotReformatTemplate) {
      builder.setIsToReformat(false);
    }
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
    PsiFileFactory fileFactory = PsiFileFactory.getInstance(xmlFile.getProject());
    XmlFile dummyFile = (XmlFile)fileFactory.createFileFromText("dummy.xml", StdFileTypes.XML, xmlFile.getText());
    final XmlTag tag = dummyFile.getRootTag();
    if (tag != null) {
      for (Pair<String, String> pair : attr2value) {
        if (Strings.isNullOrEmpty(pair.second)) {
          template.addVariable(prepareVariableName(pair.first), "", "", true);
        }
      }
      XmlTag tag1 = hasChildren ? expandEmptyTagIfNecessary(tag) : tag;
      setAttributeValues(tag1, attr2value);
      XmlFile physicalFile = (XmlFile)fileFactory.createFileFromText("dummy.xml", StdFileTypes.XML, tag1.getContainingFile().getText(),
                                                                     LocalTimeCounter.currentTime(), true);
      VirtualFile vFile = physicalFile.getVirtualFile();
      if (vFile != null) {
        vFile.putUserData(UndoConstants.DONT_RECORD_UNDO, Boolean.TRUE);
      }
      token.setFile(physicalFile);
    }
    ZenCodingGenerator zenCodingGenerator = generator != null ? generator : XmlZenCodingGeneratorImpl.INSTANCE;
    template = zenCodingGenerator.generateTemplate(token, hasChildren, callback.getContext());
    removeVariablesWhichHasNoSegment(template);
    return template;
  }

  private static String prepareVariableName(@NotNull String attributeName) {
    return StringUtil.replaceChar(attributeName, '-', '_');
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
    Set<String> segments = new HashSet<String>();
    for (int i = 0; i < template.getSegmentsCount(); i++) {
      segments.add(template.getSegmentName(i));
    }
    for (int i = template.getVariableCount() - 1; i >= 0; i--) {
      String varName = template.getVariableNameAt(i);
      if (!segments.contains(varName)) {
        template.removeVariable(i);
      } else {
        segments.remove(varName);
      }
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

    String attributes = generator.buildAttributesString(attribute2value, hasChildren, myNumberInIteration, myTotalIterations, mySurroundedText);
    attributes = attributes.length() > 0 ? ' ' + attributes : null;
    Map<String, String> predefinedValues = null;
    if (attributes != null) {
      predefinedValues = new HashMap<String, String>();
      predefinedValues.put(TemplateToken.ATTRS, attributes);
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
        tag.setAttribute(pair.first,
                         Strings.isNullOrEmpty(pair.second)
                         ? "$" + prepareVariableName(pair.first) + "$"
                         : ZenCodingUtil.getValue(pair.second, myNumberInIteration, myTotalIterations, mySurroundedText));
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

  public GenerationNode getParent() {
    return myParent;
  }

  public void setParent(GenerationNode parent) {
    myParent = parent;
  }
}
