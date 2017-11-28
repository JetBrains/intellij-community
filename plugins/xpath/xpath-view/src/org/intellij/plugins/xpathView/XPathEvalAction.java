/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView;

import com.intellij.find.FindProgressIndicator;
import com.intellij.find.FindSettings;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.*;
import com.intellij.util.Processor;
import icons.XpathIcons;
import org.intellij.plugins.xpathView.eval.EvalExpressionDialog;
import org.intellij.plugins.xpathView.support.XPathSupport;
import org.intellij.plugins.xpathView.ui.InputExpressionDialog;
import org.intellij.plugins.xpathView.util.CachedVariableContext;
import org.intellij.plugins.xpathView.util.HighlighterUtil;
import org.intellij.plugins.xpathView.util.MyPsiUtil;
import org.jaxen.JaxenException;
import org.jaxen.XPath;
import org.jaxen.XPathSyntaxException;
import org.jaxen.saxpath.SAXPathException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * <p>This class implements the core action to enter, evaluate and display the results of an XPath expression.</p>
 *
 * <p>The evaluation is performed by the <a target="_blank" href="http://www.jaxen.org">Jaxen</a> XPath-engine, which allows arbitrary
 * object models to be used. The adapter class for IDEA's object model, the PSI-tree, is located in the class
 * {@link org.intellij.plugins.xpathView.support.jaxen.PsiDocumentNavigator}.</p>
 *
 * <p>The plugin can be invoked in three different ways:<ol>
 *  <li>By pressing a keystroke (default: ctrl-alt-x, e) that can be set in the keymap configuration
 *  <li>By selecting "Evaluate XPath" from the edtior popup menu
 *  <li>By clicking the icon in the toolbar (it's the icon that is associated with xml-files on windows)
 * </ol>
 *
 * <p>The result of an expression is displayed according to its type: Primitive XPath values (Strings, numbers, booleans)
 * are displayed by a message box. If the result is a node/nodelist, the corresponding nodes are highlighted in IDEA's
 * editor.</p>
 * <p>The highlighting is cleared upon each new evaluation. Additionally, the plugin registers an own handler for the
 * &lt;esc&gt; key, which also clears the highlighting.</p>
 *
 * <p>The evalutation can be performed relatively to a context node: When the option "Use node at cursor as context node"
 * is turned on, all XPath expressions are evaluted relatively to this node. This node (which can actually only be a tag
 * element), is then highlighted to give a visual indication when entering the expression. This does not affect
 * expressions that start with {@code /} or {@code //}.</p>
 *
 * <p><b>Limitations:</b></p>
 * <ul>
 *  <li>Namespaces: Although queries containing namespace-prefixes are supported, the XPath namespace-axis
 *      ({@code namespace::}) is currently unsupported.<br>
 * <li>Matching for text(): Such queries will currently also highlight whitespace <em>inside</em> a start/end tag.<br>
 *      This is due the tree-structure of the PSI. Further investigation is needed here.
 * <li>String values with string(): Whitespace handling for the string() function is far from being correctly
 *      implemented. To produce somewhat acceptable results, all whitespace inside a string is normalized.<br>
 *      <em>DON'T EXPECT THESE RESULTS TO BE THE SAME AS WITH OTHER TOOLS</em>.
 * <li>Entites references: This is a limitation for matching text() as well as for the result produced by string().
 *      The only recognized entity refences are the predefined ones for XML:<br>&nbsp;&nbsp;
 *          &amp;amp; &amp;lt; &amp;gt; &amp;quot;<br>
 *      In all other cases, the text that is returned is the text shown in the editor and does not include resolved
 *      entities. Therefore you will get no/false results when entites are involved.<br>
 *      It is currently undecided whether it makes sense to recurse into resolved entities, because there seems no
 *      reasonable way to display the result.
 * <li><b>This plugin is completely based on IDEA's PSI (Program Structure Interface)</b>. This API is not part of the
 *      current Open-API and is completely unsupported by IntelliJ. Interfaces and functionality and may be changed
 *      without any prior notice, which might break this plugin.<br>
 *      <em>Please don't bother IntelliJ staff in such a case</em>.
 * <li>Probably some others ;-)
 * </ul>
 *
 * @author Sascha Weinreuter
 */
public class XPathEvalAction extends XPathAction {

    private static final Logger LOG = Logger.getInstance("org.intellij.plugins.xpathView.XPathEvalAction");

  @Override
    protected void updateToolbar(AnActionEvent event) {
        super.updateToolbar(event);
        if (XpathIcons.Xml != null) {
            event.getPresentation().setIcon(XpathIcons.Xml);
        }
    }

    @Override
    protected boolean isEnabledAt(XmlFile xmlFile, int offset) {
        return true;
    }

    @Override
    public void actionPerformed(AnActionEvent event) {
        final Project project = event.getProject();
        if (project == null) {
            // no active project
            LOG.debug("No project");
            return;
        }

        Editor editor = CommonDataKeys.EDITOR.getData(event.getDataContext());
        if (editor == null) {
            FileEditorManager fem = FileEditorManager.getInstance(project);
            editor = fem.getSelectedTextEditor();
        }
        if (editor == null) {
            // no editor available
            LOG.debug("No editor");
            return;
        }

        // do we have an xml file?
        final PsiDocumentManager pdm = PsiDocumentManager.getInstance(project);
        final PsiFile psiFile = pdm.getPsiFile(editor.getDocument());
        if (!(psiFile instanceof XmlFile)) {
            // not xml
            LOG.debug("No XML-File: " + psiFile);
            return;
        }

        // make sure PSI is in sync with document
        pdm.commitDocument(editor.getDocument());

        execute(editor);
    }

    private void execute(Editor editor) {
        final Project project = editor.getProject();
        final PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return;
        }

        InputExpressionDialog.Context input;
        XmlElement contextNode = null;
        final Config cfg = XPathAppComponent.getInstance().getConfig();
        do {
            RangeHighlighter contextHighlighter = null;
            if (cfg.isUseContextAtCursor()) {
                // find out current context node
                contextNode = MyPsiUtil.findContextNode(psiFile, editor);
                if (contextNode != null) {
                    contextHighlighter = HighlighterUtil.highlightNode(editor, contextNode, cfg.getContextAttributes(), cfg);
                }
            }
            if (contextNode == null) {
                // in XPath data model, / is the document itself, including comments, PIs and the root element
                contextNode = ((XmlFile)psiFile).getDocument();
                if (contextNode == null) {
                  FileViewProvider fileViewProvider = psiFile.getViewProvider();
                  if (fileViewProvider instanceof TemplateLanguageFileViewProvider) {
                    Language dataLanguage = ((TemplateLanguageFileViewProvider)fileViewProvider).getTemplateDataLanguage();
                    PsiFile templateDataFile = fileViewProvider.getPsi(dataLanguage);
                    if (templateDataFile instanceof XmlFile) contextNode = ((XmlFile)templateDataFile).getDocument();
                  }
                }
            }

            input = inputXPathExpression(project, contextNode);
            if (contextHighlighter != null) {
                contextHighlighter.dispose();
            }
            if (input == null) {
                return;
            }

            HighlighterUtil.clearHighlighters(editor);
        } while (contextNode != null && evaluateExpression(input, contextNode, editor, cfg));
    }

    private boolean evaluateExpression(EvalExpressionDialog.Context context, XmlElement contextNode, Editor editor, Config cfg) {
        final Project project = editor.getProject();

        try {
            final XPathSupport support = XPathSupport.getInstance();
            final XPath xpath = support.createXPath((XmlFile)contextNode.getContainingFile(), context.input.expression, context.input.namespaces);

            xpath.setVariableContext(new CachedVariableContext(context.input.variables, xpath, contextNode));

            // evaluate the expression on the whole document
            final Object result = xpath.evaluate(contextNode);
            LOG.debug("result = " + result);
            LOG.assertTrue(result != null, "null result?");

            if (result instanceof List<?>) {
                final List<?> list = (List<?>)result;
                if (!list.isEmpty()) {
                    if (cfg.HIGHLIGHT_RESULTS) {
                        highlightResult(contextNode, editor, list);
                    }
                    if (cfg.SHOW_USAGE_VIEW) {
                        showUsageView(editor, xpath, contextNode, list);
                    }
                    if (!cfg.SHOW_USAGE_VIEW && !cfg.HIGHLIGHT_RESULTS) {
                        final String s = StringUtil.pluralize("match", list.size());
                        Messages.showInfoMessage(project, "Expression produced " + list.size() + " " + s, "XPath Result");
                    }
                } else {
                    return Messages.showOkCancelDialog(project, "Sorry, your expression did not return any result", "XPath Result",
                                                       "OK", "Edit Expression", Messages.getInformationIcon()) != Messages.OK;
                }
            } else if (result instanceof String) {
                Messages.showMessageDialog("'" + result.toString() + "'", "XPath result (String)", Messages.getInformationIcon());
            } else if (result instanceof Number) {
                Messages.showMessageDialog(result.toString(), "XPath result (Number)", Messages.getInformationIcon());
            } else if (result instanceof Boolean) {
                Messages.showMessageDialog(result.toString(), "XPath result (Boolean)", Messages.getInformationIcon());
            } else {
              LOG.error("Unknown XPath result: " + result);
            }
        } catch (XPathSyntaxException e) {
            LOG.debug(e);
            // TODO: Better layout of the error message with non-fixed size fonts
            return Messages.showOkCancelDialog(project, e.getMultilineMessage(), "XPath syntax error", "Edit Expression", "Cancel", Messages.getErrorIcon()) == Messages.OK;
        } catch (SAXPathException e) {
            LOG.debug(e);
            Messages.showMessageDialog(project, e.getMessage(), "XPath error", Messages.getErrorIcon());
        }
        return false;
    }

    private void showUsageView(final Editor editor, final XPath xPath, final XmlElement contextNode, final List<?> result) {
        final Project project = editor.getProject();

        //noinspection unchecked
        final List<?> _result = new ArrayList(result);
        final Factory<UsageSearcher> searcherFactory = () -> new MyUsageSearcher(_result, xPath, contextNode);
        final MyUsageTarget usageTarget = new MyUsageTarget(xPath.toString(), contextNode);

        showUsageView(project, usageTarget, searcherFactory, new EditExpressionAction() {
            final Config config = XPathAppComponent.getInstance().getConfig();

            @Override
            protected void execute() {
                XPathEvalAction.this.execute(editor);
            }
        });
    }

    public static void showUsageView(@NotNull final Project project, MyUsageTarget usageTarget, Factory<UsageSearcher> searcherFactory, final EditExpressionAction editAction) {
        final UsageViewPresentation presentation = new UsageViewPresentation();
        presentation.setTargetsNodeText("XPath Expression");
        presentation.setCodeUsages(false);
        presentation.setCodeUsagesString("Found Matches");
        presentation.setNonCodeUsagesString("Result");
        presentation.setUsagesString("XPath Result");
        presentation.setUsagesWord("match");
        final ItemPresentation targetPresentation = usageTarget.getPresentation();
        if (targetPresentation != null) {
          presentation
            .setTabText(StringUtil.shortenTextWithEllipsis("XPath '" + targetPresentation.getPresentableText() + '\'', 60, 0, true));
        }
        else {
          presentation.setTabText("XPath");
        }
        presentation.setScopeText("XML Files");

        presentation.setOpenInNewTab(FindSettings.getInstance().isShowResultsInSeparateView());

        final FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation(presentation);
        processPresentation.setProgressIndicatorFactory(() -> new FindProgressIndicator(project, "XML Document(s)"));
        processPresentation.setShowPanelIfOnlyOneUsage(true);
        processPresentation.setShowNotFoundMessage(true);
        final UsageTarget[] usageTargets = { usageTarget };

        UsageViewManager.getInstance(project).searchAndShowUsages(
                usageTargets,
                searcherFactory,
                processPresentation,
                presentation,
                new UsageViewManager.UsageViewStateListener() {
                    @Override
                    public void usageViewCreated(@NotNull UsageView usageView) {
                        usageView.addButtonToLowerPane(editAction, "&Edit Expression");
                    }

                    @Override
                    public void findingUsagesFinished(UsageView usageView) {
                    }
                });
    }

    /**
     * Opens an input box to input an XPath expression. The box will have a history dropdown from which
     * previously entered expressions can be selected.
     * @return The expression or {@code null} if the user hits the cancel button
     * @param project The project to take the history from
     */
    @Nullable
    private EvalExpressionDialog.Context inputXPathExpression(final Project project, XmlElement contextNode) {
        final XPathProjectComponent pc = XPathProjectComponent.getInstance(project);
        LOG.assertTrue(pc != null);

        // get expression history from project component
        final HistoryElement[] history = pc.getHistory();

        final EvalExpressionDialog dialog = new EvalExpressionDialog(project, XPathAppComponent.getInstance().getConfig(), history);
        if (!dialog.show(contextNode)) {
            // cancel
            LOG.debug("Input canceled");
            return null;
        }

        final InputExpressionDialog.Context context = dialog.getContext();
        LOG.debug("expression = " + context.input.expression);

        pc.addHistory(context.input);

        return context;
    }

    /**
     * <p>Process the result of an XPath query.</p>
     * <p>If the result is a {@code java.util.List} object, iterate over all elements and
     * add a highlighter object in the editor if the element is of type {@code PsiElement}.
     * <p>If the result is a primitive value (String, Number, Boolean) a message box displaying
     * the value will be displayed. </p>
     *
     * @param editor The editor object to apply the highlighting to
     */
    private void highlightResult(XmlElement contextNode, @NotNull final Editor editor, final List<?> list) {

        final Config cfg = XPathAppComponent.getInstance().getConfig();
        int lowestOffset = Integer.MAX_VALUE;

        for (final Object o : list) {
            LOG.assertTrue(o != null, "null element?");

            if (o instanceof PsiElement) {
                final PsiElement element = (PsiElement)o;

                if (element.getContainingFile() == contextNode.getContainingFile()) {
                    lowestOffset = highlightElement(editor, element, cfg, lowestOffset);
                }
            } else {
                LOG.info("Don't know what to do with " + o + " in a list context");
            }
            LOG.debug("o = " + o);
        }

        if (cfg.isScrollToFirst() && lowestOffset != Integer.MAX_VALUE) {
            editor.getScrollingModel().scrollTo(editor.offsetToLogicalPosition(lowestOffset), ScrollType.MAKE_VISIBLE);
            editor.getCaretModel().moveToOffset(lowestOffset);
        }

        SwingUtilities.invokeLater(() -> {
            final StatusBar statusBar = WindowManager.getInstance().getStatusBar(editor.getProject());
            final String s = StringUtil.pluralize("match", list.size());
            statusBar.setInfo(list.size() + " XPath " + s + " found (press Escape to remove the highlighting)");
        });
    }

    private static int highlightElement(Editor editor, PsiElement element, Config cfg, int offset) {
        final RangeHighlighter highlighter = HighlighterUtil.highlightNode(editor, element, cfg.getAttributes(), cfg);
        HighlighterUtil.addHighlighter(editor, highlighter);

        return Math.min(highlighter.getStartOffset(), offset);
    }

    public static class MyUsageTarget implements UsageTarget {
        private final ItemPresentation myItemPresentation;
        private final XmlElement myContextNode;

        public MyUsageTarget(String expression, XmlElement contextNode) {
            myContextNode = contextNode;
            myItemPresentation = new PresentationData(expression, null, null, null);
        }

        @Override
        public void findUsages() {
            throw new IllegalArgumentException();
        }

        @Override
        public void findUsagesInEditor(@NotNull FileEditor editor) {
            throw new IllegalArgumentException();
        }

      @Override
      public void highlightUsages(@NotNull PsiFile file, @NotNull Editor editor, boolean clearHighlights) {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean isValid() {
            // re-run will become unavailable if the context node is invalid
            return myContextNode == null || myContextNode.isValid();
        }

        @Override
        public boolean isReadOnly() {
            return true;
        }

        @Override
        @Nullable
        public VirtualFile[] getFiles() {
            return null;
        }

        @Override
        public void update() {
        }

        @Override
        public String getName() {
            return "Expression";
        }

        @Override
        public ItemPresentation getPresentation() {
            return myItemPresentation;
        }

        @Override
        public void navigate(boolean requestFocus) {
        }

        @Override
        public boolean canNavigate() {
            return false;
        }

        @Override
        public boolean canNavigateToSource() {
            return false;
        }
    }

    private static class MyUsageSearcher implements UsageSearcher {
        private final List<?> myResult;
        private final XPath myXPath;
        private final XmlElement myContextNode;

        public MyUsageSearcher(List<?> result, XPath xPath, XmlElement contextNode) {
            myResult = result;
            myXPath = xPath;
            myContextNode = contextNode;
        }

        @Override
        public void generate(@NotNull final Processor<Usage> processor) {
            Runnable runnable = () -> {
                final List<?> list;
                if (myResult.isEmpty()) {
                    try {
                        list = (List<?>)myXPath.selectNodes(myContextNode);
                    } catch (JaxenException e) {
                        LOG.debug(e);
                        Messages.showMessageDialog(myContextNode.getProject(), e.getMessage(), "XPath error", Messages.getErrorIcon());
                        return;
                    }
                } else {
                    list = myResult;
                }

                final int size = list.size();
                final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
                indicator.setText("Collecting matches...");

                Collections.sort(list, (Comparator)(o1, o2) -> {
                    indicator.checkCanceled();
                    if (o1 instanceof PsiElement && o2 instanceof PsiElement) {
                        return ((PsiElement)o1).getTextRange().getStartOffset() - ((PsiElement)o2).getTextRange().getStartOffset();
                    } else {
                        return String.valueOf(o1).compareTo(String.valueOf(o2));
                    }
                });
                for (int i = 0; i < size; i++) {
                    indicator.checkCanceled();
                    Object o = list.get(i);
                    if (o instanceof PsiElement) {
                        final PsiElement element = (PsiElement)o;
                        processor.process(new UsageInfo2UsageAdapter(new UsageInfo(element)));
                        indicator.setText2(element.getContainingFile().getName());
                    }
                    indicator.setFraction(i / (double)size);
                }
                list.clear();
            };
            ApplicationManager.getApplication().runReadAction(runnable);
        }
    }

    public abstract static class EditExpressionAction implements Runnable {
        @Override
        public void run() {
          execute();
        }

        protected abstract void execute();
    }
}
