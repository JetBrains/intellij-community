package com.intellij.structuralsearch.plugin;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchContext;
import com.intellij.structuralsearch.plugin.ui.SearchDialog;

public class StructuralSearchAction extends AnAction {
  // context of the search
  private SearchContext searchContext = new SearchContext();

  public StructuralSearchAction() {
    super(SSRBundle.message("structuralsearch.action"));
  }

  /** Handles IDEA action event
   * @param event the event of action
   */
  public void actionPerformed(AnActionEvent event) {
    try {
      searchContext.configureFromDataContext(event.getDataContext());

      triggerAction(null,searchContext);
    }
    finally {
      searchContext.setProject(null);
      searchContext.setFile(null);
      searchContext.setCurrentFile(null);
    }
  }

  public static void triggerAction(Configuration config, SearchContext searchContext) {
    final SearchDialog searchDialog = new SearchDialog(searchContext);

    if (config!=null) {
      searchDialog.setUseLastConfiguration(true);
      searchDialog.setValuesFromConfig(config);
    }

    searchDialog.show();
  }

  /** Updates the state of the action
   * @param event the action event
   */
  public void update(AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final DataContext context = event.getDataContext();
    final Project project = DataKeys.PROJECT.getData(context);
    final StructuralSearchPlugin plugin = project==null ? null:StructuralSearchPlugin.getInstance( project );

    if (plugin == null || plugin.isSearchInProgress()) {
      presentation.setEnabled( false );
    } else {
      presentation.setEnabled( true );
    }

    super.update(event);
  }

}

