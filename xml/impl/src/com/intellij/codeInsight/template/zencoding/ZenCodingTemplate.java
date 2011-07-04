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
import com.intellij.xml.XmlBundle;
import org.apache.xerces.util.XML11Char;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


/**
 * @author Eugene.Kudelevsky
 */
public class ZenCodingTemplate implements CustomLiveTemplate {
  public static final char MARKER = '\0';
  private static final String DELIMS = ">+*|()[].#,='\" \0";
  public static final String ATTRS = "ATTRS";
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
  private static ZenCodingGenerator findApplicableDefaultGenerator(@NotNull PsiElement context, boolean wrapping) {
    for (ZenCodingGenerator generator : ZenCodingGenerator.getInstances()) {
      if (generator.isMyContext(context, wrapping) && generator.isAppliedByDefault(context)) {
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
    List<ZenCodingToken> tokens = lex(text);
    if (tokens == null) {
      return null;
    }
    MyParser parser = new MyParser(tokens, callback, generator);
    ZenCodingNode node = parser.parse();
    return parser.myIndex == tokens.size() ? node : null;
  }

  @Nullable
  private static List<ZenCodingToken> lex(@NotNull String text) {
    text += MARKER;
    final List<ZenCodingToken> result = new ArrayList<ZenCodingToken>();

    boolean inQuotes = false;
    boolean inApostrophes = false;

    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < text.length(); i++) {
      final char c = text.charAt(i);

      if (inQuotes) {
        builder.append(c);
        if (c == '"') {
          inQuotes = false;
          result.add(new StringLiteralToken(builder.toString()));
          builder = new StringBuilder();
        }
        continue;
      }

      if (inApostrophes) {
        builder.append(c);
        if (c == '\'') {
          inApostrophes = false;
          result.add(new StringLiteralToken(builder.toString()));
          builder = new StringBuilder();
        }
        continue;
      }

      if (DELIMS.indexOf(c) < 0) {
        builder.append(c);
      }
      else {
        // handle special case: ul+ template
        if (c == '+' && (i == text.length() - 2 || text.charAt(i + 1) == ')')) {
          builder.append(c);
          continue;
        }

        if (builder.length() > 0) {
          final String tokenText = builder.toString();
          final int n = parseNonNegativeInt(tokenText);
          if (n >= 0) {
            result.add(new NumberToken(n));
          }
          else {
            result.add(new IdentifierToken(tokenText));
          }
          builder = new StringBuilder();
        }
        if (c == '"') {
          inQuotes = true;
          builder.append(c);
        }
        else if (c == '\'') {
          inApostrophes = true;
          builder.append(c);
        }
        else if (c == '(') {
          result.add(ZenCodingTokens.OPENING_BRACE);
        }
        else if (c == ')') {
          result.add(ZenCodingTokens.CLOSING_BRACE);
        }
        else if (c == '[') {
          result.add(ZenCodingTokens.OPENING_SQ_BRACKET);
        }
        else if (c == ']') {
          result.add(ZenCodingTokens.CLOSING_SQ_BRACKET);
        }
        else if (c == '=') {
          result.add(ZenCodingTokens.EQ);
        }
        else if (c == '.') {
          result.add(ZenCodingTokens.DOT);
        }
        else if (c == '#') {
          result.add(ZenCodingTokens.SHARP);
        }
        else if (c == ',') {
          result.add(ZenCodingTokens.COMMA);
        }
        else if (c == ' ') {
          result.add(ZenCodingTokens.SPACE);
        }
        else if (c == '|') {
          result.add(ZenCodingTokens.PIPE);
        }
        else if (c != MARKER) {
          result.add(new OperationToken(c));
        }
      }
    }
    return result;
  }


  public static boolean checkTemplateKey(@NotNull String key, CustomTemplateCallback callback, ZenCodingGenerator generator) {
    return parse(key, callback, generator) != null;
  }

  public void expand(String key, @NotNull CustomTemplateCallback callback) {
    ZenCodingGenerator defaultGenerator = findApplicableDefaultGenerator(callback.getContext(), false);
    assert defaultGenerator != null;
    expand(key, callback, null, defaultGenerator);
  }

  @Nullable
  private static ZenCodingGenerator findApplicableGenerator(ZenCodingNode node, PsiElement context, boolean wrapping) {
    ZenCodingGenerator defaultGenerator = null;
    List<ZenCodingGenerator> generators = ZenCodingGenerator.getInstances();
    for (ZenCodingGenerator generator : generators) {
      if (defaultGenerator == null && generator.isMyContext(context, wrapping) && generator.isAppliedByDefault(context)) {
        defaultGenerator = generator;
      }
    }
    while (node instanceof FilterNode) {
      FilterNode filterNode = (FilterNode)node;
      String suffix = filterNode.getFilter();
      for (ZenCodingGenerator generator : generators) {
        if (generator.isMyContext(context, wrapping)) {
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
                             String surroundedText,
                             @NotNull ZenCodingGenerator defaultGenerator) {
    ZenCodingNode node = parse(key, callback, defaultGenerator);
    assert node != null;
    if (surroundedText == null) {
      if (node instanceof TemplateNode) {
        if (key.equals(((TemplateNode)node).getTemplateToken().getKey()) &&
            callback.findApplicableTemplates(key).size() > 1) {
          callback.startTemplate();
          return;
        }
      }
      callback.deleteTemplateKey(key);
    }

    PsiElement context = callback.getContext();
    ZenCodingGenerator generator = findApplicableGenerator(node, context, false);
    List<ZenCodingFilter> filters = getFilters(node, context);

    expand(node, generator, filters, surroundedText, callback);
  }

  private static void expand(ZenCodingNode node,
                             ZenCodingGenerator generator,
                             List<ZenCodingFilter> filters,
                             String surroundedText,
                             CustomTemplateCallback callback) {
    if (surroundedText != null) {
      surroundedText = surroundedText.trim();
    }
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
        ZenCodingGenerator generator = findApplicableDefaultGenerator(callback.getContext(), true);
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

  public boolean isApplicable(PsiFile file, int offset, boolean wrapping) {
    WebEditorOptions webEditorOptions = WebEditorOptions.getInstance();
    if (!webEditorOptions.isZenCodingEnabled()) {
      return false;
    }
    if (file == null) {
      return false;
    }
    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
    PsiElement element = CustomTemplateCallback.getContext(file, offset);
    return findApplicableDefaultGenerator(element, wrapping) != null;
  }

  protected static void doWrap(final String selection,
                               final String abbreviation,
                               final CustomTemplateCallback callback) {
    final ZenCodingGenerator defaultGenerator = findApplicableDefaultGenerator(callback.getContext(), true);
    assert defaultGenerator != null;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(callback.getProject(), new Runnable() {
          public void run() {
            callback.fixInitialState(true);
            ZenCodingNode node = parse(abbreviation, callback, defaultGenerator);
            assert node != null;
            PsiElement context = callback.getContext();
            ZenCodingGenerator generator = findApplicableGenerator(node, context, true);
            List<ZenCodingFilter> filters = getFilters(node, context);

            EditorModificationUtil.deleteSelectedText(callback.getEditor());
            PsiDocumentManager.getInstance(callback.getProject()).commitAllDocuments();

            expand(node, generator, filters, selection, callback);
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
    ZenCodingGenerator generator = findApplicableDefaultGenerator(callback.getContext(), false);
    if (generator == null) return null;
    return generator.computeTemplateKey(callback);
  }

  public boolean supportsWrapping() {
    return true;
  }

  public static boolean checkFilterSuffix(@NotNull String suffix) {
    for (ZenCodingGenerator generator : ZenCodingGenerator.getInstances()) {
      if (suffix.equals(generator.getSuffix())) {
        return true;
      }
    }
    for (ZenCodingFilter filter : ZenCodingFilter.getInstances()) {
      if (suffix.equals(filter.getSuffix())) {
        return true;
      }
    }
    return false;
  }

  private static class MyParser {
    private final List<ZenCodingToken> myTokens;
    private final CustomTemplateCallback myCallback;
    private final ZenCodingGenerator myGenerator;
    private int myIndex = 0;

    private MyParser(List<ZenCodingToken> tokens, CustomTemplateCallback callback, ZenCodingGenerator generator) {
      myTokens = tokens;
      myCallback = callback;
      myGenerator = generator;
    }

    @Nullable
    private ZenCodingNode parse() {
      ZenCodingNode add = parseAddOrMore();
      if (add == null) {
        return null;
      }

      ZenCodingNode result = add;

      while (true) {
        ZenCodingToken token = nextToken();
        if (token != ZenCodingTokens.PIPE) {
          return result;
        }

        myIndex++;
        token = nextToken();
        if (!(token instanceof IdentifierToken)) {
          return null;
        }

        final String filterSuffix = ((IdentifierToken)token).getText();
        if (!checkFilterSuffix(filterSuffix)) {
          return null;
        }

        myIndex++;
        result = new FilterNode(result, filterSuffix);
      }
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
      if (openingBrace == ZenCodingTokens.OPENING_BRACE) {
        myIndex++;
        ZenCodingNode add = parseAddOrMore();
        if (add == null) {
          return null;
        }
        ZenCodingToken closingBrace = nextToken();
        if (closingBrace != ZenCodingTokens.CLOSING_BRACE) {
          return null;
        }
        myIndex++;
        return add;
      }
      return parseTemplate();
    }

    @Nullable
    private ZenCodingNode parseTemplate() {
      final ZenCodingToken token = nextToken();
      String templateKey = isHtml(myCallback) ? DEFAULT_TAG : null;
      boolean mustHaveSelector = true;

      if (token instanceof IdentifierToken) {
        templateKey = ((IdentifierToken)token).getText();
        mustHaveSelector = false;
        myIndex++;
      }

      if (templateKey == null) {
        return null;
      }

      final TemplateImpl template = myCallback.findApplicableTemplate(templateKey);
      if (template == null && !isXML11ValidQName(templateKey)) {
        return null;
      }

      final List<Pair<String, String>> attrList = parseSelectors();
      if (mustHaveSelector && attrList.size() == 0) {
        return null;
      }

      final TemplateToken templateToken = new TemplateToken(templateKey, attrList);

      if (!setTemplate(templateToken, template)) {
        return null;
      }
      return new TemplateNode(templateToken);
    }

    @SuppressWarnings("unchecked")
    @NotNull
    private List<Pair<String, String>> parseSelectors() {
      final List<Pair<String, String>> result = new ArrayList<Pair<String, String>>();

      int classAttrPosition = -1;
      int idAttrPosition = -1;

      final StringBuilder classAttrBuilder = new StringBuilder();
      final StringBuilder idAttrBuilder = new StringBuilder();

      while (true) {
        final List<Pair<String, String>> attrList = parseSelector();
        if (attrList == null) {
          if (classAttrPosition != -1) {
            result.set(classAttrPosition, new Pair<String, String>(CLASS, classAttrBuilder.toString()));
          }
          if (idAttrPosition != -1) {
            result.set(idAttrPosition, new Pair<String, String>(ID, idAttrBuilder.toString()));
          }
          return result;
        }

        for (Pair<String, String> attr : attrList) {
          if (CLASS.equals(attr.first)) {
            if (classAttrBuilder.length() > 0) {
              classAttrBuilder.append(' ');
            }
            classAttrBuilder.append(attr.second);
            if (classAttrPosition == -1) {
              classAttrPosition = result.size();
              result.add(attr);
            }
          }
          else if (ID.equals(attr.first)) {
            if (idAttrBuilder.length() > 0) {
              idAttrBuilder.append(' ');
            }
            idAttrBuilder.append(attr.second);
            if (idAttrPosition == -1) {
              idAttrPosition = result.size();
              result.add(attr);
            }
          }
          else {
            result.add(attr);
          }
        }
      }
    }

    @Nullable
    private List<Pair<String, String>> parseSelector() {
      ZenCodingToken token = nextToken();
      if (token == ZenCodingTokens.OPENING_SQ_BRACKET) {
        myIndex++;
        final List<Pair<String, String>> attrList = parseAttributeList();
        if (attrList == null || nextToken() != ZenCodingTokens.CLOSING_SQ_BRACKET) {
          return null;
        }
        myIndex++;
        return attrList;
      }

      if (token == ZenCodingTokens.DOT || token == ZenCodingTokens.SHARP) {
        final String name = token == ZenCodingTokens.DOT ? CLASS : ID;
        myIndex++;
        token = nextToken();
        final String value = getAttributeValueByToken(token);
        myIndex++;
        return value != null ? Collections.singletonList(new Pair<String, String>(name, value)) : null;
      }

      return null;
    }

    private boolean setTemplate(final TemplateToken token, TemplateImpl template) {
      if (template == null) {
        template = myGenerator.createTemplateByKey(token.getKey());
      }
      if (template == null) {
        return false;
      }
      token.setTemplate(template);
      final XmlFile xmlFile = parseXmlFileInTemplate(template.getString(), myCallback, true);
      token.setFile(xmlFile);
      XmlDocument document = xmlFile.getDocument();
      final XmlTag tag = document != null ? document.getRootTag() : null;
      if (token.getAttribute2Value().size() > 0 && tag == null) {
        return false;
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
      return true;
    }

    @Nullable
    private List<Pair<String, String>> parseAttributeList() {
      final List<Pair<String, String>> result = new ArrayList<Pair<String, String>>();
      while (true) {
        final Pair<String, String> attribute = parseAttribute();
        if (attribute == null) {
          return result;
        }
        result.add(attribute);

        final ZenCodingToken token = nextToken();
        if (token != ZenCodingTokens.COMMA && token != ZenCodingTokens.SPACE) {
          return result;
        }
        myIndex++;
      }
    }

    @Nullable
    private Pair<String, String> parseAttribute() {
      ZenCodingToken token = nextToken();
      if (!(token instanceof IdentifierToken)) {
        return null;
      }

      final String name = ((IdentifierToken)token).getText();

      myIndex++;
      token = nextToken();
      if (token != ZenCodingTokens.EQ) {
        return null;
      }

      myIndex++;
      token = nextToken();
      final String value = getAttributeValueByToken(token);
      if (value != null) {
        myIndex++;
      }
      return new Pair<String, String>(name, value != null ? value : "");
    }

    @Nullable
    private static String getAttributeValueByToken(ZenCodingToken token) {
      if (token instanceof StringLiteralToken) {
        final String text = ((StringLiteralToken)token).getText();
        return text.substring(1, text.length() - 1);
      }
      else if (token instanceof IdentifierToken) {
        return ((IdentifierToken)token).getText();
      }
      else if (token instanceof NumberToken) {
        return Integer.toString(((NumberToken)token).getNumber());
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
