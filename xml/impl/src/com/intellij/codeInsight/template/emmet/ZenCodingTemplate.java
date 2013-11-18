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
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


/**
 * @author Eugene.Kudelevsky
 */
public class ZenCodingTemplate extends CustomLiveTemplateBase {
  public static final char MARKER = '\0';
  private static final String EMMET_RECENT_WRAP_ABBREVIATIONS_KEY = "emmet.recent.wrap.abbreviations";
  private static final String EMMET_LAST_WRAP_ABBREVIATIONS_KEY = "emmet.last.wrap.abbreviations";

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
                                    @NotNull ZenCodingGenerator generator,
                                    @Nullable String surroundedText) {
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
    expand(key, callback, null, defaultGenerator, Collections.<ZenCodingFilter>emptyList(), true);
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


  public static void expand(@NotNull String key, @NotNull CustomTemplateCallback callback, @Nullable String surroundedText,
                            @NotNull ZenCodingGenerator defaultGenerator,
                            @NotNull Collection<? extends ZenCodingFilter> extraFilters,
                            boolean expandPrimitiveAbbreviations) {
    final ZenCodingNode node = parse(key, callback, defaultGenerator, surroundedText);
    if (node == null) {
      return;
    }
    if (surroundedText == null) {
      if (node instanceof TemplateNode) {
        if (key.equals(((TemplateNode)node).getTemplateToken().getKey()) && callback.findApplicableTemplates(key).size() > 1) {
          callback.startTemplate();
          return;
        }
      }
      callback.deleteTemplateKey(key);
    }

    PsiElement context = callback.getContext();
    ZenCodingGenerator generator = findApplicableGenerator(node, context, false);
    List<ZenCodingFilter> filters = getFilters(node, context);
    filters.addAll(extraFilters);

    expand(node, generator, filters, surroundedText, callback, expandPrimitiveAbbreviations);
  }

  private static void expand(ZenCodingNode node,
                             ZenCodingGenerator generator,
                             List<ZenCodingFilter> filters,
                             String surroundedText,
                             CustomTemplateCallback callback, boolean expandPrimitiveAbbreviations) {
    if (surroundedText != null) {
      surroundedText = surroundedText.trim();
    }

    GenerationNode fakeParentNode = new GenerationNode(TemplateToken.EMPTY_TEMPLATE_TOKEN, -1, 1, surroundedText, true, null);
    node.expand(-1, 1, surroundedText, callback, true, fakeParentNode);

    if (!expandPrimitiveAbbreviations) {
      if (isPrimitiveNode(node)) {
        return;
      }
    }

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

  private static boolean isPrimitiveNode(@NotNull ZenCodingNode node) {
    if (node instanceof TemplateNode) {
      final TemplateToken token = ((TemplateNode)node).getTemplateToken();
      if (token != null && token.getAttribute2Value().isEmpty()) {
        return true;
      }
    }
    return false;
  }

  public void wrap(@NotNull final String selection, @NotNull final CustomTemplateCallback callback) {
    final TextFieldWithStoredHistory field = new TextFieldWithStoredHistory(EMMET_RECENT_WRAP_ABBREVIATIONS_KEY);
    final Dimension fieldPreferredSize = field.getPreferredSize();
    field.setPreferredSize(new Dimension(Math.max(160, fieldPreferredSize.width), fieldPreferredSize.height));
    field.setHistorySize(10);
    final JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    final BalloonImpl balloon = (BalloonImpl)popupFactory.createDialogBalloonBuilder(field, XmlBundle.message("emmet.title"))
      .setCloseButtonEnabled(false)
      .setBlockClicksThroughBalloon(true)
      .setAnimationCycle(0)
      .setHideOnKeyOutside(true)
      .createBalloon();
    
    field.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        validateTemplateKey(field, balloon, field.getText(), callback);
      }
    });
    field.addKeyboardListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (!field.isPopupVisible()) {
          switch (e.getKeyCode()) {
            case KeyEvent.VK_ENTER:
              final String abbreviation = field.getText();
              if (validateTemplateKey(field, balloon, abbreviation, callback)) {
                doWrap(selection, abbreviation, callback);
                PropertiesComponent.getInstance().setValue(EMMET_LAST_WRAP_ABBREVIATIONS_KEY, abbreviation);
                field.addCurrentTextToHistory();
                balloon.hide(true);
              }
              break;
            case KeyEvent.VK_ESCAPE:
              balloon.hide(false);
              break;
          }
        }
      }
    });

    IdeEventQueue.getInstance().addDispatcher(new IdeEventQueue.EventDispatcher() {
      @Override
      public boolean dispatch(AWTEvent e) {
        if (e instanceof MouseEvent) {
          if (e.getID() == MouseEvent.MOUSE_PRESSED) {
            if (!balloon.isInsideBalloon((MouseEvent)e) && !PopupUtil.isComboPopupKeyEvent((ComponentEvent)e, field)) {
              balloon.hide();
            }
          }
        }
        return false;
      }
    }, balloon);

    balloon.addListener(new JBPopupListener.Adapter() {
      @Override
      public void beforeShown(LightweightWindowEvent event) {
        field.setText(PropertiesComponent.getInstance().getValue(EMMET_LAST_WRAP_ABBREVIATIONS_KEY, ""));
      }
    });
    balloon.show(popupFactory.guessBestPopupLocation(callback.getEditor()), Balloon.Position.below);

    final IdeFocusManager focusManager = IdeFocusManager.getInstance(callback.getProject());
    focusManager.doWhenFocusSettlesDown(new Runnable() {
      @Override
      public void run() {
        focusManager.requestFocus(field, true);
      }
    });
  }

  private static boolean validateTemplateKey(@NotNull TextFieldWithHistory field,
                                             @Nullable Balloon balloon,
                                             @NotNull String abbreviation,
                                             @NotNull CustomTemplateCallback callback) {
    final boolean correct = checkTemplateKey(abbreviation, callback);
    field.getTextEditor().setBackground(correct ? LightColors.SLIGHTLY_GREEN : LightColors.RED);
    if (balloon != null && !balloon.isDisposed()) {
      balloon.revalidate();
    }
    return correct;
  }

  static boolean checkTemplateKey(String inputString, CustomTemplateCallback callback) {
    ZenCodingGenerator generator = findApplicableDefaultGenerator(callback.getContext(), true);
    assert generator != null;
    return checkTemplateKey(inputString, callback, generator);
  }

  public boolean isApplicable(PsiFile file, int offset, boolean wrapping) {
    if (file == null) {
      return false;
    }
    PsiDocumentManager.getInstance(file.getProject()).commitAllDocuments();
    PsiElement element = CustomTemplateCallback.getContext(file, offset);
    final ZenCodingGenerator applicableGenerator = findApplicableDefaultGenerator(element, wrapping);
    return applicableGenerator != null && applicableGenerator.isEnabled();
  }

  @Override
  public boolean hasCompletionItem(@NotNull PsiFile file, int offset) {
    PsiElement element = CustomTemplateCallback.getContext(file, offset);
    final ZenCodingGenerator applicableGenerator = findApplicableDefaultGenerator(element, false);
    return applicableGenerator != null && applicableGenerator.isEnabled() && applicableGenerator.hasCompletionItem();
  }

  public static void doWrap(final String selection, final String abbreviation, final CustomTemplateCallback callback) {
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

            expand(node, generator, filters, selection, callback, true);
          }
        }, CodeInsightBundle.message("insert.code.template.command"), null);
      }
    });
  }

  @NotNull
  public String getTitle() {
    return XmlBundle.message("emmet.title");
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
