package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.structuralsearch.UnsupportedPatternException;
import com.intellij.structuralsearch.plugin.ui.*;
import com.intellij.structuralsearch.plugin.replace.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageView;
import com.intellij.usageView.UsageViewManager;

import javax.swing.*;
import java.awt.*;

// Class to show the user the request for search
public class ReplaceDialog extends SearchDialog {
  private Editor replaceCriteriaEdit;
  private JCheckBox shortenFQN;
  private JCheckBox formatAccordingToStyle;

  protected String getDefaultTitle() {
    return "Structural Replace";
  }

  protected boolean isChanged(Configuration configuration) {
    if (super.isChanged(configuration)) return true;

    String replacement;

    if (configuration instanceof ReplaceConfiguration) {
      replacement = ((ReplaceConfiguration)configuration).getOptions().getReplacement();
    } else {
      replacement = configuration.getMatchOptions().getSearchPattern();
    }

    if (replacement==null) return false;

    return !replaceCriteriaEdit.getDocument().getText().equals(replacement);
  }

  protected JComponent createEditorContent() {
    JPanel result = new JPanel( new BorderLayout() );
    Splitter p;

    result.add(BorderLayout.CENTER, p = new Splitter(true,0.5f));
    p.setFirstComponent( super.createEditorContent() );

    JPanel replace = new JPanel( new BorderLayout() );
    replaceCriteriaEdit = createEditor(searchContext);
    replace.add(BorderLayout.NORTH,new JLabel("Replacement template:"));
    replace.add(BorderLayout.CENTER, replaceCriteriaEdit.getComponent() );
    replaceCriteriaEdit.getComponent().setMinimumSize(new Dimension(150,100));

    p.setSecondComponent( replace );

    return result;
  }

  protected int getRowsCount() {
    return super.getRowsCount() + 1;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.structuralsearch.plugin.replace.ui.ReplaceDialog";
  }

  protected StructuralSearchViewDescriptor createDescriptor(SearchContext context, Configuration config) {
    return new StructuralReplaceViewDescriptor(
      config.getMatchOptions().getSearchPattern(),
      new ReplaceCommand(context, config),
      replaceCriteriaEdit.getDocument().getText()
    );
  }

  protected void buildOptions(JPanel searchOptions) {
    super.buildOptions(searchOptions);
    searchOptions.add(
      UIUtil.createOptionLine(shortenFQN = new JCheckBox("Shorten fully qualified names", true) )
    );
    shortenFQN.setMnemonic('o');

    searchOptions.add(
      UIUtil.createOptionLine(formatAccordingToStyle = new JCheckBox("Format according to style", true) )
    );

    formatAccordingToStyle.setMnemonic('r');
  }

  protected NavigateSearchResultsDialog createResultsNavigator(final SearchContext searchContext,
                                                               Configuration config) {
    final NavigateSearchResultsDialog resultsDialog = new NavigateSearchResultsDialog(searchContext.getProject(),true);
    resultsDialog.setOptions( ((ReplaceConfiguration)config).getOptions() );
    return resultsDialog;
  }

  private static boolean isValid(UsageInfo info, UsageView usageView) {
    return !usageView.isExcluded(info) && info.getElement()!=null && info.getElement().isValid();
  }
  protected void createUsageView(final SearchContext searchContext, Configuration config) {
    super.createUsageView(searchContext, config);

    final Runnable replaceRunnable = new Runnable() {
      public void run() {
        LocalVcs instance = LocalVcs.getInstance(searchContext.getProject());
        LvcsAction lvcsAction = instance.startAction("StructuralReplace",null,false);

        doReplace(usageView, (StructuralReplaceViewDescriptor)descriptor);
        UsageViewManager.getInstance(searchContext.getProject()).closeContent(usageView);
        lvcsAction.finish();
      }
    };

    usageView.addDoProcessAction(replaceRunnable, "Replace All",null,"&Do Replace All");

    final AnAction replaceSelected = new AnAction() {
      {
        getTemplatePresentation().setText("Replace S&elected");
      }
      public void actionPerformed(AnActionEvent e) {
        final UsageInfo infos[] = usageView.getSelectedUsages();
        if (infos==null) return;

        LocalVcs instance = LocalVcs.getInstance(searchContext.getProject());
        LvcsAction lvcsAction = instance.startAction("StructuralReplace",null,false);

        for(int i=0;i<infos.length;++i) {
          if (!isValid(infos[i],usageView)) {
            continue;
          }
          replaceOne(infos[i], searchContext,false);
        }
        lvcsAction.finish();
      }

      public void update(AnActionEvent e) {
        e.getPresentation().setEnabled(!usageView.isInAsyncUpdate());
        super.update(e);
      }
    };
    usageView.addButton(1, replaceSelected);

    usageView.addButton(2,new AnAction("P&review Replacement") {
      public void actionPerformed(AnActionEvent e) {
        UsageInfo[] selection = usageView.getSelectedUsages();

        if (selection == null ||
            selection.length == 0 ||
            !isValid(selection[0],usageView)
            ) {
          return;
        }
        UsageInfo info = selection[0];

        replaceOne(info, searchContext,true);
      }
    });
  }

  private void replaceOne(UsageInfo info, final SearchContext searchContext, boolean doConfirm) {

    StructuralReplaceViewDescriptor replaceDescriptor = ((StructuralReplaceViewDescriptor)descriptor);

    int index = replaceDescriptor.getUsagesList().indexOf(info);
    if (index==-1) return;

    ReplacementInfo replacementInfo = replaceDescriptor.getResultPtrList().get(index);
    boolean approved;

    if (doConfirm) {
      ReplacementPreviewDialog wrapper = new ReplacementPreviewDialog(
        searchContext.getProject(),
        info,
        replacementInfo.getReplacement()
      );

      wrapper.show();
      approved = wrapper.isOK();
    } else {
      approved = true;
    }

    if (approved) {
      usageView.removeUsage(info);
      replaceDescriptor.getReplacer().replace(replacementInfo);
    }
  }

  private void doReplace(UsageView view, StructuralReplaceViewDescriptor descriptor) {
    UsageInfo infos[] = view.getUsages();
    java.util.List<ReplacementInfo> results = descriptor.getResultPtrList();

    for(int i = 0; i < infos.length; ++i) {

      if (!isValid(infos[i],usageView)) {
        int index = descriptor.getUsagesList().indexOf(infos[i]);
        if (index!=-1) {
          results.remove(index);
          descriptor.getUsagesList().remove(infos[i]);
        }
      }
    }

    descriptor.getReplacer().replaceAll(results);
  }

  public ReplaceDialog(SearchContext _searchContext) {
    super(_searchContext);
  }

  public Configuration createConfiguration() {
    ReplaceConfiguration configuration = new ReplaceConfiguration();
    configuration.setName( USER_DEFINED );
    return configuration;
  }

  protected void dispose() {
    EditorFactory.getInstance().releaseEditor(replaceCriteriaEdit);
    super.dispose();
  }

  public void setValuesFromConfig(Configuration configuration) {
    replaceCriteriaEdit.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, configuration);

    if (configuration instanceof ReplaceConfiguration) {
      final ReplaceConfiguration config = (ReplaceConfiguration)configuration;
      final ReplaceOptions options = config.getOptions();
      super.setValuesFromConfig(config);

      UIUtil.setContent(
        replaceCriteriaEdit,
        config.getOptions().getReplacement(),
        0,
        replaceCriteriaEdit.getDocument().getTextLength(),
        searchContext.getProject()
      );

      shortenFQN.setSelected(options.isToShortenFQN());
      formatAccordingToStyle.setSelected(options.isToReformatAccordingToStyle());
    } else {
      super.setValuesFromConfig(configuration);

      UIUtil.setContent(
        replaceCriteriaEdit,
        configuration.getMatchOptions().getSearchPattern(),
        0,
        replaceCriteriaEdit.getDocument().getTextLength(),
        searchContext.getProject()
      );
    }
  }

  protected void setValuesToConfig(Configuration _config) {
    super.setValuesToConfig(_config);

    final ReplaceConfiguration config = (ReplaceConfiguration)_config;
    final ReplaceOptions options = config.getOptions();

    options.setMatchOptions( config.getMatchOptions() );
    options.setReplacement( replaceCriteriaEdit.getDocument().getText() );
    options.setToShortenFQN( shortenFQN.isSelected() );
    options.setToReformatAccordingToStyle( formatAccordingToStyle.isSelected() );
  }

  protected boolean equalsConfigs(Configuration config, Configuration configuration) {
    if (config instanceof ReplaceConfiguration && configuration instanceof ReplaceConfiguration) {
      final ReplaceConfiguration replaceConfig = ((ReplaceConfiguration)config);
      final ReplaceConfiguration replaceConfiguration = ((ReplaceConfiguration)configuration);

      return replaceConfig.getOptions().getReplacement().equals(
        replaceConfiguration.getOptions().getReplacement()) &&
        replaceConfig.getOptions().getMatchOptions().getSearchPattern().equals(
        replaceConfiguration.getOptions().getMatchOptions().getSearchPattern()
      );
    }

    return false;
  }

  protected boolean isRecursiveSearchEnabled() {
    return false;
  }

  protected boolean isSearchOnDemandEnabled() {
    return false;
  }

  // Performs ok action
  protected void doOKAction() {
    try {
      Replacer.checkSupportedReplacementPattern(
        searchContext.getProject(),
        searchCriteriaEdit.getDocument().getText(),
        replaceCriteriaEdit.getDocument().getText(),
        getCurrentFileType()
      );
      super.doOKAction();
    } catch(UnsupportedPatternException ex) {
      Messages.showErrorDialog(searchContext.getProject(),ex.toString(),"Unsupported Replacement Pattern");
    }
  }

  public void show() {
    replaceCriteriaEdit.putUserData(
      SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY,
      model.getConfig()
    );
    
    super.show();
  }

  protected boolean isReplaceDialog() {
    return true;
  }

  protected void addOrReplaceSelection(final String selection) {
    super.addOrReplaceSelection(selection);
    addOrReplaceSelectionForEditor(selection,replaceCriteriaEdit);
  }
}
