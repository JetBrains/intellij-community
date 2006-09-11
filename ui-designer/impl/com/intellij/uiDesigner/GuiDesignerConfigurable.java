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
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.make.FormSourceCodeGenerator;
import com.intellij.uiDesigner.radComponents.LayoutManagerRegistry;
import com.intellij.util.IncorrectOperationException;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;

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

  @NotNull
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

  @NotNull
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

    if (!Comparing.equal(configuration.DEFAULT_LAYOUT_MANAGER, myGeneralUI.myLayoutManagerCombo.getSelectedItem())) {
      return true;
    }

    if (configuration.INSTRUMENT_CLASSES != myGeneralUI.myRbInstrumentClasses.isSelected()) {
      return true;
    }

    return false;
  }

  public void apply() {
    final GuiDesignerConfiguration configuration = GuiDesignerConfiguration.getInstance(myProject);
    configuration.COPY_FORMS_RUNTIME_TO_OUTPUT = myGeneralUI.myChkCopyFormsRuntime.isSelected();
    configuration.DEFAULT_LAYOUT_MANAGER = (String)myGeneralUI.myLayoutManagerCombo.getSelectedItem();
    configuration.INSTRUMENT_CLASSES = myGeneralUI.myRbInstrumentClasses.isSelected();

    if (configuration.INSTRUMENT_CLASSES && !myProject.isDefault()) {
      final DispatchThreadProgressWindow progressWindow = new DispatchThreadProgressWindow(false, myProject);
      progressWindow.setRunnable(new MyApplyRunnable(progressWindow));
      progressWindow.setTitle(UIDesignerBundle.message("title.converting.project"));
      progressWindow.start();
    }
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

    myGeneralUI.myLayoutManagerCombo.setModel(new DefaultComboBoxModel(LayoutManagerRegistry.getLayoutManagerNames()));
    myGeneralUI.myLayoutManagerCombo.setRenderer(new ColoredListCellRenderer() {
        protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          append(LayoutManagerRegistry.getLayoutManagerDisplayName((String) value), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
      });
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
    private JComboBox myLayoutManagerCombo;
  }

  private final class MyApplyRunnable implements Runnable {
    private final DispatchThreadProgressWindow myProgressWindow;

    public MyApplyRunnable(final DispatchThreadProgressWindow progressWindow) {
      myProgressWindow = progressWindow;
    }

    /**
     * Removes all generated sources
     */
    private void vanishGeneratedSources() {
      final PsiShortNamesCache cache = PsiManager.getInstance(myProject).getShortNamesCache();
      final PsiMethod[] methods = cache.getMethodsByName(AsmCodeGenerator.SETUP_METHOD_NAME, GlobalSearchScope.projectScope(myProject));
      final ArrayList<VirtualFile> vFiles = new ArrayList<VirtualFile>();

      for(PsiMethod method: methods) {
        PsiFile file = method.getContainingFile();
        if (file != null) {
          VirtualFile vFile = file.getVirtualFile();
          if (vFile != null) {
            vFiles.add(vFile);
          }
        }
      }

      ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(vFiles.toArray(new VirtualFile[vFiles.size()]));

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
              FormSourceCodeGenerator.cleanup(aClass);
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

    public void run() {
      ProgressManager.getInstance().runProcess(new Runnable() {
        public void run() {
          applyImpl();
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
