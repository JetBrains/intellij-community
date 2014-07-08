package com.intellij.structuralsearch.plugin.replace.ui;

import com.intellij.codeInsight.template.impl.Variable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.ui.Splitter;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.ReplacementVariableDefinition;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.UnsupportedPatternException;
import com.intellij.structuralsearch.plugin.replace.ReplaceOptions;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import com.intellij.structuralsearch.plugin.ui.*;
import com.intellij.util.containers.hash.LinkedHashMap;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Class to show the user the request for search

@SuppressWarnings({"RefusedBequest"})
public class ReplaceDialog extends SearchDialog {
  private Editor replaceCriteriaEdit;
  private JCheckBox shortenFQN;
  private JCheckBox formatAccordingToStyle;

  private String mySavedEditorText;

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

    replaceCriteriaEdit = createEditor(searchContext, mySavedEditorText != null ? mySavedEditorText : "");
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
    searchOptions
      .add(UIUtil.createOptionLine(shortenFQN = new JCheckBox(SSRBundle.message("shorten.fully.qualified.names.checkbox"), true)));

    searchOptions
      .add(UIUtil.createOptionLine(formatAccordingToStyle = new JCheckBox(SSRBundle.message("format.according.to.style.checkbox"), true)));

  }

  protected UsageViewContext createUsageViewContext(Configuration configuration) {
    return new ReplaceUsageViewContext(searchContext, configuration);
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

  protected void disposeEditorContent() {
    mySavedEditorText = replaceCriteriaEdit.getDocument().getText();
    EditorFactory.getInstance().releaseEditor(replaceCriteriaEdit);
    super.disposeEditorContent();
  }

  public void setValuesFromConfig(Configuration configuration) {
    //replaceCriteriaEdit.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, configuration);

    if (configuration instanceof ReplaceConfiguration) {
      final ReplaceConfiguration config = (ReplaceConfiguration)configuration;
      final ReplaceOptions options = config.getOptions();
      super.setValuesFromConfig(config);

      UIUtil.setContent(replaceCriteriaEdit, config.getOptions().getReplacement(), 0, replaceCriteriaEdit.getDocument().getTextLength(),
                        searchContext.getProject());

      shortenFQN.setSelected(options.isToShortenFQN());
      formatAccordingToStyle.setSelected(options.isToReformatAccordingToStyle());

      ReplaceOptions newReplaceOptions = ((ReplaceConfiguration)model.getConfig()).getOptions();
      newReplaceOptions.clearVariableDefinitions();
      
      for (ReplacementVariableDefinition def : options.getReplacementVariableDefinitions()) {
        newReplaceOptions.addVariableDefinition((ReplacementVariableDefinition)def.clone());
      }
    }
    else {
      super.setValuesFromConfig(configuration);

      UIUtil.setContent(replaceCriteriaEdit, configuration.getMatchOptions().getSearchPattern(), 0,
                        replaceCriteriaEdit.getDocument().getTextLength(), searchContext.getProject());
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

  protected boolean isRecursiveSearchEnabled() {
    return false;
  }

  protected java.util.List<Variable> getVariablesFromListeners() {
    ArrayList<Variable> vars = getVarsFrom(replaceCriteriaEdit);
    List<Variable> searchVars = super.getVariablesFromListeners();
    Map<String, Variable> varsMap = new LinkedHashMap<String, Variable>(searchVars.size());

    for(Variable var:searchVars) varsMap.put(var.getName(), var);
    for(Variable var:vars) {
      if (!varsMap.containsKey(var.getName())) {
        String newVarName = var.getName() + ReplaceConfiguration.REPLACEMENT_VARIABLE_SUFFIX;
        varsMap.put(newVarName, new Variable(newVarName, null, null, false, false));
      }
    }
    return new ArrayList<Variable>(varsMap.values());
  }

  protected boolean isValid() {
    if (!super.isValid()) return false;

    try {
      Replacer.checkSupportedReplacementPattern(searchContext.getProject(), ((ReplaceConfiguration)model.getConfig()).getOptions());
    }
    catch (UnsupportedPatternException ex) {
      reportMessage("unsupported.replacement.pattern.message", replaceCriteriaEdit, ex.getMessage());
      return false;
    }
    catch (MalformedPatternException ex) {
      reportMessage("malformed.replacement.pattern.message", replaceCriteriaEdit, ex.getMessage());
      return false;
    }

    return true;
  }

  public void show() {
    replaceCriteriaEdit.putUserData(SubstitutionShortInfoHandler.CURRENT_CONFIGURATION_KEY, model.getConfig());

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
