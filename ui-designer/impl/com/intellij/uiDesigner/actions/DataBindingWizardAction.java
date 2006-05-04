package com.intellij.uiDesigner.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiClass;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.LwComponent;
import com.intellij.uiDesigner.lw.LwContainer;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.wizard.DataBindingWizard;
import com.intellij.uiDesigner.wizard.Generator;
import com.intellij.uiDesigner.wizard.WizardData;
import com.intellij.CommonBundle;

import java.text.MessageFormat;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class DataBindingWizardAction extends AnAction{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.DataBindingWizardAction");

  private final GuiEditor myEditor;

  public DataBindingWizardAction() {
    myEditor = null;
  }

  public DataBindingWizardAction(final GuiEditor editor) {
    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_DATA_BINDING_WIZARD));
    myEditor = editor;
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project;
    final VirtualFile formFile;
    if (myEditor == null) {
      final DataContext context = e.getDataContext();
      project = (Project)context.getData(DataConstants.PROJECT);
      formFile = (VirtualFile)context.getData(DataConstants.VIRTUAL_FILE);
    }
    else {
      project = myEditor.getProject();
      formFile = myEditor.getFile();
    }

    try {
      final WizardData wizardData = new WizardData(project, formFile);


      final Module module = VfsUtil.getModuleForFile(wizardData.myProject, formFile);
      LOG.assertTrue(module != null);

      final LwRootContainer[] rootContainer = new LwRootContainer[1];
      Generator.exposeForm(wizardData.myProject, formFile, rootContainer);
      final String classToBind = rootContainer[0].getClassToBind();
      if (classToBind == null) {
        Messages.showInfoMessage(
          project,
          UIDesignerBundle.message("info.form.not.bound"),
          UIDesignerBundle.message("title.data.binding.wizard")
        );
        return;
      }

      final PsiClass boundClass = FormEditingUtil.findClassToBind(module, classToBind);
      if(boundClass == null){
        Messages.showErrorDialog(
          project,
          UIDesignerBundle.message("error.bound.to.not.found.class", classToBind),
          UIDesignerBundle.message("title.data.binding.wizard")
        );
        return;
      }

      Generator.prepareWizardData(wizardData, boundClass);

      if(!hasBinding(rootContainer[0])){
        Messages.showInfoMessage(
          project,
          UIDesignerBundle.message("info.no.bound.components"),
          UIDesignerBundle.message("title.data.binding.wizard")
        );
        return;
      }

      if (!wizardData.myBindToNewBean) {
        final String[] variants = new String[]{UIDesignerBundle.message("action.alter.data.binding"),
          UIDesignerBundle.message("action.bind.to.another.bean"), CommonBundle.getCancelButtonText()};
        final int result = Messages.showDialog(
          project,
          MessageFormat.format(UIDesignerBundle.message("info.data.binding.regenerate"),
                               wizardData.myBeanClass.getQualifiedName()),
          UIDesignerBundle.message("title.data.binding"),
          variants,
          0,
          Messages.getQuestionIcon()
        );
        if (result == 0) {
          // do nothing here
        }
        else if (result == 1) {
          wizardData.myBindToNewBean = true;
        }
        else {
          return;
        }
      }

      final DataBindingWizard wizard = new DataBindingWizard(project, formFile, wizardData);
      wizard.show();
    }
    catch (Generator.MyException exc) {
      Messages.showErrorDialog(
        project,
        exc.getMessage(),
        CommonBundle.getErrorTitle()
      );
    }
  }

  public void update(final AnActionEvent e) {
    if (myEditor != null) {
      e.getPresentation().setEnabled(true);
      return;
    }

    final DataContext context = e.getDataContext();
    final Project project = (Project)context.getData(DataConstants.PROJECT);
    if(project == null){
      e.getPresentation().setVisible(false);
      return;
    }

    final VirtualFile vFile = (VirtualFile)context.getData(DataConstants.VIRTUAL_FILE);
    if (vFile == null) {
      e.getPresentation().setVisible(false);
      return;
    }
    final FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(vFile);
    if(!StdFileTypes.GUI_DESIGNER_FORM.equals(fileType)){
      e.getPresentation().setVisible(false);
      return;
    }
    e.getPresentation().setVisible(true);
  }


  private static boolean hasBinding(final LwComponent component) {
    if (component.getBinding() != null) {
      return true;
    }

    if (component instanceof LwContainer) {
      final LwContainer container = (LwContainer)component;
      for (int i=0; i < container.getComponentCount(); i++) {
        if (hasBinding((LwComponent)container.getComponent(i))) {
          return true;
        }
      }
    }

    return false;
  }
}
