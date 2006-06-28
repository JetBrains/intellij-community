package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.TooltipGroup;
import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.find.FindProgressIndicator;
import com.intellij.find.FindSettings;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.plugin.replace.ui.NavigateSearchResultsDialog;
import com.intellij.structuralsearch.plugin.ui.actions.DoSearchAction;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.usages.*;
import com.intellij.util.Alarm;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collection;

// Class to show the user the request for search

@SuppressWarnings({"RefusedBequest", "AssignmentToStaticFieldFromInstanceMethod"})
public class SearchDialog extends DialogWrapper implements ConfigurationCreator {
  protected SearchContext searchContext;

  // text for search
  protected Editor searchCriteriaEdit;

  // options of search scope
  private ScopeChooserCombo myScopeChooserCombo;

  private JCheckBox searchIncrementally;
  private JCheckBox recursiveMatching;
  //private JCheckBox distinctResults;
  private JCheckBox caseSensitiveMatch;

  private JCheckBox maxMatchesSwitch;
  private JTextField maxMatches;
  private JComboBox fileTypes;
  private JLabel status;
  private JLabel statusText;

  protected SearchModel model;
  private JCheckBox openInNewTab;
  private Alarm myAlarm;

  public static final String USER_DEFINED = SSRBundle.message("new.template.defaultname");
  protected final ExistingTemplatesComponent existingTemplatesComponent;

  private boolean useLastConfiguration;

  private static boolean ourOpenInNewTab;
  private static boolean ourUseMaxCount;
  @NonNls private static String ourFileType = "java";
  private static final TooltipGroup OUR_TOOLTIP_GROUP = new TooltipGroup("ssr.error.tooltip", 100);
  private final boolean myShowScopePanel;
  private final boolean myRunFindActionOnClose;

  public SearchDialog(SearchContext searchContext) {
    this(searchContext, true, true);
  }

  public SearchDialog(SearchContext searchContext, boolean showScope, boolean runFindActionOnClose) {
    super(searchContext.getProject(), true);
    myShowScopePanel = showScope;
    myRunFindActionOnClose = runFindActionOnClose;
    this.searchContext = (SearchContext)searchContext.clone();
    setTitle(getDefaultTitle());

    if (runFindActionOnClose) {
      setOKButtonText(SSRBundle.message("ssdialog.find.botton"));
      setOKButtonIcon(IconLoader.getIcon("/actions/find.png"));
      getOKAction().putValue(Action.MNEMONIC_KEY, new Integer('F'));
    }

    existingTemplatesComponent = ExistingTemplatesComponent.getInstance(this.searchContext.getProject());
    model = new SearchModel(createConfiguration());
    myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

    init();
  }

  protected UsageViewContext createUsageViewContext(Configuration configuration) {
    return new UsageViewContext(searchContext, configuration);
  }

  public void setUseLastConfiguration(boolean useLastConfiguration) {
    this.useLastConfiguration = useLastConfiguration;
  }

  protected boolean isChanged(Configuration configuration) {
    if (configuration.getMatchOptions().getSearchPattern() == null) return false;
    return !searchCriteriaEdit.getDocument().getText().equals(configuration.getMatchOptions().getSearchPattern());
  }

  public void setSearchPattern(final Configuration config) {
    model.setShadowConfig(config);
    setValuesFromConfig(config);
    initiateValidation();
  }

  protected Editor createEditor(final SearchContext searchContext) {
    PsiElement element = searchContext.getFile();

    if (element != null && !useLastConfiguration) {
      final Editor selectedEditor = FileEditorManager.getInstance(searchContext.getProject()).getSelectedTextEditor();

      if (selectedEditor != null) {
        int caretPosition = selectedEditor.getCaretModel().getOffset();
        PsiElement positionedElement = searchContext.getFile().findElementAt(caretPosition);

        if (positionedElement == null) {
          positionedElement = searchContext.getFile().findElementAt(caretPosition + 1);
        }

        if (positionedElement != null) {
          element = PsiTreeUtil.getParentOfType(
            positionedElement,
            PsiClass.class, PsiCodeBlock.class
          );
        }
      }
    }

    final PsiManager psimanager = PsiManager.getInstance(searchContext.getProject());
    PsiCodeFragment file = psimanager.getElementFactory().createCodeBlockCodeFragment("", element, true);
    Document doc = PsiDocumentManager.getInstance(searchContext.getProject()).getDocument(file);
    DaemonCodeAnalyzer.getInstance(searchContext.getProject()).setHighlightingEnabled(file, false);
    Editor editor = UIUtil.createEditor(doc, searchContext.getProject(), true, true);

    editor.getContentComponent().addKeyListener(
      new KeyAdapter() {
        public void keyTyped(KeyEvent e) {
          initiateValidation();
        }
      }
    );
    return editor;
  }

  private void initiateValidation() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {

      public void run() {
        try {
          SwingUtilities.invokeAndWait(
            new Runnable() {
              public void run() {
                ApplicationManager.getApplication().runWriteAction(
                  new Runnable() {
                    public void run() {
                      if (!doValidate()) {
                        getOKAction().setEnabled(false);
                      } else {
                        getOKAction().setEnabled(true);
                        reportMessage(null,null);
                      }
                    }
                  }
                );
              }
            }
          );
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }, 500);
  }

  protected void buildOptions(JPanel searchOptions) {
    searchIncrementally = new JCheckBox(SSRBundle.message("find.with.prompt.checkbox"), false);

    if (isSearchOnDemandEnabled()) {
      searchOptions.add(
        UIUtil.createOptionLine(searchIncrementally)
      );
    }

    recursiveMatching = new JCheckBox(SSRBundle.message("recursive.matching.checkbox"), true);

    if (isRecursiveSearchEnabled()) {
      searchOptions.add(
        UIUtil.createOptionLine(recursiveMatching)
      );
    }

    //searchOptions.add(
    //  UIUtil.createOptionLine(
    //    distinctResults = new JCheckBox("Distinct results",false)
    //  )
    //);
    //
    //distinctResults.setMnemonic('D');

    caseSensitiveMatch = new JCheckBox(SSRBundle.message("case.sensitive.checkbox"), true);

    searchOptions.add(UIUtil.createOptionLine(caseSensitiveMatch));

    searchOptions.add(
      UIUtil.createOptionLine(
        new JComponent[]{
          maxMatchesSwitch = new JCheckBox(SSRBundle.message("maximum.matches.checkbox"), false),
          maxMatches = new JTextField(
            Integer.toString(
              MatchOptions.DEFAULT_MAX_MATCHES_COUNT
            ),
            3
          ),
          (JComponent)Box.createHorizontalGlue()
        }
      )
    );

    maxMatches.setMaximumSize(new Dimension(50, 25));

    //noinspection HardCodedStringLiteral
    fileTypes = new JComboBox(new String[]{"java", "xml", "html"});
    if (ourSupportDifferentFileTypes) {
      final JLabel jLabel = new JLabel(SSRBundle.message("search.dialog.file.type.label"));
      searchOptions.add(
        UIUtil.createOptionLine(
          new JComponent[]{
            jLabel,
            fileTypes,
            (JComponent)Box.createHorizontalGlue()
          }
        )
      );

      jLabel.setLabelFor(fileTypes);
    }

    final PsiFile file = searchContext.getFile();
    if (file != null) {
      if (file.getFileType() == StdFileTypes.HTML || file.getFileType() == StdFileTypes.JSP) {
        ourFileType = "html";
      }
      else if (file.getFileType() == StdFileTypes.XHTML || file.getFileType() == StdFileTypes.JSPX) {
        ourFileType = "xml";
      }
      else {
        ourFileType = "java";
      }
    }

    fileTypes.setSelectedItem(ourFileType);
  }

  protected boolean isRecursiveSearchEnabled() {
    return true;
  }

  public void setValuesFromConfig(Configuration configuration) {
    //searchCriteriaEdit.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, configuration);

    setDialogTitle(configuration);
    final MatchOptions matchOptions = configuration.getMatchOptions();

    UIUtil.setContent(
      searchCriteriaEdit,
      matchOptions.getSearchPattern(),
      0,
      searchCriteriaEdit.getDocument().getTextLength(),
      searchContext.getProject()
    );

    model.getConfig().getMatchOptions().setSearchPattern(
      matchOptions.getSearchPattern()
    );

    recursiveMatching.setSelected(
      isRecursiveSearchEnabled() && matchOptions.isRecursiveSearch()
    );

    caseSensitiveMatch.setSelected(
      matchOptions.isCaseSensitiveMatch()
    );
    //distinctResults.setSelected(matchOptions.isDistinct());

    if (matchOptions.getMaxMatchesCount() != Integer.MAX_VALUE) {
      maxMatches.setText(String.valueOf(matchOptions.getMaxMatchesCount()));
    }

    model.getConfig().getMatchOptions().clearVariableConstraints();
    if (matchOptions.hasVariableConstraints()) {
      for (Iterator<String> i = matchOptions.getVariableConstraintNames(); i.hasNext();) {
        final MatchVariableConstraint constraint = (MatchVariableConstraint)matchOptions.getVariableConstraint(i.next()).clone();
        model.getConfig().getMatchOptions().addVariableConstraint(constraint);
        if (isReplaceDialog()) constraint.setPartOfSearchResults(false);
      }
    }

    searchIncrementally.setSelected(configuration.isSearchOnDemand());
    fileTypes.setSelectedItem(configuration.getMatchOptions().getFileType().getName().toLowerCase());
  }

  private void setDialogTitle(final Configuration configuration) {
    setTitle(getDefaultTitle() + " - " + configuration.getName());
  }

  public Configuration createConfiguration() {
    SearchConfiguration configuration = new SearchConfiguration();
    configuration.setName(USER_DEFINED);
    return configuration;
  }

  protected void addOrReplaceSelection(final String selection) {
    addOrReplaceSelectionForEditor(selection, searchCriteriaEdit);
  }

  protected final void addOrReplaceSelectionForEditor(final String selection, Editor editor) {
    UIUtil.setContent(editor, selection, 0, -1, searchContext.getProject());
    editor.getSelectionModel().setSelection(
      0,
      selection.length()
    );
  }

  protected void runAction(final Configuration config, final SearchContext searchContext) {
    if (searchIncrementally.isSelected()) {
      NavigateSearchResultsDialog resultsDialog = createResultsNavigator(searchContext, config);

      DoSearchAction.execute(searchContext.getProject(), resultsDialog, config);
    }
    else {
      createUsageView(searchContext, config);
    }
  }

  protected NavigateSearchResultsDialog createResultsNavigator(final SearchContext searchContext, Configuration config) {
    return new NavigateSearchResultsDialog(searchContext.getProject(), false);
  }

  protected void createUsageView(final SearchContext searchContext, final Configuration config) {
    UsageViewManager manager = searchContext.getProject().getComponent(UsageViewManager.class);

    final UsageViewContext context = createUsageViewContext(config);
    final UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setOpenInNewTab(openInNewTab.isSelected());
    presentation.setScopeText(config.getMatchOptions().getScope().getDisplayName());
    context.configure(presentation);

    final FindUsagesProcessPresentation processPresentation = new FindUsagesProcessPresentation();
    processPresentation.setShowNotFoundMessage(true);
    processPresentation.setShowPanelIfOnlyOneUsage(true);

    processPresentation.setProgressIndicatorFactory(
      new Factory<ProgressIndicator>() {
        public ProgressIndicator create() {
          return new FindProgressIndicator(searchContext.getProject(), presentation.getScopeText()) {
            public void cancel() {
              context.getCommand().stopAsyncSearch();
              super.cancel();
            }
          };
        }
      }
    );

    processPresentation.addNotFoundAction(
      new AbstractAction(SSRBundle.message("edit.query.button.description")) {
        {
          putValue(FindUsagesProcessPresentation.NAME_WITH_MNEMONIC_KEY, SSRBundle.message("edit.query.button"));
        }

        public void actionPerformed(ActionEvent e) {
          UIUtil.invokeAction(config, searchContext);
        }
      }
    );

    manager.searchAndShowUsages(
      new UsageTarget[]{
        context.getTarget()
      },
      new Factory<UsageSearcher>() {
        public UsageSearcher create() {
          return new UsageSearcher() {
            public void generate(final Processor<Usage> processor) {
              context.getCommand().findUsages(processor);
            }
          };
        }
      },
      processPresentation,
      presentation,
      new UsageViewManager.UsageViewStateListener() {
        public void usageViewCreated(UsageView usageView) {
          context.setUsageView(usageView);
          configureActions(context);
        }

        public void findingUsagesFinished(final UsageView usageView) {
        }
      }
    );
  }

  protected void configureActions(final UsageViewContext context) {
    context.getUsageView().addButtonToLowerPane(
      new Runnable() {
        public void run() {
          context.getUsageView().close();
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              UIUtil.invokeAction(context.getConfiguration(), searchContext);
            }
          });
        }
      },
      SSRBundle.message("edit.query.button.description")
    );
  }

  protected String getDefaultTitle() {
    return SSRBundle.message("structural.search.title");
  }

  protected JComponent createEditorContent() {
    JPanel result = new JPanel(new BorderLayout());

    result.add(BorderLayout.NORTH, new JLabel(SSRBundle.message("search.template")));
    searchCriteriaEdit = createEditor(searchContext);
    result.add(BorderLayout.CENTER, searchCriteriaEdit.getComponent());
    result.setMinimumSize(new Dimension(150, 100));

    return result;
  }

  private static boolean ourSupportDifferentFileTypes = true;

  protected int getRowsCount() {
    return ourSupportDifferentFileTypes ? 4 : 3;
  }

  protected JComponent createCenterPanel() {

    JPanel editorPanel = new JPanel(new BorderLayout());
    editorPanel.add(BorderLayout.CENTER, createEditorContent());
    editorPanel.add(BorderLayout.SOUTH, Box.createVerticalStrut(8));
    JComponent centerPanel = new JPanel(new BorderLayout());
    {
      JPanel panel = new JPanel(new BorderLayout());
      panel.add(BorderLayout.CENTER, editorPanel);
      panel.add(BorderLayout.SOUTH, createTemplateManagementButtons());
      centerPanel.add(BorderLayout.CENTER, panel);
    }

    JPanel optionsContent = new JPanel(new BorderLayout());
    centerPanel.add(BorderLayout.SOUTH, optionsContent);

    JPanel searchOptions = new JPanel();
    searchOptions.setLayout(new GridLayout(getRowsCount(), 1, 0, 0));
    searchOptions.setBorder(IdeBorderFactory.createTitledBorder(SSRBundle.message("ssdialog.options.group.border")));

    myScopeChooserCombo = new ScopeChooserCombo(
      searchContext.getProject(),
      true,
      false,
      isReplaceDialog() ? SSRBundle.message("default.replace.scope") : FindSettings.getInstance().getDefaultScopeName()
    );
    JPanel allOptions = new JPanel(new BorderLayout());
    if (myShowScopePanel) {
      JPanel scopePanel = new JPanel(new BorderLayout());
      scopePanel.add(Box.createVerticalStrut(8), BorderLayout.NORTH);
      JLabel label = new JLabel(SSRBundle.message("search.dialog.scope.label"));
      scopePanel.add(label, BorderLayout.WEST);

      scopePanel.add(myScopeChooserCombo, BorderLayout.CENTER);
      label.setLabelFor(myScopeChooserCombo.getComboBox());

      allOptions.add(
        scopePanel,
        BorderLayout.SOUTH
      );

      myScopeChooserCombo.getComboBox().addItemListener(new ItemListener() {
        public void itemStateChanged(ItemEvent e) {
          initiateValidation();
        }
      });
    }

    buildOptions(searchOptions);

    allOptions.add(searchOptions, BorderLayout.CENTER);
    optionsContent.add(allOptions, BorderLayout.CENTER);

    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    openInNewTab = new JCheckBox(SSRBundle.message("open.in.new.tab.checkbox"));
    openInNewTab.setSelected(ourOpenInNewTab);
    ToolWindow findWindow = ToolWindowManager.getInstance(searchContext.getProject()).getToolWindow(ToolWindowId.FIND);
    openInNewTab.setEnabled(findWindow != null && findWindow.isAvailable());
    panel.add(openInNewTab, BorderLayout.EAST);

    optionsContent.add(BorderLayout.SOUTH, panel);

    return centerPanel;
  }


  protected JComponent createSouthPanel() {
    final JPanel statusPanel = new JPanel(new BorderLayout());
    statusPanel.add(super.createSouthPanel(), BorderLayout.NORTH);
    statusPanel.add(statusText = new JLabel(SSRBundle.message("status.message")), BorderLayout.WEST);
    final String s = SSRBundle.message("status.mnemonic");
    if (s.length() > 0) statusText.setDisplayedMnemonic( s.charAt(0) );
    statusPanel.add(status = new JLabel(),BorderLayout.CENTER);

    return statusPanel;
  }

  private JPanel createTemplateManagementButtons() {
    JPanel panel = new JPanel(null);
    panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
    panel.add(Box.createHorizontalGlue());

    panel.add(
      createJButtonForAction(new AbstractAction() {
        {
          putValue(NAME, SSRBundle.message("save.template.text.button"));
        }

        public void actionPerformed(ActionEvent e) {
          String name = showSaveTemplateAsDialog();

          if (name != null) {
            final Project project = searchContext.getProject();
            final ConfigurationManager configurationManager = StructuralSearchPlugin.getInstance(project).getConfigurationManager();
            final Collection<Configuration> configurations = configurationManager.getConfigurations();

            if (configurations != null) {
              name = ConfigurationManager.findAppropriateName(configurations, name, project);
              if (name == null) return;
            }

            model.getConfig().setName(name);
            setValuesToConfig(model.getConfig());
            setDialogTitle(model.getConfig());

            if (model.getShadowConfig() == null ||
                model.getShadowConfig() instanceof PredefinedConfiguration) {
              existingTemplatesComponent.addConfigurationToUserTemplates(model.getConfig());
            }
            else {  // ???
              setValuesToConfig(model.getShadowConfig());
              model.getShadowConfig().setName(name);
            }
          }
        }
      })
    );

    panel.add(
      Box.createHorizontalStrut(8)
    );

    panel.add(
      createJButtonForAction(
        new AbstractAction() {
          {
            putValue(NAME, SSRBundle.message("edit.variables.button"));
          }

          public void actionPerformed(ActionEvent e) {
            SubstitutionShortInfoHandler handler = searchCriteriaEdit.getUserData(UIUtil.LISTENER_KEY);
            ArrayList<Variable> variables = new ArrayList<Variable>(handler.getVariables());

            CompletionTextField.setCurrentProject(searchContext.getProject());
            new EditVarConstraintsDialog(
              searchContext.getProject(),
              model,
              variables,
              isReplaceDialog(),
              getFileTypeByString((String)fileTypes.getSelectedItem())
            ).show();
            initiateValidation();
            CompletionTextField.setCurrentProject(null);
          }
        }
      )
    );

    panel.add(
      Box.createHorizontalStrut(8)
    );

    panel.add(
      createJButtonForAction(
        new AbstractAction() {
          {
            putValue(NAME, SSRBundle.message("history.button"));
          }

          public void actionPerformed(ActionEvent e) {
            SelectTemplateDialog dialog = new SelectTemplateDialog(searchContext.getProject(), true, isReplaceDialog());
            dialog.show();

            if (!dialog.isOK()) {
              return;
            }
            Configuration[] configurations = dialog.getSelectedConfigurations();
            if (configurations.length == 1) {
              setSearchPattern(configurations[0]);
            }
          }
        }
      )
    );

    panel.add(
      Box.createHorizontalStrut(8)
    );

    panel.add(
      createJButtonForAction(
        new AbstractAction() {
          {
            putValue(NAME, SSRBundle.message("copy.existing.template.button"));
          }

          public void actionPerformed(ActionEvent e) {
            SelectTemplateDialog dialog = new SelectTemplateDialog(searchContext.getProject(), false, isReplaceDialog());
            dialog.show();

            if (!dialog.isOK()) {
              return;
            }
            Configuration[] configurations = dialog.getSelectedConfigurations();
            if (configurations.length == 1) {
              setSearchPattern(configurations[0]);
            }
          }
        }
      )
    );

    return panel;
  }

  public final Project getProject() {
    return searchContext.getProject();
  }

  public String showSaveTemplateAsDialog() {
    return ConfigurationManager.showSaveTemplateAsDialog(
      model.getShadowConfig() != null ? model.getShadowConfig().getName() : "",
      searchContext.getProject()
    );
  }

  protected boolean isReplaceDialog() {
    return false;
  }

  public void show() {
    Configuration.setActiveCreator(this);
    searchCriteriaEdit.putUserData(
      SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY,
      model.getConfig()
    );

    maxMatchesSwitch.setSelected(ourUseMaxCount);

    if (!useLastConfiguration) {
      final Editor editor = FileEditorManager.getInstance(searchContext.getProject()).getSelectedTextEditor();
      boolean setSomeText = false;

      if (editor != null) {
        final SelectionModel selectionModel = editor.getSelectionModel();

        if (selectionModel.hasSelection()) {
          addOrReplaceSelection(selectionModel.getSelectedText());
          existingTemplatesComponent.getPatternTree().setSelectionPath(null);
          existingTemplatesComponent.getHistoryList().setSelectedIndex(-1);
          setSomeText = true;
        }
      }

      if (!setSomeText) {
        int selection = existingTemplatesComponent.getHistoryList().getSelectedIndex();
        if (selection != -1) {
          setValuesFromConfig(
            (Configuration)existingTemplatesComponent.getHistoryList().getSelectedValue()
          );
        }
      }
    }

    initiateValidation();

    super.show();
  }

  public JComponent getPreferredFocusedComponent() {
    return searchCriteriaEdit.getContentComponent();
  }

  // Performs ok action
  protected void doOKAction() {
    SearchScope selectedScope = getSelectedScope();
    if (selectedScope == null) return;

    boolean result = doValidate();
    if (!result) return;

    super.doOKAction();
    if (!myRunFindActionOnClose) return;

    if (!isReplaceDialog()) {
      FindSettings.getInstance().setDefaultScopeName(selectedScope.getDisplayName());
    }
    ourOpenInNewTab = openInNewTab.isSelected();

    try {
      if (model.getShadowConfig() != null) {
        if (model.getShadowConfig() instanceof PredefinedConfiguration) {
          model.getConfig().setName(
            model.getShadowConfig().getName()
          );
        } //else {
        //  // user template, save it
        //  setValuesToConfig(model.getShadowConfig());
        //}
      }
      existingTemplatesComponent.addConfigurationToHistory(model.getConfig());

      runAction(model.getConfig(), searchContext);
    }
    catch (MalformedPatternException ex) {
      reportMessage("this.pattern.is.malformed.message", searchCriteriaEdit, ex.getMessage());
    }
  }

  public Configuration getConfiguration() {
    return model.getConfig();
  }

  private SearchScope getSelectedScope() {
    SearchScope selectedScope = myScopeChooserCombo.getSelectedScope();
    return selectedScope;
  }

  protected boolean doValidate() {
    setValuesToConfig(model.getConfig());
    boolean result = true;

    try {
      Matcher.validate(searchContext.getProject(), model.getConfig().getMatchOptions());
    }
    catch (MalformedPatternException ex) {
      reportMessage(
        "this.pattern.is.malformed.message",
        searchCriteriaEdit,
        ex.getMessage() != null ? ex.getMessage():""
      );
      result = false;
    }
    catch (UnsupportedPatternException ex) {
      reportMessage("this.pattern.is.unsupported.message", searchCriteriaEdit, ex.getPattern());
      result = false;
    }

    //getOKAction().setEnabled(result);
    return result;
  }

  protected void reportMessage(@NonNls String messageId, Editor editor, Object... params) {
    final String message = messageId != null ? SSRBundle.message(messageId, params) : "";
    status.setText(message);
    status.setToolTipText(message);
    status.revalidate();
    statusText.setLabelFor(editor != null ? editor.getContentComponent() : null);
  }

  protected void setValuesToConfig(Configuration config) {

    MatchOptions options = config.getMatchOptions();

    options.setScope(myScopeChooserCombo.getSelectedScope());
    options.setLooseMatching(true);
    options.setRecursiveSearch(isRecursiveSearchEnabled() && recursiveMatching.isSelected());
    //options.setDistinct( distinctResults.isSelected() );

    ourUseMaxCount = maxMatchesSwitch.isSelected();

    if (maxMatchesSwitch.isSelected()) {
      try {
        options.setMaxMatchesCount(
          Integer.parseInt(maxMatches.getText())
        );
      }
      catch (NumberFormatException ex) {
        options.setMaxMatchesCount(
          MatchOptions.DEFAULT_MAX_MATCHES_COUNT
        );
      }
    }
    else {
      options.setMaxMatchesCount(Integer.MAX_VALUE);
    }

    ourFileType = (String)fileTypes.getSelectedItem();
    options.setFileType(getFileTypeByString(ourFileType));

    options.setSearchPattern(searchCriteriaEdit.getDocument().getText());
    options.setCaseSensitiveMatch(caseSensitiveMatch.isSelected());
    config.setSearchOnDemand(isSearchOnDemandEnabled() && searchIncrementally.isSelected());
  }

  protected FileType getCurrentFileType() {
    return getFileTypeByString((String)fileTypes.getSelectedItem());
  }

  private static FileType getFileTypeByString(String ourFileType) {
    //noinspection HardCodedStringLiteral
    if (ourFileType.equals("java")) {
      return StdFileTypes.JAVA;
    }
    else //noinspection HardCodedStringLiteral
      if (ourFileType.equals("html")) {
        return StdFileTypes.HTML;
      }
      else {
        return StdFileTypes.XML;
      }
  }

  protected boolean isSearchOnDemandEnabled() {
    return false;
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.structuralsearch.plugin.ui.SearchDialog";
  }

  public void dispose() {
    Configuration.setActiveCreator(null);

    // this will remove from myExcludedSet
    DaemonCodeAnalyzer.getInstance(searchContext.getProject()).setHighlightingEnabled(
      PsiDocumentManager.getInstance(searchContext.getProject()).getPsiFile(
        searchCriteriaEdit.getDocument()
      ), true
    );

    EditorFactory.getInstance().releaseEditor(searchCriteriaEdit);
    myAlarm.cancelAllRequests();

    super.dispose();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("find.structuredSearch");
  }

  public SearchContext getSearchContext() {
    return searchContext;
  }
}
