package com.intellij.ide.fileTemplates.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.actions.EditFileTemplatesAction;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog;
import com.intellij.ide.fileTemplates.ui.SelectTemplateDialog;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.apache.velocity.runtime.parser.ParseException;

import java.util.*;

public class CreateFromTemplateGroup extends ActionGroup{
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.fileTemplates.actions.CreateFromTemplateGroup");

  public void update(AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    FileTemplate[] allTemplates = FileTemplateManager.getInstance().getAllTemplates();
    for (int i = 0; i < allTemplates.length; i++){
      FileTemplate template = allTemplates[i];
      if(canCreateFromTemplate(event, template)){
        presentation.setEnabled(true);
        return;
      }
    }
    presentation.setEnabled(false);
  }

  public AnAction[] getChildren(AnActionEvent e){
    FileTemplateManager manager = FileTemplateManager.getInstance();
    FileTemplate[] templates;
    templates = manager.getAllTemplates();
    List<AnAction> result = new ArrayList<AnAction>();

    boolean showAll = templates.length <= FileTemplateManager.RECENT_TEMPLATES_SIZE;
    if (!showAll) {
      List recentNames = manager.getRecentNames();
      templates = new FileTemplate[recentNames.size()];
      int i = 0;
      for (Iterator iterator = recentNames.iterator(); iterator.hasNext(); i++) {
        String name = (String)iterator.next();
        templates[i] = FileTemplateManager.getInstance().getTemplate(name);
      }
    }

    Arrays.sort(templates, new Comparator<FileTemplate>() {
      public int compare(FileTemplate template1, FileTemplate template2) {
        // java first
        if (template1.isJavaClassTemplate() && !template2.isJavaClassTemplate()) {
          return -1;
        }
        if (template2.isJavaClassTemplate() && !template1.isJavaClassTemplate()) {
          return 1;
        }

        // group by type
        int i = template1.getExtension().compareTo(template2.getExtension());
        if (i != 0) {
          return i;
        }

        // group by name if same type
        return template1.getName().compareTo(template2.getName());
      }
    });

    for (int i = 0; i < templates.length; i++){
      FileTemplate template = templates[i];
      if(canCreateFromTemplate(e, template)){
        CreateFromTemplateAction action = new CreateFromTemplateAction(template);
        result.add(action);
      }
    }

    if (!result.isEmpty()) {
      if (!showAll) {
        result.add(new CreateFromTemplatesAction("From File Template ..."));
      }

      result.add(Separator.getInstance());
      result.add(new EditFileTemplatesAction("Edit File Templates..."));
    }

    return result.toArray(new AnAction[result.size()]);
}

  private boolean canCreateFromTemplate(AnActionEvent e, FileTemplate template){
    DataContext dataContext = e.getDataContext();
    IdeView view = (IdeView)dataContext.getData(DataConstantsEx.IDE_VIEW);
    if (view == null) return false;

    PsiDirectory[] dirs = view.getDirectories();
    if (dirs.length == 0) return false;

    return FileTemplateUtil.canCreateFromTemplate(dirs, template);
  }

  private class CreateFromTemplatesAction extends AnAction{

    public CreateFromTemplatesAction(String title){
      super(title);
    }

    public final void actionPerformed(AnActionEvent e){
      DataContext dataContext = e.getDataContext();

      IdeView view = (IdeView)dataContext.getData(DataConstantsEx.IDE_VIEW);
      if (view == null) {
        return;
      }
      Project project = (Project)dataContext.getData(DataConstants.PROJECT);

      PsiDirectory dir = PackageUtil.getOrChooseDirectory(view);
      if (dir == null) return;

      FileTemplate[] allTemplates = FileTemplateManager.getInstance().getAllTemplates();
      List<FileTemplate> available = new ArrayList<FileTemplate>();
      for (int i = 0; i < allTemplates.length; i++) {
        FileTemplate template = allTemplates[i];
        if (canCreateFromTemplate(e, template)) {
          available.add(template);
        }
      }

      SelectTemplateDialog dialog = new SelectTemplateDialog(project, dir);
      dialog.show();
      FileTemplate selectedTemplate = dialog.getSelectedTemplate();
      if(selectedTemplate != null){
        PsiElement createdElement = showCreateFromTemplateDialog(project, dir, selectedTemplate);
        if (createdElement != null){
          view.selectElement(createdElement);
        }
      }
    }

  }

  private PsiElement showCreateFromTemplateDialog(Project project, PsiDirectory directory, FileTemplate template){
    CreateFromTemplateDialog dialog;
    try{
      dialog = new CreateFromTemplateDialog(project, directory, template);
    }
    catch (ParseException ex){
      String message = "Unable to parse template \""+template.getName()+"\"";
      message += "\nError message: "+ ex.getMessage();
      Messages.showMessageDialog(project, message, "Invalid Template", Messages.getErrorIcon());
      LOG.debug(message);
      LOG.debug(ex);
      return null;
    }
    if(needToShowDialog(template)){
      dialog.show();
      return dialog.getCreatedElement();
    }
    else{
      Properties defaultProperties = FileTemplateManager.getInstance().getDefaultProperties();
      Properties properties = new Properties(defaultProperties);
      String fileName = null;
      LOG.assertTrue(template.isJavaClassTemplate());
      if(template.isJavaClassTemplate()){
        String packageName = directory.getPackage().getQualifiedName();
        properties.setProperty(FileTemplateUtil.PACKAGE_ATTR, packageName);
      }
      PsiElement[] element = new PsiElement[1];
      try{
        FileTemplateUtil.createFromTemplate(element, template, fileName, properties, project, directory);
      }
      catch (Exception e){
        Messages.showMessageDialog(project, e.getMessage(), "Cannot Create " + (template.isJavaClassTemplate() ? "Class" : "File"), Messages.getErrorIcon());
      }
      return element[0];
    }
  }

  /**
   * @return false if all template attributes can be defined.
   */
  private boolean needToShowDialog(FileTemplate template){
    if(template == null) return false;
    if(!template.isJavaClassTemplate()) return true;  //Will show filename attribute

    Properties defaultProperties = FileTemplateManager.getInstance().getDefaultProperties();
    defaultProperties.put(FileTemplateUtil.PACKAGE_ATTR, FileTemplateUtil.PACKAGE_ATTR);

    try {
      return template.getUnsetAttributes(defaultProperties).length > 0;
    } catch (ParseException e) {
      throw new RuntimeException("Unable to parse template \""+template.getName()+"\"", e);
    }
  }


  private class CreateFromTemplateAction extends AnAction{
    private FileTemplate myTemplate;

    public CreateFromTemplateAction(FileTemplate template){
      super(template.getName(), null, FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(template.getExtension()).getIcon());
      myTemplate = template;
    }

    public final void actionPerformed(AnActionEvent e){
      DataContext dataContext = e.getDataContext();

      IdeView view = (IdeView)dataContext.getData(DataConstantsEx.IDE_VIEW);
      if (view == null) {
        return;
      }
      Project project = (Project)dataContext.getData(DataConstants.PROJECT);

      FileTemplateManager.getInstance().addRecentName(myTemplate.getName());
      PsiDirectory dir = PackageUtil.getOrChooseDirectory(view);
      if (dir == null) return;
      PsiElement createdElement = showCreateFromTemplateDialog(project, dir, myTemplate);
      if (createdElement != null){
        view.selectElement(createdElement);
      }
    }

    public void update(AnActionEvent e){
      super.update(e);
      Presentation presentation = e.getPresentation();
      boolean isEnabled = canCreateFromTemplate(e, myTemplate);
      presentation.setEnabled(isEnabled);
      presentation.setVisible(isEnabled);
    }
  }
}
