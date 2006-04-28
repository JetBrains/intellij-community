package com.intellij.uiDesigner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.DispatchThreadProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.make.FormSourceCodeGenerator;
import com.intellij.uiDesigner.radComponents.LayoutManagerRegistry;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class GuiDesignerConfigurable implements SearchableConfigurable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.GuiDesignerConfigurable");
  private final Project myProject;
  private MyGeneralUI myGeneralUI;

  /**
   * Invoked by reflection
   */
  public GuiDesignerConfigurable(final Project project) {
    myProject = project;
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public String getComponentName() {
    return "uidesigner-configurable";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public String getDisplayName() {
    return UIDesignerBundle.message("title.gui.designer");
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/uiDesigner.png");
  }

  public String getHelpTopic() {
    return "project.propGUI";
  }

  public JComponent createComponent() {
    if (myGeneralUI == null) {
      myGeneralUI = new MyGeneralUI();
    }

    return myGeneralUI.myPanel;
  }

  public boolean isModified() {
    final GuiDesignerConfiguration configuration = GuiDesignerConfiguration.getInstance(myProject);

    if (myGeneralUI.myChkCopyFormsRuntime.isSelected() != configuration.COPY_FORMS_RUNTIME_TO_OUTPUT) {
      return true;
    }
    if (myGeneralUI.myIridaCompatibleLayout.isSelected() != configuration.IRIDA_LAYOUT_MODE) {
      return true;
    }

    if (!Comparing.equal(configuration.DEFAULT_LAYOUT_MANAGER, myGeneralUI.myLayoutManagerCombo.getSelectedItem())) {
      return true;
    }

    if (configuration.INSTRUMENT_CLASSES != myGeneralUI.myRbInstrumentClasses.isSelected()) {
      return true;
    }

    return false;
  }

  public void apply() {
    final DispatchThreadProgressWindow progressWindow = new DispatchThreadProgressWindow(false, myProject);
    progressWindow.setTitle(UIDesignerBundle.message("title.converting.project"));
    progressWindow.start();

    final GuiDesignerConfiguration configuration = GuiDesignerConfiguration.getInstance(myProject);
    configuration.COPY_FORMS_RUNTIME_TO_OUTPUT = myGeneralUI.myChkCopyFormsRuntime.isSelected();
    configuration.IRIDA_LAYOUT_MODE = myGeneralUI.myIridaCompatibleLayout.isSelected();
    configuration.DEFAULT_LAYOUT_MANAGER = (String)myGeneralUI.myLayoutManagerCombo.getSelectedItem();

    // We have to store value of the radio button here because myGeneralUI will be cleared
    // just after apply is invoked (applyImpl is invoked later)
    final boolean instrumentClasses = myGeneralUI.myRbInstrumentClasses.isSelected();
    ApplicationManager.getApplication()
      .invokeLater(new MyApplyRunnable(progressWindow, instrumentClasses), progressWindow.getModalityState());
  }

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
    myGeneralUI.myIridaCompatibleLayout.setSelected(configuration.IRIDA_LAYOUT_MODE);

    myGeneralUI.myLayoutManagerCombo.setModel(new DefaultComboBoxModel(LayoutManagerRegistry.getLayoutManagerNames()));
    myGeneralUI.myLayoutManagerCombo.setSelectedItem(configuration.DEFAULT_LAYOUT_MANAGER);
  }

  public void disposeUIResources() {
    myGeneralUI = null;
  } /*UI for "General" tab*/

  private static final class MyGeneralUI {
    public JPanel myPanel;
    public JRadioButton myRbInstrumentClasses;
    public JRadioButton myRbInstrumentSources;
    public JCheckBox myChkCopyFormsRuntime;
    private JCheckBox myIridaCompatibleLayout;
    private JComboBox myLayoutManagerCombo;
  }

  private final class MyApplyRunnable implements Runnable {
    private final DispatchThreadProgressWindow myProgressWindow;
    private final boolean myInstrumentClasses;

    public MyApplyRunnable(final DispatchThreadProgressWindow progressWindow, final boolean instrumentClasses) {
      myProgressWindow = progressWindow;
      myInstrumentClasses = instrumentClasses;
    }

    /**
     * Removes all generated sources
     */
    private void vanishGeneratedSources() {
      final PsiShortNamesCache cache = PsiManager.getInstance(myProject).getShortNamesCache();
      final PsiMethod[] methods = cache.getMethodsByName(AsmCodeGenerator.SETUP_METHOD_NAME, GlobalSearchScope.projectScope(myProject));

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
            FormSourceCodeGenerator.cleanup(aClass);
          }
          catch (IncorrectOperationException e) {
            e.printStackTrace();
          }
        }
      }
    }

    /**
     * Launches vanish/generate sources processes
     */
    private void applyImpl(final boolean instrumentClasses) {
      final GuiDesignerConfiguration configuration = GuiDesignerConfiguration.getInstance(myProject);
      configuration.INSTRUMENT_CLASSES = instrumentClasses;

      if (configuration.INSTRUMENT_CLASSES && !myProject.isDefault()) {
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                PsiDocumentManager.getInstance(myProject).commitAllDocuments();
                vanishGeneratedSources();
              }
            });
          }
        }, "", null);
      }
    }

    public void run() {
      ProgressManager.getInstance().runProcess(new Runnable() {
        public void run() {
          applyImpl(myInstrumentClasses);
        }
      }, myProgressWindow);
    }
  }

  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    return false;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
