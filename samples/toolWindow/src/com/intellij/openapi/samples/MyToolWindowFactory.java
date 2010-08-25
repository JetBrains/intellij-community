package com.intellij.openapi.samples;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Chursin
 * Date: Aug 25, 2010
 * Time: 2:09:00 PM
 */
public class MyToolWindowFactory implements ToolWindowFactory {

    // Creates the tool window content.
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
     ToolDialog myDialog = new ToolDialog();
     myDialog.toolWin = toolWindow;
     myDialog.currentDateTime();     
     JPanel myContentPanel = myDialog.getPanel();
     ContentFactory contentFactory =  ContentFactory.SERVICE.getInstance();
     Content content = contentFactory.createContent(myContentPanel, "", false);
     toolWindow.getContentManager().addContent(content);
        
    }
    
}
