/*
 * Class EditClassFiltersDialog
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.ClassFilter;
import com.intellij.debugger.ClassFilter;
import com.intellij.debugger.ui.ClassFilterEditor;
import com.intellij.debugger.ClassFilter;
import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.*;

public class EditClassFiltersDialog extends DialogWrapper {
  private ClassFilterEditor myClassFilterEditor;
  private ClassFilterEditor myClassExclusionFilterEditor;
  private Project myProject;
  private TreeClassChooser.ClassFilter myChooserFilter;

  public EditClassFiltersDialog(Project project) {
    this(project, null);
  }

  public EditClassFiltersDialog(Project project, TreeClassChooser.ClassFilter filter) {
    super(project, true);
    myChooserFilter = filter;
    myProject = project;
    setTitle("Class Filters");
    init();
  }


  protected JComponent createCenterPanel() {
    JPanel contentPanel = new JPanel(new BorderLayout());

    Box mainPanel = Box.createHorizontalBox();

    myClassFilterEditor = new ClassFilterEditor(myProject, myChooserFilter);
    myClassFilterEditor.setPreferredSize(new Dimension(400, 200));
    myClassFilterEditor.setBorder(IdeBorderFactory.createTitledBorder("Class Filters"));
    mainPanel.add(myClassFilterEditor);

    myClassExclusionFilterEditor = new ClassFilterEditor(myProject, myChooserFilter);
    myClassExclusionFilterEditor.setPreferredSize(new Dimension(400, 200));
    myClassExclusionFilterEditor.setBorder(IdeBorderFactory.createTitledBorder("Class Exclusion Filters"));
    mainPanel.add(myClassExclusionFilterEditor);

    contentPanel.add(mainPanel, BorderLayout.CENTER);

    return contentPanel;
  }

  protected void dispose(){
    myClassFilterEditor.stopEditing();
    super.dispose();
  }

  public void setFilters(ClassFilter[] filters, ClassFilter[] inverseFilters) {
    myClassFilterEditor.setFilters(filters);
    myClassExclusionFilterEditor.setFilters(inverseFilters);
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.debugger.ui.breakpoints.EditClassFiltersDialog";
  }

  public ClassFilter[] getFilters() {
    return myClassFilterEditor.getFilters();
  }

  public ClassFilter[] getExclusionFilters() {
    return myClassExclusionFilterEditor.getFilters();
  }
}