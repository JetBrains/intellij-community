package com.intellij.uiDesigner;

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.DispatchThreadProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.uiDesigner.make.FormSourceCodeGenerator;
import com.intellij.util.IncorrectOperationException;

import javax.swing.*;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class GuiDesignerConfigurable implements SearchableConfigurable, ProjectComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.GuiDesignerConfigurable");
  private final Project myProject;
  private MyGeneralUI myGeneralUI;
  private GlassPanel myGlassPanel;

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
    LOG.assertTrue(myGeneralUI == null);

    myGeneralUI = new MyGeneralUI();

    myGlassPanel = new GlassPanel(myGeneralUI.myPanel);
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

    if (configuration.INSTRUMENT_CLASSES != myGeneralUI.myRbInstrumentClasses.isSelected()) {
      return true;
    }

    return false;
  }

  public void apply() {
    final DispatchThreadProgressWindow progressWindow = new DispatchThreadProgressWindow(false, myProject);
    progressWindow.setTitle(UIDesignerBundle.message("title.converting.project"));
    progressWindow.start();

    GuiDesignerConfiguration.getInstance(myProject).COPY_FORMS_RUNTIME_TO_OUTPUT = myGeneralUI.myChkCopyFormsRuntime.isSelected();
    GuiDesignerConfiguration.getInstance(myProject).IRIDA_LAYOUT_MODE = myGeneralUI.myIridaCompatibleLayout.isSelected();

    // We have to store value of the radio button here because myGeneralUI will be cleared
    // just after apply is invoked (applyImpl is invoked later)
    final boolean instrumentClasses = myGeneralUI.myRbInstrumentClasses.isSelected();
    ApplicationManager.getApplication()
      .invokeLater(new MyApplyRunnable(progressWindow, instrumentClasses), progressWindow.getModalityState());
  }

  public void reset() {
    myGeneralUI.myPanel.getRootPane().setGlassPane(myGlassPanel);
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

    public MyGeneralUI() {
      final ButtonGroup group = new ButtonGroup();
      group.add(myRbInstrumentClasses);
      group.add(myRbInstrumentSources);
    }
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
      final PsiMethod[] methods = cache.getMethodsByName(FormSourceCodeGenerator.METHOD_NAME, GlobalSearchScope.projectScope(myProject));

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

  public Runnable showOption(String option) {
    return SearchUtil.lightOptions(myGeneralUI.myPanel, option, myGlassPanel);
  }

  public String getId() {
    return getHelpTopic();
  }

  public void clearSearch() {
    myGlassPanel.clear();
  }
}
