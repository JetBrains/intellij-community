package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.find.FindProgressIndicator;
import com.intellij.find.FindSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.plugin.StructuralSearchPlugin;
import com.intellij.structuralsearch.plugin.replace.ui.NavigateSearchResultsDialog;
import com.intellij.structuralsearch.plugin.ui.actions.DoSearchAction;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.usages.*;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

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
  private JComboBox contexts;
  private JComboBox dialects;
  private JLabel status;
  private JLabel statusText;

  protected SearchModel model;
  private JCheckBox openInNewTab;
  private Alarm myAlarm;

  public static final String USER_DEFINED = SSRBundle.message("new.template.defaultname");
  protected final ExistingTemplatesComponent existingTemplatesComponent;

  private static final String DEFAULT_FILE_TYPE_SEARCH_VARIANT = StructuralSearchProfile.getTypeName(StructuralSearchUtil.DEFAULT_FILE_TYPE);

  private boolean useLastConfiguration;

  private static boolean ourOpenInNewTab;
  private static boolean ourUseMaxCount;

  @NonNls private static String ourFtSearchVariant = DEFAULT_FILE_TYPE_SEARCH_VARIANT;
  private static Language ourDialect = null;
  private static String ourContext = null;

  private final boolean myShowScopePanel;
  private final boolean myRunFindActionOnClose;
  private boolean myDoingOkAction;

  public SearchDialog(SearchContext searchContext) {
    this(searchContext, true, true);
  }

  public SearchDialog(SearchContext searchContext, boolean showScope, boolean runFindActionOnClose) {
    super(searchContext.getProject(), true);

    if (showScope) setModal(false);
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
    PsiCodeFragment file = JavaPsiFacade.getInstance(psimanager.getProject()).getElementFactory().createCodeBlockCodeFragment("", element, true);
    Document doc = PsiDocumentManager.getInstance(searchContext.getProject()).getDocument(file);
    DaemonCodeAnalyzer.getInstance(searchContext.getProject()).setHighlightingEnabled(file, false);
    Editor editor = UIUtil.createEditor(doc, searchContext.getProject(), true, true);

    editor.getDocument().addDocumentListener(new DocumentListener() {
      public void beforeDocumentChange(final DocumentEvent event) {}

      public void documentChanged(final DocumentEvent event) {
        initiateValidation();
      }
    });

    return editor;
  }

  private void initiateValidation() {
    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new Runnable() {

      public void run() {
        try {
          GuiUtils.invokeAndWait(new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  if (!isValid()) {
                    getOKAction().setEnabled(false);
                  }
                  else {
                    getOKAction().setEnabled(true);
                    reportMessage(null, null);
                  }
                }
              });
            }
          });
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
    Set<String> typeNames = new HashSet<String>();

    for (FileType fileType : StructuralSearchUtil.getSuitableFileTypes()) {
      if (StructuralSearchUtil.getProfileByFileType(fileType) != null) {
        typeNames.add(StructuralSearchProfile.getTypeName(fileType));
      }
    }

    fileTypes = new JComboBox(ArrayUtil.toStringArray(typeNames));
    fileTypes.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateDialectsAndContexts();
      }
    });

    contexts = new JComboBox(new DefaultComboBoxModel());
    contexts.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
    });

    dialects = new JComboBox(new DefaultComboBoxModel());
    dialects.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value == null) {
          value = "<no dialect>";
        }
        else if (value instanceof Language) {
          value = ((Language)value).getDisplayName();
        }
        return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      }
    });

    final JLabel jLabel = new JLabel(SSRBundle.message("search.dialog.file.type.label"));
    final JLabel jLabel2 = new JLabel(SSRBundle.message("search.dialog.context.label"));
    final JLabel jLabel3 = new JLabel(SSRBundle.message("search.dialog.file.dialect.label"));
    searchOptions.add(
      UIUtil.createOptionLine(
        new JComponent[]{
          (JComponent)Box.createHorizontalStrut(8),
          jLabel,
          fileTypes,
          (JComponent)Box.createHorizontalStrut(8),
          jLabel2,
          contexts,
          (JComponent)Box.createHorizontalStrut(8),
          jLabel3,
          dialects,
          new Box.Filler(new Dimension(0, 0), new Dimension(Short.MAX_VALUE, 0), new Dimension(Short.MAX_VALUE, 0))
        }
      )
    );

    jLabel.setLabelFor(fileTypes);

    detectFileTypeAndDialect();

    fileTypes.setSelectedItem(ourFtSearchVariant);
    fileTypes.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED) initiateValidation();
      }
    });

    dialects.setSelectedItem(ourDialect);
    contexts.setSelectedItem(ourContext);

    updateDialectsAndContexts();
  }

  private void updateDialectsAndContexts() {
    final Object item = fileTypes.getSelectedItem();
    final FileType fileType = getFileTypeByName((String)item);
    if (fileType instanceof LanguageFileType) {
      Language language = ((LanguageFileType)fileType).getLanguage();
      Language[] languageDialects = LanguageUtil.getLanguageDialects(language);
      Language[] variants = new Language[languageDialects.length + 1];
      variants[0] = null;
      System.arraycopy(languageDialects, 0, variants, 1, languageDialects.length);
      dialects.setModel(new DefaultComboBoxModel(variants));
    }

    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(fileType);

    if (profile instanceof StructuralSearchProfileBase) {
      final String[] contextNames = ((StructuralSearchProfileBase)profile).getContextNames();
      if (contextNames.length > 0) {
        contexts.setModel(new DefaultComboBoxModel(contextNames));
        contexts.setSelectedItem(contextNames[0]);
        contexts.setEnabled(true);
        return;
      }
    }
    contexts.setSelectedItem(null);
    contexts.setEnabled(false);
  }

  private void detectFileTypeAndDialect() {
    final PsiFile file = searchContext.getFile();
    if (file != null) {
      PsiElement context = null;

      if (searchContext.getEditor() != null) {
        context = file.findElementAt(searchContext.getEditor().getCaretModel().getOffset());
        if (context != null) {
          context = context.getParent();
        }
      }
      if (context == null) {
        context = file;
      }

      FileType detectedFileType = null;

      StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(context);
      if (profile != null) {
        FileType fileType = profile.detectFileType(context);
        if (fileType != null) {
          detectedFileType = fileType;
        }
      }

      if (detectedFileType == null) {
        for (FileType fileType : StructuralSearchUtil.getSuitableFileTypes()) {
          if (fileType instanceof LanguageFileType && ((LanguageFileType)fileType).getLanguage().equals(context.getLanguage())) {
            detectedFileType = fileType;
            break;
          }
        }
      }

      ourFtSearchVariant = detectedFileType != null ?
                           StructuralSearchProfile.getTypeName(detectedFileType) :
                           DEFAULT_FILE_TYPE_SEARCH_VARIANT;

      // todo: detect dialect

      /*if (file.getLanguage() == StdLanguages.HTML ||
          (file.getFileType() == StdFileTypes.JSP &&
           contextLanguage == StdLanguages.HTML
          )
        ) {
        ourFileType = "html";
      }
      else if (file.getLanguage() == StdLanguages.XHTML ||
               (file.getFileType() == StdFileTypes.JSPX &&
                contextLanguage == StdLanguages.HTML
               )) {
        ourFileType = "xml";
      }
      else {
        ourFileType = DEFAULT_TYPE_NAME;
      }*/
    }
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
    MatchOptions options = configuration.getMatchOptions();
    StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(options.getFileType());
    assert profile != null;
    fileTypes.setSelectedItem(StructuralSearchProfile.getTypeName(options.getFileType()));
    dialects.setSelectedItem(options.getDialect());
    if (options.getPatternContext() != null) {
      contexts.setSelectedItem(options.getPatternContext());
    }
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
    UsageViewManager manager = UsageViewManager.getInstance(searchContext.getProject());

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
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

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

  protected int getRowsCount() {
    return 4;
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
      FindSettings.getInstance().getDefaultScopeName()
    );
    Disposer.register(myDisposable, myScopeChooserCombo);
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
            EditVarConstraintsDialog.setProject(searchContext.getProject());
            new EditVarConstraintsDialog(
              searchContext.getProject(),
              model, getVariablesFromListeners(),
              isReplaceDialog(),
              getFileTypeByName((String)fileTypes.getSelectedItem())
            ).show();
            initiateValidation();
            EditVarConstraintsDialog.setProject(null);
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

  protected java.util.List<Variable> getVariablesFromListeners() {
    return getVarsFrom(searchCriteriaEdit);
  }

  protected static ArrayList<Variable> getVarsFrom(Editor searchCriteriaEdit) {
    SubstitutionShortInfoHandler handler = searchCriteriaEdit.getUserData(UIUtil.LISTENER_KEY);
    return new ArrayList<Variable>(handler.getVariables());
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

    myDoingOkAction = true;
    boolean result = isValid();
    myDoingOkAction = false;
    if (!result) return;

    myAlarm.cancelAllRequests();
    super.doOKAction();
    if (!myRunFindActionOnClose) return;

    FindSettings.getInstance().setDefaultScopeName(selectedScope.getDisplayName());
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

  protected boolean isValid() {
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

    boolean searchWithinHierarchy = IdeBundle.message("scope.class.hierarchy").equals(myScopeChooserCombo.getSelectedScopeName());
    // We need to reset search within hierarchy scope during online validation since the scope works with user participation
    options.setScope(searchWithinHierarchy && !myDoingOkAction? GlobalSearchScope.projectScope(getProject()) :myScopeChooserCombo.getSelectedScope());
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

    ourFtSearchVariant = (String)fileTypes.getSelectedItem();
    ourDialect = (Language)dialects.getSelectedItem();
    ourContext = (String)contexts.getSelectedItem();
    FileType fileType = getFileTypeByName(ourFtSearchVariant);
    options.setFileType(fileType);
    options.setDialect(ourDialect);
    options.setPatternContext(ourContext);

    options.setSearchPattern(searchCriteriaEdit.getDocument().getText());
    options.setCaseSensitiveMatch(caseSensitiveMatch.isSelected());
    config.setSearchOnDemand(isSearchOnDemandEnabled() && searchIncrementally.isSelected());
  }

  private static FileType getFileTypeByName(String nameInCombo) {
    for (FileType type : StructuralSearchUtil.getSuitableFileTypes()) {
      if (StructuralSearchProfile.getTypeName(type).equals(nameInCombo)) {
        return type;
      }
    }
    assert false : "unknown file type: " + nameInCombo;
    return null;
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
