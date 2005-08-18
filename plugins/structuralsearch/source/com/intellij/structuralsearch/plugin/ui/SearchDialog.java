package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.find.FindSettings;
import com.intellij.find.FindProgressIndicator;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.structuralsearch.*;
import com.intellij.structuralsearch.plugin.replace.ui.NavigateSearchResultsDialog;
import com.intellij.structuralsearch.plugin.ui.actions.DoSearchAction;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.usages.*;
import com.intellij.util.Processor;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Iterator;

// Class to show the user the request for search
public class SearchDialog extends DialogWrapper implements ConfigurationCreator {
  protected SearchContext searchContext;

  // text for search
  protected Editor searchCriteriaEdit;

  // options of search scope
  private ScopeChooserCombo combo;

  private JCheckBox searchIncrementally;
  private JCheckBox recursiveMatching;
  //private JCheckBox distinctResults;
  private JCheckBox caseSensitiveMatch;

  private JCheckBox maxMatchesSwitch;
  private JTextField maxMatches;
  private JComboBox fileTypes;

  protected SearchModel model;
  private JCheckBox openInNewTab;

  protected static final String USER_DEFINED = "user defined";
  protected final ExistingTemplatesComponent existingTemplatesComponent;

  private boolean useLastConfiguration;

  private static boolean ourOpenInNewTab;
  private static boolean ourUseMaxCount;
  private static String ourFileType = "java";

  protected UsageViewContext createUsageViewContext(Configuration configuration) {
    return new UsageViewContext(searchContext, configuration);
  }

  public void setUseLastConfiguration(boolean useLastConfiguration) {
    this.useLastConfiguration = useLastConfiguration;
  }

  protected boolean isChanged(Configuration configuration) {
    if (configuration.getMatchOptions().getSearchPattern()==null) return false;
    return !searchCriteriaEdit.getDocument().getText().equals(configuration.getMatchOptions().getSearchPattern());
  }

  private void setSearchPatternFromNode(final DefaultMutableTreeNode node) {
    if (node == null) return;
    final Object userObject = node.getUserObject();

    if (userObject instanceof Configuration) {
      Configuration config = (Configuration)userObject;
      model.setShadowConfig( config );
      setValuesFromConfig( config );
    }
  }

  protected Editor createEditor(final SearchContext searchContext) {
    PsiElement element = searchContext.getFile();

    if (element!=null && !useLastConfiguration) {
      final Editor selectedEditor = FileEditorManager.getInstance(searchContext.getProject()).getSelectedTextEditor();

      if (selectedEditor!=null) {
        int caretPosition = selectedEditor.getCaretModel().getOffset();
        PsiElement positionedElement = searchContext.getFile().findElementAt(caretPosition);

        if (positionedElement==null) {
          positionedElement = searchContext.getFile().findElementAt(caretPosition+1);
        }

        if(positionedElement!=null) {
          element = PsiTreeUtil.getParentOfType(
            positionedElement,
            PsiClass.class, PsiCodeBlock.class
          );
        }
      }
    }

    final PsiManager psimanager = PsiManager.getInstance(searchContext.getProject());
    PsiCodeFragment file = psimanager.getElementFactory().createCodeBlockCodeFragment("",element,true);
    Document doc = PsiDocumentManager.getInstance(searchContext.getProject()).getDocument( file );
    DaemonCodeAnalyzer.getInstance(searchContext.getProject()).setHighlightingEnabled(file,false);
    Editor editor = UIUtil.createEditor(doc, searchContext.getProject(),true,true);

    return editor;
  }

  protected void buildOptions(JPanel searchOptions) {
    searchIncrementally = new JCheckBox("Find with prompt",false);

    if (isSearchOnDemandEnabled()) {
      searchOptions.add(
        UIUtil.createOptionLine(searchIncrementally )
      );
      searchIncrementally.setMnemonic('p');
    }

    recursiveMatching = new JCheckBox("Recursive matching",true);

    if (isRecursiveSearchEnabled()) {
      searchOptions.add(
        UIUtil.createOptionLine(recursiveMatching )
      );
      recursiveMatching.setMnemonic('R');
    }

    //searchOptions.add(
    //  UIUtil.createOptionLine(
    //    distinctResults = new JCheckBox("Distinct results",false)
    //  )
    //);
    //
    //distinctResults.setMnemonic('D');

    caseSensitiveMatch = new JCheckBox("Case sensitive",true);
    caseSensitiveMatch.setMnemonic('C');

    searchOptions.add( UIUtil.createOptionLine(caseSensitiveMatch) );

    searchOptions.add(
      UIUtil.createOptionLine(
        new JComponent[] {
          maxMatchesSwitch = new JCheckBox("Maximum matches",false),
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

    maxMatchesSwitch.setMnemonic('x');
    maxMatches.setMaximumSize(new Dimension(50,25));

    fileTypes = new JComboBox(new String[] {"java", "xml", "html"} );
    if (ourSupportDifferentFileTypes) {
      searchOptions.add(
        UIUtil.createOptionLine(
          new JComponent[] {
            new JLabel("File type:"),
            fileTypes,
            (JComponent)Box.createHorizontalGlue()
          }
        )
      );
    }

    fileTypes.setSelectedItem(ourFileType);

    if (isSearchOnDemandEnabled()) {
      searchOptions.add(
        UIUtil.createOptionLine(searchIncrementally )
      );
      searchIncrementally.setMnemonic('p');
    }
  }

  protected boolean isRecursiveSearchEnabled() {
    return true;
  }

  public void setValuesFromConfig(Configuration configuration) {
    searchCriteriaEdit.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, configuration);

    setTitle(getDefaultTitle()+" - " + configuration.getName());
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
      maxMatches.setText( String.valueOf(matchOptions.getMaxMatchesCount()) );
    }

    model.getConfig().getMatchOptions().clearVariableConstraints();
    if (matchOptions.hasVariableConstraints()) {
      for(Iterator<String> i = matchOptions.getVariableConstraintNames();i.hasNext();) {
        model.getConfig().getMatchOptions().addVariableConstraint(
          (MatchVariableConstraint)matchOptions.getVariableConstraint(i.next()).clone()
        );
      }
    }                                  

    searchIncrementally.setSelected( configuration.isSearchOnDemand() );
    fileTypes.setSelectedItem( configuration.getMatchOptions().getFileType().getName().toLowerCase() );
  }

  public Configuration createConfiguration() {
    SearchConfiguration configuration = new SearchConfiguration();
    configuration.setName( USER_DEFINED );
    return configuration;
  }

  protected void addOrReplaceSelection(final String selection) {
    addOrReplaceSelectionForEditor(selection,searchCriteriaEdit);
  }

  protected final void addOrReplaceSelectionForEditor(final String selection, Editor editor) {
    UIUtil.setContent(editor,selection,0,-1,searchContext.getProject());
    editor.getSelectionModel().setSelection(
      0,
      selection.length()
    );
  }

  protected void runAction(final Configuration config, final SearchContext searchContext) {
    if (searchIncrementally.isSelected()) {
      NavigateSearchResultsDialog resultsDialog = createResultsNavigator(searchContext, config);

      DoSearchAction.execute(searchContext.getProject(), resultsDialog, config);
    } else {
      createUsageView(searchContext, config);
    }
  }

  protected NavigateSearchResultsDialog createResultsNavigator(final SearchContext searchContext,
                                                               Configuration config) {
    return new NavigateSearchResultsDialog(searchContext.getProject(),false);
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
      new AbstractAction("Edit Query") {
        {
          putValue(FindUsagesProcessPresentation.NAME_WITH_MNEMONIC_KEY,"Edit &Query");
        }
        public void actionPerformed(ActionEvent e) {
          UIUtil.invokeActionAnotherTime(config,searchContext);
        }
      }
    );

    manager.searchAndShowUsages(
      new UsageTarget[] {
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
      new com.intellij.usages.UsageViewManager.UsageViewStateListener() {
        public void usageViewCreated(com.intellij.usages.UsageView usageView) {
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
          SwingUtilities.invokeLater( new Runnable() {
            public void run() {
              UIUtil.invokeActionAnotherTime(context.getConfiguration(),searchContext);
            }
          });
       }
      },
      "Edit Query",
      'Q'
    );
  }

  public SearchDialog(SearchContext _searchContext) {
    super(_searchContext.getProject(),true);
    searchContext = (SearchContext)_searchContext.clone();
    setTitle(getDefaultTitle());
    setOKButtonText("Find");
    setOKButtonIcon(IconLoader.getIcon("/actions/find.png"));
    getOKAction().putValue(Action.MNEMONIC_KEY,new Integer('F'));

    existingTemplatesComponent = ExistingTemplatesComponent.getInstance(searchContext.getProject());
    model = new SearchModel(createConfiguration());
    init();
  }

  protected String getDefaultTitle() {
    return "Structural Search";
  }

  protected JComponent createEditorContent() {
    JPanel result = new JPanel(new BorderLayout());

    result.add(BorderLayout.NORTH,new JLabel("Search template:"));
    searchCriteriaEdit = createEditor(searchContext);
    result.add(BorderLayout.CENTER, searchCriteriaEdit.getComponent());
    result.setMinimumSize(new Dimension(150,100));

    return result;
  }

  private static boolean ourSupportDifferentFileTypes = false;
  
  protected int getRowsCount() {
    return (ourSupportDifferentFileTypes)?4:3;
  }

  protected JComponent createCenterPanel() {
    JComponent centerPanel = new JPanel(new BorderLayout());

    JPanel panel = new JPanel( new BorderLayout() );
    JPanel editorPanel = new JPanel( new BorderLayout() );
    editorPanel.add(BorderLayout.CENTER,createEditorContent());
    editorPanel.add(BorderLayout.SOUTH,Box.createVerticalStrut(8));
    panel.add(BorderLayout.CENTER,editorPanel);
    panel.add(BorderLayout.SOUTH,createTemplateManagementButtons());
    centerPanel.add(BorderLayout.CENTER,panel);

    JPanel optionsContent = new JPanel( new BorderLayout() );
    centerPanel.add( BorderLayout.SOUTH, optionsContent );

    JPanel searchOptions = new JPanel();
    searchOptions.setLayout(new GridLayout(getRowsCount(),1,0,0) );
    searchOptions.setBorder( IdeBorderFactory.createTitledBorder("Options"));

    JPanel allOptions = new JPanel( new BorderLayout() );

    JPanel scopePanel = new JPanel(new BorderLayout());
    JLabel label;
    scopePanel.add(Box.createVerticalStrut(8),BorderLayout.NORTH);
    scopePanel.add(
      label = new JLabel("Scope: "), BorderLayout.WEST
    );

    label.setDisplayedMnemonic('s');
    scopePanel.add(
      combo = new ScopeChooserCombo(
        searchContext.getProject(),
        true,
        false,
        (isReplaceDialog())?"Current File":FindSettings.getInstance().getDefaultScopeName()
      ),
      BorderLayout.CENTER
    );
    label.setLabelFor(combo.getComboBox());

    allOptions.add(
      scopePanel,
      BorderLayout.SOUTH
    );

    buildOptions(searchOptions);

    allOptions.add( searchOptions, BorderLayout.CENTER );
    optionsContent.add( allOptions, BorderLayout.CENTER );

    JPanel _panel = new JPanel(new BorderLayout());
    _panel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
    openInNewTab = new JCheckBox("Open in new tab");
    openInNewTab.setMnemonic('t');
    openInNewTab.setSelected(ourOpenInNewTab);
    openInNewTab.setEnabled(
      ToolWindowManager.getInstance(searchContext.getProject()).getToolWindow(ToolWindowId.FIND).isAvailable()
    );
    _panel.add(openInNewTab, BorderLayout.EAST);

    optionsContent.add( BorderLayout.SOUTH, _panel );

    return centerPanel;
  }

  private JPanel createTemplateManagementButtons() {
    JPanel panel = new JPanel(null);
    panel.setLayout(new BoxLayout(panel,BoxLayout.X_AXIS));
    panel.add(Box.createHorizontalGlue());

    panel.add(
      createJButtonForAction(new AbstractAction() {
        {
          putValue(NAME,"S&ave template...");
        }
        public void actionPerformed(ActionEvent e) {
          String name = Messages.showInputDialog(
            searchContext.getProject(),
            "Template name",
            "Save template",
            IconLoader.getIcon("/general/questionDialog.png"),
            model.getShadowConfig()!=null ? model.getShadowConfig().getName():"",
            null
          );
          if (name!=null) {
            model.getConfig().setName(name);
            setValuesToConfig(model.getConfig());

            if (model.getShadowConfig() == null ||
                model.getShadowConfig() instanceof PredefinedConfiguration
               ) {
              existingTemplatesComponent.addConfigurationToUserTemplates(
                model.getConfig()
              );
            } else {
              setValuesToConfig(model.getShadowConfig());
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
            putValue(NAME,"E&dit variables...");
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
              getFileTypeByString((String) fileTypes.getSelectedItem())
            ).show();
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
            putValue(NAME,"&History...");
          }
          public void actionPerformed(ActionEvent e) {
            DialogWrapper wrapper = new SelectTemplateDialog(searchContext.getProject(),true, isReplaceDialog());
            wrapper.show();

            if (wrapper.isOK()) {
              int selection = existingTemplatesComponent.getHistoryList().getSelectedIndex();

              if (selection!=-1) {
                setValuesFromConfig(
                  (Configuration)existingTemplatesComponent.getHistoryList().getSelectedValue()
                );
                model.setShadowConfig( null );
              }
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
            putValue(NAME,"Co&py existing template...");
          }
          public void actionPerformed(ActionEvent e) {
            DialogWrapper wrapper = new SelectTemplateDialog(searchContext.getProject(),false,isReplaceDialog());
            wrapper.show();

            if (wrapper.isOK()) {
              TreePath path = existingTemplatesComponent.getPatternTree().getSelectionPath();
              if (path!=null) {
                setSearchPatternFromNode((DefaultMutableTreeNode)path.getLastPathComponent());
              }
            }
          }
        }
      )
    );

    return panel;
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
          addOrReplaceSelection( selectionModel.getSelectedText() );
          existingTemplatesComponent.getPatternTree().setSelectionPath(null);
          existingTemplatesComponent.getHistoryList().setSelectedIndex(-1);
          setSomeText = true;
        }
      }

      if (!setSomeText) {
        int selection = existingTemplatesComponent.getHistoryList().getSelectedIndex();
        if (selection!=-1) {
          setValuesFromConfig(
            (Configuration)existingTemplatesComponent.getHistoryList().getSelectedValue()
          );
        }
      }
    }

    super.show();
  }

  public JComponent getPreferredFocusedComponent() {
    return searchCriteriaEdit.getContentComponent();
  }

  // Performs ok action
  protected void doOKAction() {
    SearchScope selectedScope = combo.getSelectedScope();
    if (selectedScope == null) return;

    setValuesToConfig(model.getConfig());
    
    try {
      Matcher.validate(searchContext.getProject(),model.getConfig().getMatchOptions());
    } catch(UnsupportedPatternException ex) {
      Messages.showMessageDialog(
        searchContext.getProject(),
        "This pattern is malformed or unsupported",
        "Information",
        Messages.getInformationIcon()
      );
      return;
    } catch(MalformedPatternException ex) {
      Messages.showMessageDialog(
        searchContext.getProject(),
        "This pattern is malformed or unsupported",
        "Information",
        Messages.getInformationIcon()
      );
      return;
    }

    super.doOKAction();
    if (!isReplaceDialog()) {
      FindSettings.getInstance().setDefaultScopeName(combo.getSelectedScopeName());
    }
    ourOpenInNewTab = openInNewTab.isSelected();

    try {
      if (model.getShadowConfig()!=null) {
        if (model.getShadowConfig() instanceof PredefinedConfiguration) {
          model.getConfig().setName(
            model.getShadowConfig().getName()
          );
        } //else {
        //  // user template, save it
        //  setValuesToConfig(model.getShadowConfig());
        //}
      }
      existingTemplatesComponent.addConfigurationToHistory(
        model.getConfig()
      );

      runAction(model.getConfig(), searchContext);
    } catch(MalformedPatternException ex) {
      Messages.showMessageDialog(
        searchContext.getProject(),
        "Incorrect pattern",
        "Information",
        Messages.getInformationIcon()
      );
    }
  }

  protected void setValuesToConfig(Configuration config) {
    MatchOptions options;

    options = config.getMatchOptions();

    options.setScope(
      combo.getSelectedScope()
    );
    options.setLooseMatching( true );
    options.setRecursiveSearch( isRecursiveSearchEnabled() && recursiveMatching.isSelected() );
    //options.setDistinct( distinctResults.isSelected() );

    ourUseMaxCount = maxMatchesSwitch.isSelected();

    if (maxMatchesSwitch.isSelected()) {
      try {
        options.setMaxMatchesCount(
          Integer.parseInt(maxMatches.getText())
        );
      } catch(NumberFormatException ex) {
        options.setMaxMatchesCount(
          MatchOptions.DEFAULT_MAX_MATCHES_COUNT
        );
      }
    } else {
      options.setMaxMatchesCount(Integer.MAX_VALUE);
    }

    ourFileType = (String) fileTypes.getSelectedItem();
    options.setFileType(getFileTypeByString(ourFileType));

    options.setSearchPattern(searchCriteriaEdit.getDocument().getText());
    options.setCaseSensitiveMatch( caseSensitiveMatch.isSelected() );
    config.setSearchOnDemand( isSearchOnDemandEnabled() && searchIncrementally.isSelected() );
  }

  protected FileType getCurrentFileType() {
    return getFileTypeByString((String) fileTypes.getSelectedItem());
  }

  private FileType getFileTypeByString(String ourFileType) {
    if (ourFileType.equals("java")) {
      return StdFileTypes.JAVA;
    }
    else if (ourFileType.equals("html")) {
      return StdFileTypes.HTML;
    }
    else {
      return StdFileTypes.XML;
    }
  }

  protected boolean isSearchOnDemandEnabled() {
    return false;
  }

  protected Action[] createActions(){
    return new Action[]{ getOKAction(),getCancelAction(),getHelpAction()};
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.structuralsearch.plugin.ui.SearchDialog";
  }

  protected void dispose() {
    Configuration.setActiveCreator(null);

    // this will remove from myExcludedSet
    DaemonCodeAnalyzer.getInstance(searchContext.getProject()).setHighlightingEnabled(
      PsiDocumentManager.getInstance(searchContext.getProject()).getPsiFile(
        searchCriteriaEdit.getDocument()
      ),true
    );

    EditorFactory.getInstance().releaseEditor(searchCriteriaEdit);

    super.dispose();
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp("find.structuredSearch");
  }
}
