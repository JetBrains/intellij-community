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
package com.intellij.codeInsight.template.zencoding;

import com.intellij.application.options.editor.WebEditorOptions;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.zencoding.filters.ZenCodingFilter;
import com.intellij.codeInsight.template.zencoding.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.zencoding.nodes.*;
import com.intellij.codeInsight.template.zencoding.tokens.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.HashSet;
import com.intellij.xml.XmlBundle;
import org.apache.xerces.util.XML11Char;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;


/**
 * @author Eugene.Kudelevsky
 */
public class ZenCodingTemplate implements CustomLiveTemplate {
  public static final char MARKER = '$';
  private static final String DELIMS = ">+*()";
  public static final String ATTRS = "ATTRS";
  private static final String SELECTORS = ".#[";
  private static final String ID = "id";
  private static final String CLASS = "class";
  private static final String DEFAULT_TAG = "div";

  private static int parseNonNegativeInt(@NotNull String s) {
    try {
      return Integer.parseInt(s);
    }
    catch (Throwable ignored) {
    }
    return -1;
  }

  private static String getPrefix(@NotNull String templateKey) {
    for (int i = 0, n = templateKey.length(); i < n; i++) {
      char c = templateKey.charAt(i);
      if (SELECTORS.indexOf(c) >= 0) {
        return templateKey.substring(0, i);
      }
    }
    return templateKey;
  }

  @Nullable
  private static Pair<String, String> parseAttrNameAndValue(@NotNull String text) {
    int eqIndex = text.indexOf('=');
    if (eqIndex > 0) {
      String value = text.substring(eqIndex + 1);
      if (value.length() >= 2 && (
        (value.charAt(0) == '\'' && value.charAt(value.length() - 1) == '\'') ||
        (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"'))) {
        value = value.substring(1, value.length() - 1);
      }
      return new Pair<String, String>(text.substring(0, eqIndex), value);
    }
    return null;
  }

  @Nullable
  private static TemplateToken parseSelectors(@NotNull String text) {
    String templateKey = null;
    List<Pair<String, String>> attributes = new ArrayList<Pair<String, String>>();
    Set<String> definedAttrs = new HashSet<String>();
    final List<String> classes = new ArrayList<String>();
    StringBuilder builder = new StringBuilder();
    char lastDelim = 0;
    text += MARKER;
    int classAttrPosition = -1;
    for (int i = 0, n = text.length(); i < n; i++) {
      char c = text.charAt(i);
      if (c == '#' || c == '.' || c == '[' || c == ']' || i == n - 1) {
        if (c != ']') {
          switch (lastDelim) {
            case 0:
              templateKey = builder.toString();
              break;
            case '#':
              if (!definedAttrs.add(ID)) {
                return null;
              }
              attributes.add(new Pair<String, String>(ID, builder.toString()));
              break;
            case '.':
              if (builder.length() <= 0) {
                return null;
              }
              if (classAttrPosition < 0) {
                classAttrPosition = attributes.size();
              }
              classes.add(builder.toString());
              break;
            case ']':
              if (builder.length() > 0) {
                return null;
              }
              break;
            default:
              return null;
          }
        }
        else if (lastDelim != '[') {
          return null;
        }
        else {
          Pair<String, String> pair = parseAttrNameAndValue(builder.toString());
          if (pair == null || !definedAttrs.add(pair.first)) {
            return null;
          }
          attributes.add(pair);
        }
        lastDelim = c;
        builder = new StringBuilder();
      }
      else {
        builder.append(c);
      }
    }
    if (classes.size() > 0) {
      if (definedAttrs.contains(CLASS)) {
        return null;
      }
      StringBuilder classesAttrValue = new StringBuilder();
      for (int i = 0; i < classes.size(); i++) {
        classesAttrValue.append(classes.get(i));
        if (i < classes.size() - 1) {
          classesAttrValue.append(' ');
        }
      }
      assert classAttrPosition >= 0;
      attributes.add(classAttrPosition, new Pair<String, String>(CLASS, classesAttrValue.toString()));
    }
    return new TemplateToken(templateKey, attributes);
  }

  private static boolean isXML11ValidQName(String str) {
    final int colon = str.indexOf(':');
    if (colon == 0 || colon == str.length() - 1) {
      return false;
    }
    if (colon > 0) {
      final String prefix = str.substring(0, colon);
      final String localPart = str.substring(colon + 1);
      return XML11Char.isXML11ValidNCName(prefix) && XML11Char.isXML11ValidNCName(localPart);
    }
    return XML11Char.isXML11ValidNCName(str);
  }

  private static boolean isHtml(CustomTemplateCallback callback) {
    FileType type = callback.getFileType();
    return type == StdFileTypes.HTML || type == StdFileTypes.XHTML;
  }

  private static void addMissingAttributes(XmlTag tag, List<Pair<String, String>> value) {
    List<Pair<String, String>> attr2value = new ArrayList<Pair<String, String>>(value);
    for (Iterator<Pair<String, String>> iterator = attr2value.iterator(); iterator.hasNext();) {
      Pair<String, String> pair = iterator.next();
      if (tag.getAttribute(pair.first) != null) {
        iterator.remove();
      }
    }
    addAttributesBefore(tag, attr2value);
  }

  @SuppressWarnings({"deprecation"})
  private static void addAttributesBefore(XmlTag tag, List<Pair<String, String>> attr2value) {
    XmlAttribute[] attributes = tag.getAttributes();
    XmlAttribute firstAttribute = attributes.length > 0 ? attributes[0] : null;
    XmlElementFactory factory = XmlElementFactory.getInstance(tag.getProject());
    for (Pair<String, String> pair : attr2value) {
      XmlAttribute xmlAttribute = factory.createXmlAttribute(pair.first, "");
      if (firstAttribute != null) {
        tag.addBefore(xmlAttribute, firstAttribute);
      }
      else {
        tag.add(xmlAttribute);
      }
    }
  }

  @Nullable
  private static ZenCodingGenerator findApplicableDefaultGenerator(@NotNull PsiElement context) {
    for (ZenCodingGenerator generator : ZenCodingGenerator.getInstances()) {
      if (generator.isMyContext(context) && generator.isAppliedByDefault(context)) {
        return generator;
      }
    }
    return null;
  }

  @NotNull
  private static XmlFile parseXmlFileInTemplate(String templateString, CustomTemplateCallback callback, boolean createPhysicalFile) {
    XmlFile xmlFile = (XmlFile)PsiFileFactory.getInstance(callback.getProject())
      .createFileFromText("dummy.xml", StdFileTypes.XML, templateString, LocalTimeCounter.currentTime(), createPhysicalFile);
    VirtualFile vFile = xmlFile.getVirtualFile();
    if (vFile != null) {
      vFile.putUserData(UndoManager.DONT_RECORD_UNDO, Boolean.TRUE);
    }
    return xmlFile;
  }

  @Nullable
  private static ZenCodingNode parse(@NotNull String text, @NotNull CustomTemplateCallback callback, ZenCodingGenerator generator) {
    List<ZenCodingToken> tokens = lex(text, callback, generator);
    if (tokens == null) {
      return null;
    }
    MyParser parser = new MyParser(tokens);
    ZenCodingNode node = parser.parse();
    return parser.myIndex == tokens.size() - 1 ? node : null;
  }

  @Nullable
  private static List<ZenCodingToken> lex(@NotNull String text, @NotNull CustomTemplateCallback callback, ZenCodingGenerator generator) {
    String filterString = null;

    int filterDelim = text.indexOf('|');
    if (filterDelim >= 0 && filterDelim < text.length() - 1) {
      filterString = text.substring(filterDelim + 1);
      text = text.substring(0, filterDelim);
    }

    boolean inQuotes = false;
    boolean inApostrophes = false;
    text += MARKER;
    StringBuilder templateKeyBuilder = new StringBuilder();
    List<ZenCodingToken> result = new ArrayList<ZenCodingToken>();
    for (int i = 0, n = text.length(); i < n; i++) {
      char c = text.charAt(i);
      if (inQuotes || inApostrophes) {
        templateKeyBuilder.append(c);
        if (c == '"') {
          inQuotes = false;
        }
        else if (c == '\'') inApostrophes = false;
      }
      else if (i == n - 1 || (c == ')' || c == '*' || i < n - 2 && DELIMS.indexOf(c) >= 0)) {
        String key = templateKeyBuilder.toString();
        templateKeyBuilder = new StringBuilder();
        int num = parseNonNegativeInt(key);
        if (num > 0) {
          result.add(new NumberToken(num));
        }
        else {
          TemplateToken token = parseTemplateKey(key, callback, generator);
          if (token != null) {
            result.add(token);
          }
        }
        if (i == n - 1) {
          result.add(new MarkerToken());
        }
        else if (c == '(') {
          result.add(new OpeningBraceToken());
        }
        else if (c == ')') {
          result.add(new ClosingBraceToken());
        }
        else {
          result.add(new OperationToken(c));
        }
      }
      else if (!Character.isWhitespace(c)) {
        templateKeyBuilder.append(c);
        if (c == '"') {
          inQuotes = true;
        }
        else if (c == '\'') inApostrophes = true;
      }
      else {
        return null;
      }
    }

    if (inQuotes || inApostrophes) {
      return null;
    }

    // at least MarkerToken
    assert result.size() > 0;

    if (filterString != null) {
      String[] filterSuffixes = filterString.split("\\|");
      for (String filterSuffix : filterSuffixes) {
        result.add(result.size() - 1, new FilterToken(filterSuffix));
      }
    }
    return result;
  }

  @Nullable
  protected static TemplateToken parseTemplateKey(String key, CustomTemplateCallback callback, ZenCodingGenerator generator) {
    String prefix = getPrefix(key);
    boolean useDefaultTag = false;
    if (prefix.length() == 0) {
      if (!isHtml(callback)) {
        return null;
      }
      else {
        useDefaultTag = true;
        prefix = DEFAULT_TAG;
        key = prefix + key;
      }
    }
    TemplateImpl template = callback.findApplicableTemplate(prefix);
    if (template == null && !isXML11ValidQName(prefix)) {
      return null;
    }
    final TemplateToken token = parseSelectors(key);
    if (token == null) {
      return null;
    }
    if (useDefaultTag && token.getAttribute2Value().size() == 0) {
      return null;
    }
    if (template == null) {
      template = generator.createTemplateByKey(token.getKey());
    }
    if (template == null) {
      return null;
    }
    assert prefix.equals(token.getKey());
    token.setTemplate(template);
    final XmlFile xmlFile = parseXmlFileInTemplate(template.getString(), callback, true);
    token.setFile(xmlFile);
    XmlDocument document = xmlFile.getDocument();
    final XmlTag tag = document != null ? document.getRootTag() : null;
    if (token.getAttribute2Value().size() > 0 && tag == null) {
      return null;
    }
    if (tag != null) {
      if (!containsAttrsVar(template) && token.getAttribute2Value().size() > 0) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            addMissingAttributes(tag, token.getAttribute2Value());
          }
        });
      }
    }
    return token;
  }


  public static boolean checkTemplateKey(String key, CustomTemplateCallback callback, ZenCodingGenerator generator) {
    return parse(key, callback, generator) != null;
  }

  public void expand(String key, @NotNull CustomTemplateCallback callback) {
    expand(key, callback, null);
  }

  @Nullable
  private static ZenCodingGenerator findApplicableGenerator(ZenCodingNode node, PsiElement context) {
    ZenCodingGenerator defaultGenerator = null;
    List<ZenCodingGenerator> generators = ZenCodingGenerator.getInstances();
    for (ZenCodingGenerator generator : generators) {
      if (defaultGenerator == null && generator.isMyContext(context) && generator.isAppliedByDefault(context)) {
        defaultGenerator = generator;
      }
    }
    while (node instanceof FilterNode) {
      FilterNode filterNode = (FilterNode)node;
      String suffix = filterNode.getFilter();
      for (ZenCodingGenerator generator : generators) {
        if (generator.isMyContext(context)) {
          if (suffix != null && suffix.equals(generator.getSuffix())) {
            return generator;
          }
        }
      }
      node = filterNode.getNode();
    }
    return defaultGenerator;
  }

  private static List<ZenCodingFilter> getFilters(ZenCodingNode node, PsiElement context) {
    List<ZenCodingFilter> result = new ArrayList<ZenCodingFilter>();

    while (node instanceof FilterNode) {
      FilterNode filterNode = (FilterNode)node;
      String filterSuffix = filterNode.getFilter();
      boolean filterFound = false;
      for (ZenCodingFilter filter : ZenCodingFilter.getInstances()) {
        if (filter.isMyContext(context) && filter.getSuffix().equals(filterSuffix)) {
          filterFound = true;
          result.add(filter);
        }
      }
      assert filterFound;
      node = filterNode.getNode();
    }

    for (ZenCodingFilter filter : ZenCodingFilter.getInstances()) {
      if (filter.isMyContext(context) && filter.isAppliedByDefault(context)) {
        result.add(filter);
      }
    }

    Collections.reverse(result);
    return result;
  }


  private static void expand(String key,
                             @NotNull CustomTemplateCallback callback,
                             String surroundedText) {
    ZenCodingGenerator defaultGenerator = findApplicableDefaultGenerator(callback.getContext());
    assert defaultGenerator != null;

    ZenCodingNode node = parse(key, callback, defaultGenerator);
    assert node != null;
    if (surroundedText == null) {
      if (node instanceof TemplateNode) {
        if (key.equals(((TemplateNode)node).getTemplateToken().getKey()) &&
            callback.findApplicableTemplates(key, defaultGenerator.getContextTypes()).size() > 1) {
          callback.startTemplate();
          return;
        }
      }
      callback.deleteTemplateKey(key);
    }

    PsiElement context = callback.getContext();
    ZenCodingGenerator generator = findApplicableGenerator(node, context);
    List<ZenCodingFilter> filters = getFilters(node, context);

    List<GenerationNode> genNodes = node.expand(-1, surroundedText);
    LiveTemplateBuilder builder = new LiveTemplateBuilder();
    int end = -1;
    for (int i = 0, genNodesSize = genNodes.size(); i < genNodesSize; i++) {
      GenerationNode genNode = genNodes.get(i);
      TemplateImpl template = genNode.generate(callback, generator, filters);
      int e = builder.insertTemplate(builder.length(), template, null);
      if (end == -1 && end < builder.length()) {
        end = e;
      }
    }
    /*if (end < builder.length() && end >= 0) {
      builder.insertVariableSegment(end, TemplateImpl.END);
    }*/
    callback.startTemplate(builder.buildTemplate(), null, new TemplateEditingAdapter() {
      private TextRange myEndVarRange;
      private Editor myEditor;

      @Override
      public void beforeTemplateFinished(TemplateState state, Template template) {
        int variableNumber = state.getCurrentVariableNumber();
        if (variableNumber >= 0 && template instanceof TemplateImpl) {
          TemplateImpl t = (TemplateImpl)template;
          while (variableNumber < t.getVariableCount()) {
            String varName = t.getVariableNameAt(variableNumber);
            if (LiveTemplateBuilder.isEndVariable(varName)) {
              myEndVarRange = state.getVariableRange(varName);
              myEditor = state.getEditor();
              break;
            }
            variableNumber++;
          }
        }
      }

      @Override
      public void templateFinished(Template template, boolean brokenOff) {
        if (brokenOff && myEndVarRange != null && myEditor != null) {
          int offset = myEndVarRange.getStartOffset();
          if (offset >= 0 && offset != myEditor.getCaretModel().getOffset()) {
            myEditor.getCaretModel().moveToOffset(offset);
            myEditor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
          }
        }
      }
    });
  }

  public void wrap(final String selection,
                   @NotNull final CustomTemplateCallback callback
  ) {
    InputValidatorEx validator = new InputValidatorEx() {
      public String getErrorText(String inputString) {
        ZenCodingGenerator generator = findApplicableDefaultGenerator(callback.getContext());
        assert generator != null;
        if (!checkTemplateKey(inputString, callback, generator)) {
          return XmlBundle.message("zen.coding.incorrect.abbreviation.error");
        }
        return null;
      }

      public boolean checkInput(String inputString) {
        return getErrorText(inputString) == null;
      }

      public boolean canClose(String inputString) {
        return checkInput(inputString);
      }
    };
    final String abbreviation = Messages
      .showInputDialog(callback.getProject(), XmlBundle.message("zen.coding.enter.abbreviation.dialog.label"),
                       XmlBundle.message("zen.coding.title"), Messages.getQuestionIcon(), "", validator);
    if (abbreviation != null) {
      doWrap(selection, abbreviation, callback);
    }
  }

  public boolean isApplicable(PsiFile file, int offset) {
    WebEditorOptions webEditorOptions = WebEditorOptions.getInstance();
    if (!webEditorOptions.isZenCodingEnabled()) {
      return false;
    }
    if (file == null) {
      return false;
    }
    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
    PsiElement element = CustomTemplateCallback.getContext(file, offset);
    return findApplicableDefaultGenerator(element) != null;
  }

  protected static void doWrap(final String selection,
                               final String abbreviation,
                               final CustomTemplateCallback callback) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(callback.getProject(), new Runnable() {
          public void run() {
            EditorModificationUtil.deleteSelectedText(callback.getEditor());
            PsiDocumentManager.getInstance(callback.getProject()).commitAllDocuments();
            callback.fixInitialState();
            expand(abbreviation, callback, selection);
          }
        }, CodeInsightBundle.message("insert.code.template.command"), null);
      }
    });
  }

  @NotNull
  public String getTitle() {
    return XmlBundle.message("zen.coding.title");
  }

  public char getShortcut() {
    return (char)WebEditorOptions.getInstance().getZenCodingExpandShortcut();
  }

  protected static boolean containsAttrsVar(TemplateImpl template) {
    for (int i = 0; i < template.getVariableCount(); i++) {
      String varName = template.getVariableNameAt(i);
      if (ATTRS.equals(varName)) {
        return true;
      }
    }
    return false;
  }

  public String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
    ZenCodingGenerator generator = findApplicableDefaultGenerator(callback.getContext());
    assert generator != null;
    return generator.computeTemplateKey(callback);
  }

  public boolean supportsWrapping() {
    return true;
  }

  private static class MyParser {
    private final List<ZenCodingToken> myTokens;
    private int myIndex = 0;

    private MyParser(List<ZenCodingToken> tokens) {
      myTokens = tokens;
    }

    @Nullable
    private ZenCodingNode parse() {
      ZenCodingNode add = parseAddOrMore();
      if (add == null) {
        return null;
      }
      ZenCodingToken filter = nextToken();
      ZenCodingNode result = add;
      while (filter instanceof FilterToken) {
        String suffix = ((FilterToken)filter).getSuffix();
        if (!ZenCodingParser.checkFilterSuffix(suffix)) {
          return null;
        }
        result = new FilterNode(result, suffix);
        myIndex++;
        filter = nextToken();
      }
      return result;
    }

    @Nullable
    private ZenCodingNode parseAddOrMore() {
      ZenCodingNode mul = parseMul();
      if (mul == null) {
        return null;
      }
      ZenCodingToken operationToken = nextToken();
      if (!(operationToken instanceof OperationToken)) {
        return mul;
      }
      char sign = ((OperationToken)operationToken).getSign();
      if (sign == '+') {
        myIndex++;
        ZenCodingNode add2 = parseAddOrMore();
        if (add2 == null) {
          return null;
        }
        return new AddOperationNode(mul, add2);
      }
      else if (sign == '>') {
        myIndex++;
        ZenCodingNode more2 = parseAddOrMore();
        if (more2 == null) {
          return null;
        }
        return new MoreOperationNode(mul, more2);
      }
      return null;
    }

    @Nullable
    private ZenCodingNode parseMul() {
      ZenCodingNode exp = parseExpressionInBraces();
      if (exp == null) {
        return null;
      }
      ZenCodingToken operationToken = nextToken();
      if (!(operationToken instanceof OperationToken)) {
        return exp;
      }
      if (((OperationToken)operationToken).getSign() != '*') {
        return exp;
      }
      myIndex++;
      ZenCodingToken numberToken = nextToken();
      if (numberToken instanceof NumberToken) {
        myIndex++;
        return new MulOperationNode(exp, ((NumberToken)numberToken).getNumber());
      }
      return new UnaryMulOperationNode(exp);
    }

    @Nullable
    private ZenCodingNode parseExpressionInBraces() {
      ZenCodingToken openingBrace = nextToken();
      if (openingBrace instanceof OpeningBraceToken) {
        myIndex++;
        ZenCodingNode add = parseAddOrMore();
        if (add == null) {
          return null;
        }
        ZenCodingToken closingBrace = nextToken();
        if (!(closingBrace instanceof ClosingBraceToken)) {
          return null;
        }
        myIndex++;
        return add;
      }
      ZenCodingToken templateToken = nextToken();
      if (templateToken instanceof TemplateToken) {
        myIndex++;
        return new TemplateNode((TemplateToken)templateToken);
      }
      return null;
    }

    @Nullable
    private ZenCodingToken nextToken() {
      if (myIndex < myTokens.size()) {
        return myTokens.get(myIndex);
      }
      return null;
    }
  }
}
