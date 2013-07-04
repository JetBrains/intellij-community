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
package com.intellij.codeInsight.template.emmet;

import com.intellij.application.options.emmet.EmmetOptions;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.emmet.filters.SingleLineEmmetFilter;
import com.intellij.codeInsight.template.emmet.filters.ZenCodingFilter;
import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.nodes.*;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.codeInsight.template.emmet.tokens.TextToken;
import com.intellij.codeInsight.template.emmet.tokens.ZenCodingToken;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * @author Eugene.Kudelevsky
 */
public class ZenCodingTemplate implements CustomLiveTemplate {
  public static final char MARKER = '\0';

  @Nullable
  public static ZenCodingGenerator findApplicableDefaultGenerator(@NotNull PsiElement context, boolean wrapping) {
    for (ZenCodingGenerator generator : ZenCodingGenerator.getInstances()) {
      if (generator.isMyContext(context, wrapping) && generator.isAppliedByDefault(context)) {
        return generator;
      }
    }
    return null;
  }

  @Nullable
  public static ZenCodingNode parse(@NotNull String text,
                                     @NotNull CustomTemplateCallback callback,
                                     @NotNull ZenCodingGenerator generator, @Nullable String surroundedText) {
    List<ZenCodingToken> tokens = new EmmetLexer().lex(text);
    if (tokens == null) {
      return null;
    }
    if (!validate(tokens, generator)) {
      return null;
    }
    EmmetParser parser = generator.createParser(tokens, callback, generator, surroundedText != null);
    ZenCodingNode node = parser.parse();
    if (parser.getIndex() != tokens.size() || node instanceof TextNode) {
      return null;
    }
    return node;
  }

  private static boolean validate(@NotNull List<ZenCodingToken> tokens, @NotNull ZenCodingGenerator generator) {
    for (ZenCodingToken token : tokens) {
      if (token instanceof TextToken && !(generator instanceof XmlZenCodingGenerator)) {
        return false;
      }
    }
    return true;
  }

  public static boolean checkTemplateKey(@NotNull String key, CustomTemplateCallback callback, @NotNull ZenCodingGenerator generator) {
    return parse(key, callback, generator, null) != null;
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


  public static void expand(String key,
                             @NotNull CustomTemplateCallback callback,
                             String surroundedText,
                             @NotNull ZenCodingGenerator defaultGenerator) {
    ZenCodingNode node = parse(key, callback, defaultGenerator, surroundedText);
    if (node == null) {
      return;
    }
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

    GenerationNode fakeParentNode = new GenerationNode(TemplateToken.EMPTY_TEMPLATE_TOKEN, -1, 1, surroundedText, true, null);
    node.expand(-1, 1, surroundedText, callback, true, fakeParentNode);

    List<GenerationNode> genNodes = fakeParentNode.getChildren();
    LiveTemplateBuilder builder = new LiveTemplateBuilder();
    int end = -1;
    for (int i = 0, genNodesSize = genNodes.size(); i < genNodesSize; i++) {
      GenerationNode genNode = genNodes.get(i);
      TemplateImpl template = genNode.generate(callback, generator, filters, true);
      int e = builder.insertTemplate(builder.length(), template, null);
      if (i < genNodesSize - 1 && genNode.isInsertNewLineBetweenNodes()) {
        builder.insertText(e, "\n", false);
        e++;
      }
      if (end == -1 && end < builder.length()) {
        end = e;
      }
    }
    for (ZenCodingFilter filter : filters) {
      if (filter instanceof SingleLineEmmetFilter) {
        builder.setIsToReformat(false);
        break;
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
        if (!checkTemplateKey(inputString, callback)) {
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

  public static boolean checkTemplateKey(String inputString, CustomTemplateCallback callback) {
    ZenCodingGenerator generator = findApplicableDefaultGenerator(callback.getContext(), true);
    assert generator != null;
    return checkTemplateKey(inputString, callback, generator);
  }

  public boolean isApplicable(PsiFile file, int offset, boolean wrapping) {
    EmmetOptions emmetOptions = EmmetOptions.getInstance();
    if (!emmetOptions.isEmmetEnabled()) {
      return false;
    }
    if (file == null) {
      return false;
    }
    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
    PsiElement element = CustomTemplateCallback.getContext(file, offset);
    return findApplicableDefaultGenerator(element, wrapping) != null;
  }

  public static void doWrap(final String selection,
                            final String abbreviation,
                            final CustomTemplateCallback callback) {
    final ZenCodingGenerator defaultGenerator = findApplicableDefaultGenerator(callback.getContext(), true);
    assert defaultGenerator != null;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        CommandProcessor.getInstance().executeCommand(callback.getProject(), new Runnable() {
          public void run() {
            callback.fixInitialState(true);
            ZenCodingNode node = parse(abbreviation, callback, defaultGenerator, selection);
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
    return (char)EmmetOptions.getInstance().getEmmetExpandShortcut();
  }

  public String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
    ZenCodingGenerator generator = findApplicableDefaultGenerator(callback.getContext(), false);
    if (generator == null) return null;
    return generator.computeTemplateKey(callback);
  }

  public boolean supportsWrapping() {
    return true;
  }
}
