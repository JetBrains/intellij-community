package com.intellij.ide.fileTemplates.ui;

import com.intellij.ide.fileTemplates.impl.AllFileTemplatesConfigurable;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DimensionService;

import java.awt.*;

/*
 * @author: MYakovlev
 * Date: Oct 18, 2002
 * Time: 1:31:37 PM
 */

public class ConfigureTemplatesDialog extends SingleConfigurableEditor{
  private static final String DIMENSION_KEY = "#com.intellij.ide.fileTemplates.ui.ConfigureTemplatesDialog";

  public ConfigureTemplatesDialog(Project project){
    this(project, new AllFileTemplatesConfigurable());
  }

  public ConfigureTemplatesDialog(Project project, AllFileTemplatesConfigurable fileTemplatesConfigurable){
    super(project, fileTemplatesConfigurable);
    initSize();
  }

  protected String getDimensionServiceKey(){
    return DIMENSION_KEY;
  }

  private void initSize(){
    Dimension size = DimensionService.getInstance().getSize(DIMENSION_KEY, getProject());
    if (size == null){
      DimensionService.getInstance().setSize(DIMENSION_KEY, new Dimension(700, 500), getProject());
    }
  }
}
