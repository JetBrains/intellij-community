// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.DispatchThreadProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.make.FormSourceCodeGenerator;
import com.intellij.uiDesigner.radComponents.LayoutManagerRegistry;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class GuiDesignerConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private static final Logger LOG = Logger.getInstance(GuiDesignerConfigurable.class);
  private final Project myProject;
  private MyGeneralUI myGeneralUI;

  /**
   * Invoked by reflection
   */
  public GuiDesignerConfigurable(final Project project) {
    myProject = project;
  }

  @Override
  public String getDisplayName() {
    return UIDesignerBundle.message("title.gui.designer");
  }

  @Override
  public @NotNull String getHelpTopic() {
    return "project.propGUI";
  }

  @Override
  public JComponent createComponent() {
    if (myGeneralUI == null) {
      myGeneralUI = new MyGeneralUI();
    }

    return myGeneralUI.myPanel;
  }

  @Override
  public boolean isModified() {
    final GuiDesignerConfiguration configuration = GuiDesignerConfiguration.getInstance(myProject);

    if (myGeneralUI == null) {
      return false;
    }

    if (myGeneralUI.myChkCopyFormsRuntime.isSelected() != configuration.COPY_FORMS_RUNTIME_TO_OUTPUT) {
      return true;
    }
    
    if (myGeneralUI.myChkUseDynamicBundles.isSelected() != configuration.USE_DYNAMIC_BUNDLES) {
      return true;
    }

    if (myGeneralUI.myChkGenerateSourceCodeOnFormSave.isSelected() != configuration.GENERATE_SOURCES_ON_SAVE) {
      return true;
    }

    if (!Comparing.equal(configuration.DEFAULT_LAYOUT_MANAGER, myGeneralUI.myLayoutManagerCombo.getSelectedItem())) {
      return true;
    }

    if (!Comparing.equal(configuration.DEFAULT_FIELD_ACCESSIBILITY, myGeneralUI.myDefaultFieldAccessibilityCombo.getSelectedItem())) {
      return true;
    }

    if (configuration.INSTRUMENT_CLASSES != myGeneralUI.myRbInstrumentClasses.isSelected()) {
      return true;
    }

    if (configuration.RESIZE_HEADERS != myGeneralUI.myResizeHeaders.isSelected()) {
      return true;
    }

    return false;
  }

  @Override
  public void apply() {
    final GuiDesignerConfiguration configuration = GuiDesignerConfiguration.getInstance(myProject);
    configuration.COPY_FORMS_RUNTIME_TO_OUTPUT = myGeneralUI.myChkCopyFormsRuntime.isSelected();
    configuration.DEFAULT_LAYOUT_MANAGER = (String)myGeneralUI.myLayoutManagerCombo.getSelectedItem();
    configuration.INSTRUMENT_CLASSES = myGeneralUI.myRbInstrumentClasses.isSelected();
    configuration.DEFAULT_FIELD_ACCESSIBILITY = (String)myGeneralUI .myDefaultFieldAccessibilityCombo.getSelectedItem();
    configuration.RESIZE_HEADERS = myGeneralUI.myResizeHeaders.isSelected();
    configuration.USE_DYNAMIC_BUNDLES = myGeneralUI.myChkUseDynamicBundles.isSelected();
    configuration.GENERATE_SOURCES_ON_SAVE = myGeneralUI.myChkGenerateSourceCodeOnFormSave.isSelected();

    if (configuration.INSTRUMENT_CLASSES && !myProject.isDefault()) {
      final DispatchThreadProgressWindow progressWindow = new DispatchThreadProgressWindow(false, myProject);
      progressWindow.setRunnable(new MyApplyRunnable(progressWindow));
      progressWindow.setTitle(UIDesignerBundle.message("title.converting.project"));
      progressWindow.start();
    }
  }

  @Override
  public void reset() {
    final GuiDesignerConfiguration configuration = GuiDesignerConfiguration.getInstance(myProject);

    /*general*/
    if (configuration.INSTRUMENT_CLASSES) {
      myGeneralUI.myRbInstrumentClasses.setSelected(true);
    }
    else {
      myGeneralUI.myRbInstrumentSources.setSelected(true);
    }
    myGeneralUI.myChkCopyFormsRuntime.setSelected(configuration.COPY_FORMS_RUNTIME_TO_OUTPUT);
    myGeneralUI.myChkUseDynamicBundles.setSelected(configuration.USE_DYNAMIC_BUNDLES);
    myGeneralUI.myChkGenerateSourceCodeOnFormSave.setSelected(configuration.GENERATE_SOURCES_ON_SAVE);

    myGeneralUI.myLayoutManagerCombo.setModel(new DefaultComboBoxModel<>(LayoutManagerRegistry.getNonDeprecatedLayoutManagerNames()));
    myGeneralUI.myLayoutManagerCombo.setRenderer(SimpleListCellRenderer.create("", LayoutManagerRegistry::getLayoutManagerDisplayName));
    myGeneralUI.myLayoutManagerCombo.setSelectedItem(configuration.DEFAULT_LAYOUT_MANAGER);

    myGeneralUI.myDefaultFieldAccessibilityCombo.setSelectedItem(configuration.DEFAULT_FIELD_ACCESSIBILITY);

    myGeneralUI.myResizeHeaders.setSelected(configuration.RESIZE_HEADERS);
  }

  @Override
  public void disposeUIResources() {
    myGeneralUI = null;
  } /*UI for "General" tab*/

  private static final class MyGeneralUI {
    public JPanel myPanel;
    public JRadioButton myRbInstrumentClasses;
    public JRadioButton myRbInstrumentSources;
    public JCheckBox myChkCopyFormsRuntime;
    public JCheckBox myChkUseDynamicBundles;
    public JCheckBox myChkGenerateSourceCodeOnFormSave;
    private JComboBox<String> myLayoutManagerCombo;
    private JComboBox<String> myDefaultFieldAccessibilityCombo;
    private JCheckBox myResizeHeaders;
  }

  private final class MyApplyRunnable implements Runnable {
    private final DispatchThreadProgressWindow myProgressWindow;

    MyApplyRunnable(final DispatchThreadProgressWindow progressWindow) {
      myProgressWindow = progressWindow;
    }

    /**
     * Removes all generated sources
     */
    private void vanishGeneratedSources() {
      final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(myProject);
      final PsiMethod[] methods = cache.getMethodsByName(AsmCodeGenerator.SETUP_METHOD_NAME, GlobalSearchScope.projectScope(myProject));

      CodeInsightUtil.preparePsiElementsForWrite(methods);

      for (int i = 0; i < methods.length; i++) {
        final PsiMethod method = methods[i];
        final PsiClass aClass = method.getContainingClass();
        if (aClass != null) {
          try {
            final PsiFile psiFile = aClass.getContainingFile();
            LOG.assertTrue(psiFile != null);
            final VirtualFile vFile = psiFile.getVirtualFile();
            LOG.assertTrue(vFile != null);
            myProgressWindow.setText(UIDesignerBundle.message("progress.converting", vFile.getPresentableUrl()));
            myProgressWindow.setFraction(((double)i) / ((double)methods.length));
            if (vFile.isWritable()) {
              WriteAction.run(() -> FormSourceCodeGenerator.cleanup(aClass));
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }

    /**
     * Launches vanish/generate sources processes
     */
    private void applyImpl() {
      CommandProcessor.getInstance().executeCommand(myProject, () -> {
        PsiDocumentManager.getInstance(myProject).commitAllDocuments();
        vanishGeneratedSources();
      }, "", null);
    }

    @Override
    public void run() {
      ProgressManager.getInstance().runProcess(() -> applyImpl(), myProgressWindow);
    }
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }
}
