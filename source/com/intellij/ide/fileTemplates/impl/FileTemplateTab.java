package com.intellij.ide.fileTemplates.impl;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateUtil;

import javax.swing.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Alexey Kudravtsev
 */
abstract class FileTemplateTab {
  public Map<FileTemplate, FileTemplate> savedTemplates;
  private final String myTitle;

  protected FileTemplateTab(String title) {
    myTitle = title;
  }

  public abstract JComponent getComponent();

  public abstract FileTemplate getSelectedTemplate();

  public abstract void selectTemplate(FileTemplate template);

  public abstract void removeSelected();
  public abstract void onTemplateSelected();

  public void init(FileTemplate[] templates) {
    FileTemplate oldSelection = getSelectedTemplate();
    FileTemplate newSelection = null;
    Map<FileTemplate, FileTemplate> templatesToSave = new LinkedHashMap<FileTemplate, FileTemplate>();
    for (int i = 0; i < templates.length; i++) {
      FileTemplate aTemplate = templates[i];
      FileTemplate copy = FileTemplateUtil.cloneTemplate(aTemplate);
      templatesToSave.put(aTemplate, copy);
      if (savedTemplates != null && savedTemplates.get(aTemplate) == oldSelection) {
        newSelection = copy;
      }
    }
    savedTemplates = templatesToSave;
    initSelection(newSelection);
  }

  protected abstract void initSelection(FileTemplate selection);

  public abstract void fireDataChanged();

  public FileTemplate[] getTemplates() {
    return savedTemplates.values().toArray(new FileTemplate[savedTemplates.values().size()]);
  }

  public abstract void addTemplate(FileTemplate newTemplate);

  public String getTitle() {
    return myTitle;
  }

}
