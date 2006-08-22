/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Aug 22, 2006
 * Time: 4:42:08 PM
 */
package com.intellij.diagnostic;

import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.util.ResourceUtil;
import org.jetbrains.annotations.NonNls;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Nov 6, 2003
 * Time: 4:05:51 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class DumpInspectionDescriptionsAction extends AnAction {
  public DumpInspectionDescriptionsAction() {
    super ("Dump inspection descriptions");
  }

  public void actionPerformed(AnActionEvent e) {
    final InspectionProfile profile = (InspectionProfile)InspectionProfileManager.getInstance().getRootProfile();
    final InspectionProfileEntry[] tools = profile.getInspectionTools();
    System.out.println("String[][] inspections = ");
    for (InspectionProfileEntry tool : tools) {
      String group = tool.getGroupDisplayName();
      if (group.length() == 0) group = "General";
      System.out.print("{\"" + group +"\", ");
      System.out.print("\"" + tool.getDisplayName() +"\", ");
      System.out.println("\"inspections/" + tool.getShortName() +".html\"}, ");

      try {
        @NonNls final String description = ResourceUtil.loadText(getDescriptionUrl(tool));
        FileWriter writer = new FileWriter("/Users/max/inspections/" + tool.getShortName() + ".html");
        writer.write(description);
        writer.close();
      }
      catch (IOException e1) {
        e1.printStackTrace();
      }
    }
    System.out.println("};");

//    Logger.getInstance("test").error("Test");
  }

  private static URL getDescriptionUrl(InspectionProfileEntry tool) {
    Class aClass = tool instanceof LocalInspectionToolWrapper ? ((LocalInspectionToolWrapper)tool).getTool().getClass() : tool.getClass();
    return ResourceUtil.getResource(aClass, "/inspectionDescriptions", tool.getShortName() + ".html");
  }

}