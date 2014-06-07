/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.emmet.filters.SingleLineEmmetFilter;
import com.intellij.codeInsight.template.emmet.filters.ZenCodingFilter;
import com.intellij.codeInsight.template.emmet.generators.XmlZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.generators.ZenCodingGenerator;
import com.intellij.codeInsight.template.emmet.nodes.*;
import com.intellij.codeInsight.template.emmet.tokens.TemplateToken;
import com.intellij.codeInsight.template.emmet.tokens.TextToken;
import com.intellij.codeInsight.template.emmet.tokens.ZenCodingToken;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.diagnostic.AttachmentFactory;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;


/**
 * @author Eugene.Kudelevsky
 */
public class ZenCodingTemplate extends CustomLiveTemplateBase {
  public static final char MARKER = '\0';
  private static final String EMMET_RECENT_WRAP_ABBREVIATIONS_KEY = "emmet.recent.wrap.abbreviations";
  private static final String EMMET_LAST_WRAP_ABBREVIATIONS_KEY = "emmet.last.wrap.abbreviations";
  private static final Logger LOG = Logger.getInstance(ZenCodingTemplate.class);

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

  @Override
  public void expand(@NotNull String key, @NotNull CustomTemplateCallback callback) {
    ZenCodingGenerator defaultGenerator = findApplicableDefaultGenerator(callback.getContext(), false);
    assert defaultGenerator != null;
    expand(key, callback, null, defaultGenerator, Collections.<ZenCodingFilter>emptyList(), true, Registry.intValue("emmet.segments.limit"));
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
                            boolean expandPrimitiveAbbreviations, int segmentsLimit) {
    final ZenCodingNode node = parse(key, callback, defaultGenerator, surroundedText);
    if (node == null) {
      return;
    }
    if (surroundedText == null && node instanceof TemplateNode) {
      if (key.equals(((TemplateNode)node).getTemplateToken().getKey()) && callback.findApplicableTemplates(key).size() > 1) {
        TemplateManagerImpl templateManager = (TemplateManagerImpl)callback.getTemplateManager();
        Map<TemplateImpl, String> template2Argument = templateManager.findMatchingTemplates(callback.getFile(), callback.getEditor(), null, TemplateSettings.getInstance());
        Runnable runnable = templateManager.startNonCustomTemplates(template2Argument, callback.getEditor(), null);
        if (runnable != null) {
          runnable.run();
        }
        return;
      }
    }

    PsiElement context = callback.getContext();
    ZenCodingGenerator generator = findApplicableGenerator(node, context, false);
    List<ZenCodingFilter> filters = getFilters(node, context);
    filters.addAll(extraFilters);


    if (surroundedText == null) {
      callback.deleteTemplateKey(key);
      // commit is required. otherwise injections placed after caret will be broken
      PsiDocumentManager.getInstance(callback.getProject()).commitDocument(callback.getEditor().getDocument());
    }
    expand(node, generator, filters, surroundedText, callback, expandPrimitiveAbbreviations, segmentsLimit);
  }

  private static void expand(ZenCodingNode node,
                             ZenCodingGenerator generator,
                             List<ZenCodingFilter> filters,
                             String surroundedText,
                             CustomTemplateCallback callback, boolean expandPrimitiveAbbreviations, int segmentsLimit) {
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
    LiveTemplateBuilder builder = new LiveTemplateBuilder(segmentsLimit);
    int end = -1;
    for (int i = 0, genNodesSize = genNodes.size(); i < genNodesSize; i++) {
      GenerationNode genNode = genNodes.get(i);
      TemplateImpl template = genNode.generate(callback, generator, filters, true, segmentsLimit);
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
      if (token != null) {
        final List<Couple<String>> attributes = token.getAttribute2Value();
        final Couple<String> singleAttribute = ContainerUtil.getFirstItem(attributes);
        if (singleAttribute == null || "class".equalsIgnoreCase(singleAttribute.first) && StringUtil.isEmpty(singleAttribute.second)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void wrap(@NotNull final String selection, @NotNull final CustomTemplateCallback callback) {
    final TextFieldWithStoredHistory field = new TextFieldWithStoredHistory(EMMET_RECENT_WRAP_ABBREVIATIONS_KEY);
    final Dimension fieldPreferredSize = field.getPreferredSize();
    field.setPreferredSize(new Dimension(Math.max(220, fieldPreferredSize.width), fieldPreferredSize.height));
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
      public void keyPressed(@NotNull KeyEvent e) {
        if (!field.isPopupVisible()) {
          switch (e.getKeyCode()) {
            case KeyEvent.VK_ENTER:
              final String abbreviation = field.getText();
              if (validateTemplateKey(field, balloon, abbreviation, callback)) {
                doWrap(abbreviation, callback);
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
        field.selectText();
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
    if (generator == null) {
      int offset = callback.getEditor().getCaretModel().getOffset();
      LOG.error("Emmet is disabled for context for file " + callback.getFileType().getName() + " in offset: " + offset,
                AttachmentFactory.createAttachment(callback.getEditor().getDocument()));
      return false;
    }
    return checkTemplateKey(inputString, callback, generator);
  }

  @Override
  public boolean isApplicable(PsiFile file, int offset, boolean wrapping) {
    if (file == null) {
      return false;
    }
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

  public static void doWrap(@NotNull final String abbreviation, @NotNull final CustomTemplateCallback callback) {
    final ZenCodingGenerator defaultGenerator = findApplicableDefaultGenerator(callback.getContext(), true);
    assert defaultGenerator != null;
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().executeCommand(callback.getProject(), new Runnable() {
          @Override
          public void run() {
            callback.getEditor().getCaretModel().runForEachCaret(new CaretAction() {
              @Override
              public void perform(Caret caret) {
                String selectedText = callback.getEditor().getSelectionModel().getSelectedText();
                if (selectedText != null) {
                  String selection = selectedText.trim();
                  ZenCodingNode node = parse(abbreviation, callback, defaultGenerator, selection);
                  assert node != null;
                  PsiElement context = callback.getContext();
                  ZenCodingGenerator generator = findApplicableGenerator(node, context, true);
                  List<ZenCodingFilter> filters = getFilters(node, context);

                  EditorModificationUtil.deleteSelectedText(callback.getEditor());
                  PsiDocumentManager.getInstance(callback.getProject()).commitAllDocuments();

                  expand(node, generator, filters, selection, callback, true, Registry.intValue("emmet.segments.limit"));
                }
              }
            });
          }
        }, CodeInsightBundle.message("insert.code.template.command"), null);
      }
    });
  }

  @Override
  @NotNull
  public String getTitle() {
    return XmlBundle.message("emmet.title");
  }

  @Override
  public char getShortcut() {
    return (char)EmmetOptions.getInstance().getEmmetExpandShortcut();
  }

  @Override
  public String computeTemplateKey(@NotNull CustomTemplateCallback callback) {
    ZenCodingGenerator generator = findApplicableDefaultGenerator(callback.getContext(), false);
    if (generator == null) return null;
    return generator.computeTemplateKey(callback);
  }

  @Override
  public boolean supportsWrapping() {
    return true;
  }

  @Override
  public void addCompletions(CompletionParameters parameters, CompletionResultSet result) {
    if (!parameters.isAutoPopup()) {
      return;
    }

    PsiFile file = parameters.getPosition().getContainingFile();
    int offset = parameters.getOffset();
    Editor editor = parameters.getEditor();

    ZenCodingGenerator generator = findApplicableDefaultGenerator(CustomTemplateCallback.getContext(file, offset), false);
    if (generator != null && generator.hasCompletionItem()) {
      final Ref<TemplateImpl> generatedTemplate = new Ref<TemplateImpl>();
      final CustomTemplateCallback callback = new CustomTemplateCallback(editor, file) {
        @Override
        public void deleteTemplateKey(@NotNull String key) {
        }

        @Override
        public void startTemplate(@NotNull Template template, Map<String, String> predefinedValues, TemplateEditingListener listener) {
          if (template instanceof TemplateImpl && !((TemplateImpl)template).isDeactivated()) {
            generatedTemplate.set((TemplateImpl)template);
          }
        }
      };

      final String templatePrefix = computeTemplateKeyWithoutContextChecking(callback);

      if (templatePrefix != null) {
        List<TemplateImpl> regularTemplates = TemplateManagerImpl.listApplicableTemplates(file, offset, false);
        boolean regularTemplateWithSamePrefixExists = !ContainerUtil.filter(regularTemplates, new Condition<TemplateImpl>() {
          @Override
          public boolean value(TemplateImpl template) {
            return templatePrefix.equals(template.getKey());
          }
        }).isEmpty();
        
        CompletionResultSet resultSet = result.withPrefixMatcher(result.getPrefixMatcher().cloneWithPrefix(templatePrefix));
        resultSet.restartCompletionOnPrefixChange(StandardPatterns.string().startsWith(templatePrefix));
        if (!regularTemplateWithSamePrefixExists) {
          // exclude perfect matches with existing templates because LiveTemplateCompletionContributor handles it
          final Collection<SingleLineEmmetFilter> extraFilters = ContainerUtil.newLinkedList(new SingleLineEmmetFilter());
          expand(templatePrefix, callback, null, generator, extraFilters, false, 0);
          if (!generatedTemplate.isNull()) {
            final TemplateImpl template = generatedTemplate.get();
            template.setKey(templatePrefix);
            template.setDescription(template.getTemplateText());

            resultSet.addElement(new CustomLiveTemplateLookupElement(this, template.getKey(), template.getKey(), template.getDescription(), 
              !LiveTemplateCompletionContributor.shouldShowAllTemplates(), true));
          }
        }
      }
    }
  }
}
