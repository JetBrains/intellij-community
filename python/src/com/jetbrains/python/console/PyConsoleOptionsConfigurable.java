// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.jetbrains.python.PyBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;
import java.awt.Insets;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Supplier;

public final class PyConsoleOptionsConfigurable extends SearchableConfigurable.Parent.Abstract implements Configurable.NoScroll {
  public static final String CONSOLE_SETTINGS_HELP_REFERENCE = "reference.project.settings.console";
  public static final String CONSOLE_SETTINGS_HELP_REFERENCE_PYTHON = "reference.project.settings.console.python";

  private PyConsoleOptionsPanel myPanel;

  private final Project myProject;

  public enum CodeCompletionOption {
    RUNTIME("Runtime"),
    STATIC("Static");

    CodeCompletionOption(@NlsSafe @NotNull String displayName) {
      myDisplayNameSupplier = () -> displayName;
    }

    @Override
    public @Nls String toString() {
      return myDisplayNameSupplier.get();
    }

    private final Supplier<@Nls String> myDisplayNameSupplier;
  }

  public PyConsoleOptionsConfigurable(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull String getId() {
    return "pyconsole";
  }

  @Override
  protected @NotNull Configurable @NotNull [] buildConfigurables() {
    List<Configurable> result = new ArrayList<>();

    PyConsoleSpecificOptionsPanel pythonConsoleOptionsPanel = new PyConsoleSpecificOptionsPanel(myProject);
    result.add(createConsoleChildConfigurable(PyBundle.message("configurable.PyConsoleOptionsConfigurable.child.display.name"), pythonConsoleOptionsPanel,
                                              PyConsoleOptions.getInstance(myProject).getPythonConsoleSettings(), CONSOLE_SETTINGS_HELP_REFERENCE_PYTHON));

    for (PyConsoleOptionsProvider provider : PyConsoleOptionsProvider.EP_NAME.getExtensionList()) {
      if (provider.isApplicableTo(myProject)) {
        result.add(createConsoleChildConfigurable(provider.getName(),
                                                  new PyConsoleSpecificOptionsPanel(myProject),
                                                  provider.getSettings(myProject),
                                                  provider.getHelpTopic()));
      }
    }

    return result.toArray(new Configurable[0]);
  }

  private static Configurable createConsoleChildConfigurable(final @NlsContexts.ConfigurableName String name,
                                                             final PyConsoleSpecificOptionsPanel panel,
                                                             final PyConsoleOptions.PyConsoleSettings settings, final String helpReference) {
    return new SearchableConfigurable() {

      @Override
      public @NotNull String getId() {
        return "PyConsoleConfigurable." + name;
      }

      @Override
      public @Nls String getDisplayName() {
        return name;
      }

      @Override
      public String getHelpTopic() {
        return helpReference;
      }

      @Override
      public JComponent createComponent() {
        return panel.createPanel(settings);
      }

      @Override
      public boolean isModified() {
        return panel.isModified();
      }

      @Override
      public void apply() {
        panel.apply();
      }

      @Override
      public void reset() {
        panel.reset();
      }
    };
  }

  @Override
  public @Nls String getDisplayName() {
    return PyBundle.message("configurable.PyConsoleOptionsConfigurable.display.name");
  }

  @Override
  public String getHelpTopic() {
    return CONSOLE_SETTINGS_HELP_REFERENCE;
  }

  @Override
  public JComponent createComponent() {
    myPanel = new PyConsoleOptionsPanel();

    return myPanel.createPanel(PyConsoleOptions.getInstance(myProject));
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void apply() {
    myPanel.apply();
  }


  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  private static class PyConsoleOptionsPanel {
    private final JPanel myWholePanel;
    private final JBCheckBox myShowDebugConsoleByDefault;
    private final JBCheckBox myIpythonEnabledCheckbox;
    private final JBCheckBox myShowsVariablesByDefault;
    private final JBCheckBox myUseExistingConsole;
    private final JBCheckBox myCommandQueueEnabledCheckbox;
    private final ComboBox<CodeCompletionOption> myCodeCompletionComboBox;
    private final JBLabel myCodeCompletionLabel;

    public PyConsoleOptionsPanel() {
      {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        myWholePanel = new JPanel();
        myWholePanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        final Spacer spacer1 = new Spacer();
        myWholePanel.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                      GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(7, 4, new Insets(0, 0, 0, 0), -1, -1));
        panel1.putClientProperty("BorderFactoryClass", "com.intellij.ui.IdeBorderFactory$PlainSmallWithIndent");
        myWholePanel.add(panel1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                     null,
                                                     null, 0, false));
        panel1.setBorder(IdeBorderFactory.PlainSmallWithIndent.createTitledBorder(null, this.$$$getMessageFromBundle$$$("messages/PyBundle",
                                                                                                                        "form.console.options.settings.title.system.settings"),
                                                                                  TitledBorder.DEFAULT_JUSTIFICATION,
                                                                                  TitledBorder.DEFAULT_POSITION, null, null));
        myShowDebugConsoleByDefault = new JBCheckBox();
        this.$$$loadButtonText$$$(myShowDebugConsoleByDefault,
                                  this.$$$getMessageFromBundle$$$("messages/PyBundle", "form.console.options.always.show.debug.console"));
        panel1.add(myShowDebugConsoleByDefault,
                   new GridConstraints(0, 0, 1, 4, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer2 = new Spacer();
        panel1.add(spacer2, new GridConstraints(5, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        myIpythonEnabledCheckbox = new JBCheckBox();
        this.$$$loadButtonText$$$(myIpythonEnabledCheckbox,
                                  this.$$$getMessageFromBundle$$$("messages/PyBundle", "form.console.options.use.ipython.if.available"));
        panel1.add(myIpythonEnabledCheckbox,
                   new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myShowsVariablesByDefault = new JBCheckBox();
        this.$$$loadButtonText$$$(myShowsVariablesByDefault, this.$$$getMessageFromBundle$$$("messages/PyBundle",
                                                                                             "form.console.options.show.console.variables.by.default"));
        panel1.add(myShowsVariablesByDefault,
                   new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myUseExistingConsole = new JBCheckBox();
        this.$$$loadButtonText$$$(myUseExistingConsole, this.$$$getMessageFromBundle$$$("messages/PyBundle",
                                                                                        "form.console.options.use.existing.console.for.run.with.python.console"));
        panel1.add(myUseExistingConsole,
                   new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCommandQueueEnabledCheckbox = new JBCheckBox();
        myCommandQueueEnabledCheckbox.setSelected(true);
        this.$$$loadButtonText$$$(myCommandQueueEnabledCheckbox,
                                  this.$$$getMessageFromBundle$$$("messages/PyBundle", "form.console.options.use.command.queue"));
        panel1.add(myCommandQueueEnabledCheckbox,
                   new GridConstraints(4, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCodeCompletionComboBox = new ComboBox();
        panel1.add(myCodeCompletionComboBox, new GridConstraints(6, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                 GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                 null,
                                                                 null, null, 0, false));
        myCodeCompletionLabel = new JBLabel();
        this.$$$loadLabelText$$$(myCodeCompletionLabel,
                                 this.$$$getMessageFromBundle$$$("messages/PyBundle", "form.console.options.code.completion"));
        panel1.add(myCodeCompletionLabel,
                   new GridConstraints(6, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                       GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer3 = new Spacer();
        myWholePanel.add(spacer3, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                      GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
      }
    }

    private static Method $$$cachedGetBundleMethod$$$ = null;

    /** @noinspection ALL */
    private String $$$getMessageFromBundle$$$(String path, String key) {
      ResourceBundle bundle;
      try {
        Class<?> thisClass = this.getClass();
        if ($$$cachedGetBundleMethod$$$ == null) {
          Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
          $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
        }
        bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
      }
      catch (Exception e) {
        bundle = ResourceBundle.getBundle(path);
      }
      return bundle.getString(key);
    }

    /** @noinspection ALL */
    private void $$$loadLabelText$$$(JLabel component, String text) {
      StringBuffer result = new StringBuffer();
      boolean haveMnemonic = false;
      char mnemonic = '\0';
      int mnemonicIndex = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '&') {
          i++;
          if (i == text.length()) break;
          if (!haveMnemonic && text.charAt(i) != '&') {
            haveMnemonic = true;
            mnemonic = text.charAt(i);
            mnemonicIndex = result.length();
          }
        }
        result.append(text.charAt(i));
      }
      component.setText(result.toString());
      if (haveMnemonic) {
        component.setDisplayedMnemonic(mnemonic);
        component.setDisplayedMnemonicIndex(mnemonicIndex);
      }
    }

    /** @noinspection ALL */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
      StringBuffer result = new StringBuffer();
      boolean haveMnemonic = false;
      char mnemonic = '\0';
      int mnemonicIndex = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '&') {
          i++;
          if (i == text.length()) break;
          if (!haveMnemonic && text.charAt(i) != '&') {
            haveMnemonic = true;
            mnemonic = text.charAt(i);
            mnemonicIndex = result.length();
          }
        }
        result.append(text.charAt(i));
      }
      component.setText(result.toString());
      if (haveMnemonic) {
        component.setMnemonic(mnemonic);
        component.setDisplayedMnemonicIndex(mnemonicIndex);
      }
    }

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() { return myWholePanel; }

    private PyConsoleOptions myOptionsProvider;

    public JPanel createPanel(PyConsoleOptions optionsProvider) {
      myOptionsProvider = optionsProvider;
      Arrays.stream(CodeCompletionOption.values()).forEach(e -> myCodeCompletionComboBox.addItem(e));

      return myWholePanel;
    }

    public void apply() {
      myOptionsProvider.setShowDebugConsoleByDefault(myShowDebugConsoleByDefault.isSelected());
      myOptionsProvider.setIpythonEnabled(myIpythonEnabledCheckbox.isSelected());
      myOptionsProvider.setShowVariablesByDefault(myShowsVariablesByDefault.isSelected());
      myOptionsProvider.setUseExistingConsole(myUseExistingConsole.isSelected());
      myOptionsProvider.setCommandQueueEnabled(myCommandQueueEnabledCheckbox.isSelected());
      Object selectedCodeCompletion = myCodeCompletionComboBox.getSelectedItem();
      if (selectedCodeCompletion instanceof CodeCompletionOption) {
        myOptionsProvider.setCodeCompletionOption((CodeCompletionOption)selectedCodeCompletion);
      }
    }

    public void reset() {
      myShowDebugConsoleByDefault.setSelected(myOptionsProvider.isShowDebugConsoleByDefault());
      myIpythonEnabledCheckbox.setSelected(myOptionsProvider.isIpythonEnabled());
      myShowsVariablesByDefault.setSelected(myOptionsProvider.isShowVariableByDefault());
      myUseExistingConsole.setSelected(myOptionsProvider.isUseExistingConsole());
      myCommandQueueEnabledCheckbox.setSelected(myOptionsProvider.isCommandQueueEnabled());
      myCodeCompletionComboBox.setSelectedItem(myOptionsProvider.getCodeCompletionOption());
    }

    public boolean isModified() {
      return myShowDebugConsoleByDefault.isSelected() != myOptionsProvider.isShowDebugConsoleByDefault() ||
             myIpythonEnabledCheckbox.isSelected() != myOptionsProvider.isIpythonEnabled() ||
             myShowsVariablesByDefault.isSelected() != myOptionsProvider.isShowVariableByDefault() ||
             myUseExistingConsole.isSelected() != myOptionsProvider.isUseExistingConsole() ||
             myCommandQueueEnabledCheckbox.isSelected() != myOptionsProvider.isCommandQueueEnabled() ||
             myCodeCompletionComboBox.getSelectedItem() != myOptionsProvider.getCodeCompletionOption();
    }
  }
}
