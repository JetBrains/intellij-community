package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.localVcs.LocalVcs;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.UnsupportedPatternException;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.ReplacementInfo;
import com.intellij.structuralsearch.plugin.replace.Replacer;
import com.intellij.structuralsearch.plugin.ui.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Set;

// Class to show the user the request for search

@SuppressWarnings({"RefusedBequest"})
public class ReplaceDialog extends SearchDialog {
  private Editor replaceCriteriaEdit;
  private JCheckBox shortenFQN;
  private JCheckBox formatAccordingToStyle;

  protected String getDefaultTitle() {
    return SSRBundle.message("structural.replace.title");
  }

  protected boolean isChanged(Configuration configuration) {
    if (super.isChanged(configuration)) return true;

    String replacement;

    if (configuration instanceof ReplaceConfiguration) {
      replacement = ((ReplaceConfiguration)configuration).getOptions().getReplacement();
    }
    else {
      replacement = configuration.getMatchOptions().getSearchPattern();
    }

    if (replacement == null) return false;

    return !replaceCriteriaEdit.getDocument().getText().equals(replacement);
  }

  protected JComponent createEditorContent() {
    JPanel result = new JPanel(new BorderLayout());
    Splitter p;

    result.add(BorderLayout.CENTER, p = new Splitter(true, 0.5f));
    p.setFirstComponent(super.createEditorContent());

    replaceCriteriaEdit = createEditor(searchContext);
    JPanel replace = new JPanel(new BorderLayout());
    replace.add(BorderLayout.NORTH, new JLabel(SSRBundle.message("replacement.template.label")));
    replace.add(BorderLayout.CENTER, replaceCriteriaEdit.getComponent());
    replaceCriteriaEdit.getComponent().setMinimumSize(new Dimension(150, 100));

    p.setSecondComponent(replace);

    return result;
  }

  protected int getRowsCount() {
    return super.getRowsCount() + 1;
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.structuralsearch.plugin.replace.ui.ReplaceDialog";
  }

  protected void buildOptions(JPanel searchOptions) {
    super.buildOptions(searchOptions);
    searchOptions.add(
      UIUtil.createOptionLine(shortenFQN = new JCheckBox(SSRBundle.message("shorten.fully.qualified.names.checkbox"), true))
    );

    searchOptions.add(
      UIUtil.createOptionLine(formatAccordingToStyle = new JCheckBox(SSRBundle.message("format.according.to.style.checkbox"), true))
    );

  }

  protected NavigateSearchResultsDialog createResultsNavigator(final SearchContext searchContext, Configuration config) {
    final NavigateSearchResultsDialog resultsDialog = new NavigateSearchResultsDialog(searchContext.getProject(), true);
    resultsDialog.setOptions(((ReplaceConfiguration)config).getOptions());
    return resultsDialog;
  }

  protected UsageViewContext createUsageViewContext(Configuration configuration) {
    return new ReplaceUsageViewContext(searchContext, configuration);
  }

  private static boolean isValid(UsageInfo2UsageAdapter info, UsageViewContext context) {
    final UsageInfo usageInfo = info.getUsageInfo();
    return !context.isExcluded(info) && usageInfo.getElement() != null && usageInfo.getElement().isValid();
  }

  protected void configureActions(UsageViewContext context) {
    final ReplaceUsageViewContext replaceContext = (ReplaceUsageViewContext)context;

    final Runnable replaceRunnable = new Runnable() {
      public void run() {
        LocalVcs instance = LocalVcs.getInstance(searchContext.getProject());
        LvcsAction lvcsAction = instance.startAction(getDefaultTitle(), null, false);

        doReplace(replaceContext);
        replaceContext.getUsageView().close();
        lvcsAction.finish();
      }
    };

    //noinspection HardCodedStringLiteral
    replaceContext.getUsageView().addPerformOperationAction(
      replaceRunnable,
      "Replace All",
      null,
      SSRBundle.message("do.replace.all.button")
    );

    final Runnable replaceSelected = new Runnable() {
      public void run() {
        final Set<Usage> infos = replaceContext.getUsageView().getSelectedUsages();
        if (infos == null || infos.isEmpty()) return;

        LocalVcs instance = LocalVcs.getInstance(searchContext.getProject());
        LvcsAction lvcsAction = instance.startAction(getDefaultTitle(), null, false);

        for (final Usage info : infos) {
          final UsageInfo2UsageAdapter usage = (UsageInfo2UsageAdapter)info;

          if (isValid(usage, replaceContext)) {
            replaceOne(usage, replaceContext, searchContext.getProject(), false);
          }
        }

        lvcsAction.finish();

        if (replaceContext.getUsageView().getUsagesCount() > 0) {
          for (Usage usage : replaceContext.getUsageView().getUsages()) {
            if (!replaceContext.isExcluded(usage)) {
              replaceContext.getUsageView().selectUsages(new Usage[]{usage});
              return;
            }
          }
        }
      }
    };

    replaceContext.getUsageView().addButtonToLowerPane(
      replaceSelected,
      SSRBundle.message("replace.selected.button")
    );

    final Runnable previewReplacement = new Runnable() {
      public void run() {
        Set<Usage> selection = replaceContext.getUsageView().getSelectedUsages();

        if (selection != null && !selection.isEmpty()) {
          UsageInfo2UsageAdapter usage = (UsageInfo2UsageAdapter)selection.iterator().next();

          if (isValid(usage, replaceContext)) {
            replaceOne(usage, replaceContext, searchContext.getProject(), true);
          }
        }
      }
    };

    replaceContext.getUsageView().addButtonToLowerPane(
      previewReplacement,
      SSRBundle.message("preview.replacement.button")
    );

    super.configureActions(context);
  }

  private static void ensureFileWritable(final UsageInfo2UsageAdapter usage) {
    final VirtualFile file = usage.getFile();

    if (!file.isWritable()) {
      ReadonlyStatusHandler.getInstance(usage.getElement().getProject()).ensureFilesWritable(file);
    }
  }

  private static void replaceOne(UsageInfo2UsageAdapter info, ReplaceUsageViewContext context, Project project, boolean doConfirm) {
    ReplacementInfo replacementInfo = context.getUsage2ReplacementInfo().get(info);
    boolean approved;

    if (doConfirm) {
      ReplacementPreviewDialog wrapper = new ReplacementPreviewDialog(
        project,
        info.getUsageInfo(),
        replacementInfo.getReplacement()
      );

      wrapper.show();
      approved = wrapper.isOK();
    }
    else {
      approved = true;
    }

    if (approved) {
      ensureFileWritable(info);
      context.getUsageView().removeUsage(info);
      context.getReplacer().replace(replacementInfo);

      if (context.getUsageView().getUsagesCount() == 0) {
        context.getUsageView().close();
      }
    }
  }

  private static void doReplace(ReplaceUsageViewContext context) {
    Set<Usage> infos = context.getUsageView().getUsages();
    java.util.List<ReplacementInfo> results = new ArrayList<ReplacementInfo>(context.getResults().size());

    for (final Usage info : infos) {
      UsageInfo2UsageAdapter usage = (UsageInfo2UsageAdapter)info;

      if (isValid(usage, context)) {
        results.add(context.getUsage2ReplacementInfo().get(usage));
      }
    }

    context.getReplacer().replaceAll(results);
  }

  public ReplaceDialog(SearchContext searchContext) {
    super(searchContext);
  }

  public ReplaceDialog(SearchContext searchContext, boolean showScope, boolean runFindActionOnClose) {
    super(searchContext, showScope, runFindActionOnClose);
  }


  public Configuration createConfiguration() {
    ReplaceConfiguration configuration = new ReplaceConfiguration();
    configuration.setName(USER_DEFINED);
    return configuration;
  }

  protected void dispose() {
    EditorFactory.getInstance().releaseEditor(replaceCriteriaEdit);
    super.dispose();
  }

  public void setValuesFromConfig(Configuration configuration) {
    //replaceCriteriaEdit.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, configuration);

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
    }
    else {
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

  protected void setValuesToConfig(Configuration config) {
    super.setValuesToConfig(config);
    
    final ReplaceConfiguration replaceConfiguration = (ReplaceConfiguration)config;
    final ReplaceOptions options = replaceConfiguration.getOptions();

    options.setMatchOptions(replaceConfiguration.getMatchOptions());
    options.setReplacement(replaceCriteriaEdit.getDocument().getText());
    options.setToShortenFQN(shortenFQN.isSelected());
    options.setToReformatAccordingToStyle(formatAccordingToStyle.isSelected());
  }

  protected static boolean equalsConfigs(Configuration config, Configuration configuration) {
    if (config instanceof ReplaceConfiguration && configuration instanceof ReplaceConfiguration) {
      final ReplaceConfiguration replaceConfig = (ReplaceConfiguration)config;
      final ReplaceConfiguration replaceConfiguration = (ReplaceConfiguration)configuration;

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

  protected boolean doValidate() {
    if (!super.doValidate()) return false;

    boolean result = true;

    try {
      Replacer.checkSupportedReplacementPattern(
        searchContext.getProject(),
        searchCriteriaEdit.getDocument().getText(),
        replaceCriteriaEdit.getDocument().getText(),
        getCurrentFileType()
      );
    }
    catch (UnsupportedPatternException ex) {
      reportMessage("unsupported.replacement.pattern.message",replaceCriteriaEdit, ex.getPattern());
      result = false;
    }

    return result;
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
    addOrReplaceSelectionForEditor(selection, replaceCriteriaEdit);
  }
}
