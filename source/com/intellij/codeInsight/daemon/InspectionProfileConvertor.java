package com.intellij.codeInsight.daemon;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ex.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

/**
 * User: anna
 * Date: Dec 20, 2004
 */
public class InspectionProfileConvertor {
  private HashMap<String, HighlightDisplayLevel> myDisplayLevelMap = new HashMap<String, HighlightDisplayLevel>();
  public static final String OLD_HIGHTLIGHTING_SETTINGS_PROFILE = "EditorHightlightingSettings";
  public static final String OLD_DEFAUL_PROFILE = "OldDefaultProfile";

  private String myAdditionalJavadocTags;
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettingsConvertor");

  private static InspectionProfileConvertor ourInstance = null;

  private InspectionProfileConvertor() {
    renameOldDefaultsProfile();
  }

  public static InspectionProfileConvertor getInstance() {
    if (ourInstance == null) {
      ourInstance = new InspectionProfileConvertor();
    }
    return ourInstance;
  }

  private boolean retrieveOldSettings(Element element) {
    boolean hasOldSettings = false;
    for (Iterator<Element> iterator = element.getChildren("option").iterator(); iterator.hasNext();) {
      final Element option = iterator.next();
      final String name = option.getAttributeValue("name");
      if (name != null) {
        if (name.equals("DISPLAY_LEVEL_MAP")) {
          final Element levelMap = option.getChild("value");
          for (Iterator i = levelMap.getChildren().iterator(); i.hasNext();) {
            Element e = (Element)i.next();
            String key = e.getName();
            String levelName = e.getAttributeValue("level");
            HighlightDisplayLevel level = HighlightDisplayLevel.find(levelName);
            if (level == null) continue;
            myDisplayLevelMap.put(key, level);
          }
          hasOldSettings = true;
        }
        else {
          if (name.equals("ADDITIONAL_JAVADOC_TAGS")) {
            myAdditionalJavadocTags = option.getAttributeValue("value");
            hasOldSettings = true;
          }
        }
      }
    }
    return hasOldSettings;
  }

  public void storeEditorHighlightingProfile(Element element) {
    if (retrieveOldSettings(element)) {
      final InspectionProfileManager inspectionProfileManager = InspectionProfileManager.getInstance();
      final InspectionProfileImpl editorProfile = new InspectionProfileImpl(OLD_HIGHTLIGHTING_SETTINGS_PROFILE,
                                                                            inspectionProfileManager);

      final InspectionProfile.ModifiableModel editorProfileModel = editorProfile.getModifiableModel();
      editorProfileModel.setAdditionalJavadocTags(myAdditionalJavadocTags);
      fillErrorLevels(editorProfileModel);
      editorProfileModel.commit();
    }
  }

  public static void convertToNewFormat(File profileFile, InspectionProfile profile) throws IOException, JDOMException {
    final InspectionTool[] tools = profile.getInspectionTools(ProjectManager.getInstance().getDefaultProject());
    final Document document = JDOMUtil.loadDocument(profileFile);
    for (Iterator i = document.getRootElement().getChildren("inspection_tool").iterator(); i.hasNext();) {
      Element toolElement = (Element)i.next();
      String toolClassName = toolElement.getAttributeValue("class");
      final String shortName = convertToShortName(toolClassName, tools);
      if (shortName == null) {
        continue;
      }
      toolElement.setAttribute("class", shortName);
    }
    JDOMUtil.writeDocument(document, profileFile, System.getProperty("line.separator"));
  }

  private static void renameOldDefaultsProfile() {
    final File profileDirectory = InspectionProfileManager.getProfileDirectory();
    final File[] files = profileDirectory.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        if (pathname.getPath().endsWith(File.separator + "Default.xml")) {
          return true;
        }
        return false;
      }
    });
    if (files == null || files.length != 1) {
      return;
    }
    final File dest = new File(profileDirectory, OLD_DEFAUL_PROFILE + ".xml");
    try {
      Document doc = JDOMUtil.loadDocument(files[0]);
      Element root = doc.getRootElement();
      root.setAttribute("profile_name", OLD_DEFAUL_PROFILE);
      JDOMUtil.writeDocument(doc, dest, System.getProperty("line.separator"));
      files[0].delete();
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (JDOMException e) {
      LOG.error(e);
    }
  }

  private void fillErrorLevels(final InspectionProfile.ModifiableModel profile) {
    LocalInspectionToolWrapper[] tools = profile.getLocalInspectionToolWrappers();
    LOG.assertTrue(tools != null, "Profile was not correctly init");
    //fill error levels
    for (Iterator<String> iterator = myDisplayLevelMap.keySet().iterator(); iterator.hasNext();) {
      //key <-> short name
      final String shortName = iterator.next();
      HighlightDisplayLevel level = myDisplayLevelMap.get(shortName);

      HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
      if (key == null) {
        key = HighlightDisplayKey.register(shortName);
      }

      //set up tools for default profile
      if (level != HighlightDisplayLevel.DO_NOT_SHOW) {
        profile.enableTool(shortName);
      }

      if (level == null || level == HighlightDisplayLevel.DO_NOT_SHOW) {
        level = HighlightDisplayLevel.WARNING;
      }
      profile.setErrorLevel(key, level);
    }
  }


  private static String convertToShortName(String displayName, InspectionTool[] tools) {
    for (int i = 0; i < tools.length; i++) {
      if (displayName.equals(tools[i].getDisplayName())) {
        return tools[i].getShortName();
      }
    }
    return null;
  }

}
