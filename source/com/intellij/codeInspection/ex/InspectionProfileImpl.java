package com.intellij.codeInspection.ex;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.InspectionProfileConvertor;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * @author max
 */
public class InspectionProfileImpl implements InspectionProfile.ModifiableModel, InspectionProfile {
  public static final InspectionProfileImpl EMPTY_PROFILE = new InspectionProfileImpl("empty");

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionProfileImpl");
  private static String VALID_VERSION = "1.0";
  private String myName;
  private File myFile;

  //fiff from base
  // private HashMap<String, Boolean> myEnabledTools;
  private HashMap<String, InspectionTool> myTools = new HashMap<String, InspectionTool>();
  private HashMap<String, LocalInspectionToolWrapper> myLocalTools = new HashMap<String, LocalInspectionToolWrapper>();
  private InspectionProfileManager myManager;

  //diff map with base profile
  private LinkedHashMap<HighlightDisplayKey, ToolState> myDisplayLevelMap = new LinkedHashMap<HighlightDisplayKey, ToolState>();

  private InspectionProfileImpl mySource;
  private InspectionProfileImpl myBaseProfile = null;
  //private String myBaseProfileName;

  public void setModified(final boolean modified) {
    myModified = modified;
  }

  private boolean myModified = false;
  private boolean myInitialized = false;

  private VisibleTreeState myVisibleTreeState = new VisibleTreeState();

  private String myAdditionalJavadocTags = "";

  public InspectionProfileImpl(File file, InspectionProfileManager manager) throws IOException, JDOMException {
    this(getProfileName(file), getBaseProfileName(file), file, manager);
    mySource = null;
  }

  public InspectionProfileImpl(String name, String baseProfileName, File file, InspectionProfileManager manager) {
    myName = name;
    myFile = file;
    myManager = manager;
    if (baseProfileName != null) {
      myBaseProfile = manager.getProfile(baseProfileName);
      if (myBaseProfile == null) {//was not init yet
        myBaseProfile = new InspectionProfileImpl(baseProfileName, manager);
      }
    }
    mySource = null;
  }

  public InspectionProfileImpl(String name, InspectionProfileManager manager) {
    myName = name;
    myFile = new File(InspectionProfileManager.getProfileDirectory(), myName + ".xml");
    myManager = manager;
    mySource = null;
  }


  InspectionProfileImpl(InspectionProfileImpl inspectionProfile) {
    myName = inspectionProfile.getName();
    myFile = inspectionProfile.getFile();
    myManager = inspectionProfile.getManager();
    myDisplayLevelMap = new LinkedHashMap<HighlightDisplayKey, ToolState>(inspectionProfile.myDisplayLevelMap);
    myLocalTools = new HashMap<String, LocalInspectionToolWrapper>(inspectionProfile.myLocalTools);
    myTools = new HashMap<String, InspectionTool>(inspectionProfile.myTools);
    myVisibleTreeState = new VisibleTreeState(inspectionProfile.myVisibleTreeState);
    myAdditionalJavadocTags = inspectionProfile.myAdditionalJavadocTags;
    myBaseProfile = inspectionProfile.myBaseProfile;
    mySource = inspectionProfile;
  }

  //for tests only
  public InspectionProfileImpl(final String inspectionProfile) {
    myName = inspectionProfile;
    myInitialized = true;
    setDefaultErrorLevels();
  }

  public InspectionProfile getParentProfile() {
    return mySource;
  }

  public String getBaseProfileName() {
    if (myBaseProfile == null) return null;
    return myBaseProfile.getName();
  }

  public void setBaseProfile(InspectionProfileImpl profile) {
    myBaseProfile = profile;
  }

  public void removeInheritance(boolean inheritFromBaseBase) {
    if (myBaseProfile != null) {
      LinkedHashMap<HighlightDisplayKey, ToolState> map = new LinkedHashMap<HighlightDisplayKey, ToolState>();
      if (inheritFromBaseBase) {
        map.putAll(myBaseProfile.myDisplayLevelMap);
        myBaseProfile = myBaseProfile.myBaseProfile;
      }
      else {
        map.putAll(myBaseProfile.getFullDisplayMap());
        myBaseProfile = null;
      }
      map.putAll(myDisplayLevelMap);
      myDisplayLevelMap = map;
    }
  }

  private HashMap<HighlightDisplayKey, ToolState> getFullDisplayMap() {
    final HashMap<HighlightDisplayKey, ToolState> map = new HashMap<HighlightDisplayKey, ToolState>();
    if (myBaseProfile != null) {
      map.putAll(myBaseProfile.getFullDisplayMap());
    }
    map.putAll(myDisplayLevelMap);
    return map;
  }

  public boolean isChanged() {
    return myModified;
  }

  public VisibleTreeState getExpandedNodes() {
    return myVisibleTreeState;
  }

  private boolean toolSettingsAreEqual(String toolDisplayName,
                                       InspectionProfileImpl profile1,
                                       InspectionProfileImpl profile2) {
    final InspectionTool tool1 = profile1.getInspectionTool(toolDisplayName);//findInspectionToolByName(profile1, toolDisplayName);
    final InspectionTool tool2 = profile2.getInspectionTool(toolDisplayName);//findInspectionToolByName(profile2, toolDisplayName);
    if (tool1 == null && tool2 == null) {
      return true;
    }
    if (tool1 != null && tool2 != null) {
      try {
        Element oldToolSettings = new Element("root");
        tool1.writeExternal(oldToolSettings);
        Element newToolSettings = new Element("root");
        tool2.writeExternal(newToolSettings);
        return JDOMUtil.areElementsEqual(oldToolSettings, newToolSettings);
      }
      catch (WriteExternalException e) {
        LOG.error(e);
      }
    }
    return false;
  }

  public boolean isProperSetting(HighlightDisplayKey key) {
    if (myBaseProfile == null) {
      return false;
    }
    final boolean toolsSettings = toolSettingsAreEqual(key.toString(), this, myBaseProfile);
    if (myDisplayLevelMap.keySet().contains(key)) {
      if (toolsSettings && myDisplayLevelMap.get(key).equals(myBaseProfile.getToolState(key))) {
        myDisplayLevelMap.remove(key);
        return false;
      }
      return true;
    }
    if (key == HighlightDisplayKey.UNKNOWN_JAVADOC_TAG &&
        !myBaseProfile.getAdditionalJavadocTags().equals(getAdditionalJavadocTags())) {
      return true;
    }
    if (!toolsSettings) {
      myDisplayLevelMap.put(key, myBaseProfile.getToolState(key));
      return true;
    }

    return false;
  }


  public void setAdditionalJavadocTags(String tags) {
    if (myBaseProfile != null && myBaseProfile.getAdditionalJavadocTags().length() > 0) {
      myAdditionalJavadocTags = tags.length() > myBaseProfile.getAdditionalJavadocTags().length()
                                ? tags.substring(myBaseProfile.getAdditionalJavadocTags().length() + 1).trim()
                                : "";
    }
    else {
      myAdditionalJavadocTags = tags;
    }
  }

  public void resetToBase() {
    if (myBaseProfile != null) {
      myDisplayLevelMap = new LinkedHashMap<HighlightDisplayKey, ToolState>(myBaseProfile.myDisplayLevelMap);
      myBaseProfile = myBaseProfile.myBaseProfile;
    }
    else {
      boolean toolsWereNotInstantiated = false;
      if (myTools.isEmpty() || myLocalTools.isEmpty()) {
        getInspectionTools(null);
        toolsWereNotInstantiated = true;
      }
      myDisplayLevelMap.clear();
      setDefaultErrorLevels();
      final ArrayList<String> toolNames = new ArrayList<String>(myTools.keySet());
      toolNames.addAll(myLocalTools.keySet());
      for (Iterator<String> iterator = toolNames.iterator(); iterator.hasNext();) {
        final InspectionTool tool = getInspectionTool(iterator.next());
        final HighlightDisplayLevel defaultLevel = tool.getDefaultLevel();
        HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
        if (key == null) {
          key = HighlightDisplayKey.register(tool.getShortName());
        }
        myDisplayLevelMap.put(key,
                              new ToolState(defaultLevel, tool.isEnabledByDefault()));
      }
      if (toolsWereNotInstantiated) {
        //to instantiate tools correctly
        myTools.clear();
      }
    }
    myInitialized = true;
  }

  private void setDefaultErrorLevels() {
    myDisplayLevelMap.put(HighlightDisplayKey.DEPRECATED_SYMBOL, new ToolState(HighlightDisplayLevel.WARNING));
    myDisplayLevelMap.put(HighlightDisplayKey.UNUSED_IMPORT, new ToolState(HighlightDisplayLevel.WARNING));
    myDisplayLevelMap.put(HighlightDisplayKey.UNUSED_SYMBOL, new ToolState(HighlightDisplayLevel.WARNING));
    myDisplayLevelMap.put(HighlightDisplayKey.UNUSED_THROWS_DECL, new ToolState(HighlightDisplayLevel.WARNING));
    myDisplayLevelMap.put(HighlightDisplayKey.SILLY_ASSIGNMENT, new ToolState(HighlightDisplayLevel.WARNING));
    myDisplayLevelMap.put(HighlightDisplayKey.ACCESS_STATIC_VIA_INSTANCE, new ToolState(HighlightDisplayLevel.WARNING));
    myDisplayLevelMap.put(HighlightDisplayKey.WRONG_PACKAGE_STATEMENT, new ToolState(HighlightDisplayLevel.WARNING));
    myDisplayLevelMap.put(HighlightDisplayKey.JAVADOC_ERROR, new ToolState(HighlightDisplayLevel.ERROR));
    myDisplayLevelMap.put(HighlightDisplayKey.UNKNOWN_JAVADOC_TAG, new ToolState(HighlightDisplayLevel.ERROR));
    myDisplayLevelMap.put(HighlightDisplayKey.EJB_ERROR, new ToolState(HighlightDisplayLevel.ERROR));
    myDisplayLevelMap.put(HighlightDisplayKey.EJB_WARNING, new ToolState(HighlightDisplayLevel.WARNING));
    myDisplayLevelMap.put(HighlightDisplayKey.ILLEGAL_DEPENDENCY, new ToolState(HighlightDisplayLevel.WARNING));
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public HighlightDisplayLevel getErrorLevel(HighlightDisplayKey inspectionToolKey) {
    return getToolState(inspectionToolKey).getLevel();
  }

  private ToolState getToolState(HighlightDisplayKey key) {
    ToolState state = myDisplayLevelMap.get(key);
    if (state == null) {
      if (myBaseProfile != null) {
        state = myBaseProfile.getToolState(key);
      }
    }
    //default level for converted profiles
    if (state == null) {
      state = new ToolState(HighlightDisplayLevel.WARNING, false);
    }
    return state;
  }

  private void readExternal(Element element, boolean readLocalTools) throws InvalidDataException {
    myDisplayLevelMap.clear();
    final String version = element.getAttributeValue("version");
    if (version == null || !version.equals(VALID_VERSION)) {
      try {
        InspectionProfileConvertor.convertToNewFormat(myFile, this);
        element = JDOMUtil.loadDocument(myFile).getRootElement();
      }
      catch (IOException e) {
        LOG.error(e);
      }
      catch (JDOMException e) {
        LOG.error(e);
      }
    }
    for (Iterator i = element.getChildren("inspection_tool").iterator(); i.hasNext();) {
      Element toolElement = (Element)i.next();

      String toolClassName = toolElement.getAttributeValue("class");

      HighlightDisplayKey key = HighlightDisplayKey.find(toolClassName);
      if (key == null) {
        key = HighlightDisplayKey.register(toolClassName);
      }

      final String levelName = toolElement.getAttributeValue("level");
      HighlightDisplayLevel level = HighlightDisplayLevel.find(levelName);
      if (level == null || level == HighlightDisplayLevel.DO_NOT_SHOW) {//from old profiles
        level = HighlightDisplayLevel.WARNING;
      }

      final String enabled = toolElement.getAttributeValue("enabled");
      myDisplayLevelMap.put(key, new ToolState(level, enabled != null && "true".equals(enabled)));

      InspectionTool tool = getInspectionTool(toolClassName);
      if (tool != null) {
        if (!(tool instanceof LocalInspectionToolWrapper) || readLocalTools) {
          tool.readExternal(toolElement);
        }
      }
    }
    myVisibleTreeState.readExternal(element);
    final Element additionalJavadocs = element.getChild("ADDITIONAL_JAVADOC_TAGS");
    if (additionalJavadocs != null) {
      myAdditionalJavadocTags = additionalJavadocs.getAttributeValue("value");
    }
    final String baseProfileName = element.getAttributeValue("base_profile");
    if (baseProfileName != null && myBaseProfile == null) {
      myBaseProfile = InspectionProfileManager.getInstance().getProfile(baseProfileName);
      if (baseProfileName.equals("Default")) {
        myBaseProfile.resetToBase();
      }
      if (!myBaseProfile.wasInitialized()) {
        myBaseProfile.load(readLocalTools);
      }
    }
  }


  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute("version", VALID_VERSION);
    for (Iterator<HighlightDisplayKey> iterator = myDisplayLevelMap.keySet().iterator(); iterator.hasNext();) {
      final HighlightDisplayKey key = iterator.next();
      Element inspectionElement = new Element("inspection_tool");
      final String toolName = key.toString();
      inspectionElement.setAttribute("class", toolName);
      inspectionElement.setAttribute("level", getErrorLevel(key).toString());
      inspectionElement.setAttribute("enabled", isToolEnabled(key) ? "true" : "false");

      final InspectionTool tool = getInspectionTool(toolName);
      if (tool != null) {
        tool.writeExternal(inspectionElement);
      }
      element.addContent(inspectionElement);
    }
    myVisibleTreeState.writeExternal(element);
    if (myAdditionalJavadocTags != null && myAdditionalJavadocTags.length() != 0) {
      final Element additionalTags = new Element("ADDITIONAL_JAVADOC_TAGS");
      additionalTags.setAttribute("value", myAdditionalJavadocTags);
      element.addContent(additionalTags);
    }
    if (myBaseProfile != null) {
      element.setAttribute("base_profile", myBaseProfile.getName());
    }
  }

  public InspectionTool getInspectionTool(String displayName) {
    if (myTools.get(displayName) != null) {
      return myTools.get(displayName);
    }
    if (myLocalTools.get(displayName) != null) {
      return myLocalTools.get(displayName);
    }
    return null;
  }

  public InspectionProfileManager getManager() {
    return myManager;
  }

  private static String getProfileName(File file) throws JDOMException, IOException {
    if (file.exists()) {
      Document doc = JDOMUtil.loadDocument(file);
      Element root = doc.getRootElement();
      String profileName = root.getAttributeValue("profile_name");
      if (profileName != null) return profileName;
    }
    String fileName = file.getName();
    int extensionIndex = fileName.lastIndexOf(".xml");
    return fileName.substring(0, extensionIndex);
  }

  private static String getBaseProfileName(File file) throws JDOMException, IOException {
    if (file.exists()) {
      Document doc = JDOMUtil.loadDocument(file);
      Element root = doc.getRootElement();
      String profileName = root.getAttributeValue("base_profile");
      if (profileName != null) return profileName;
    }
    return null;
  }

  void save(File file, String name) {
    try {
      Element root = new Element("inspections");
      root.setAttribute("profile_name", name);
      writeExternal(root);
      if (file != null) {
        JDOMUtil.writeDocument(new Document(root), file, CodeStyleSettingsManager.getSettings(null).getLineSeparator());
      }
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public void load(boolean readLocalTools) {
    try {
      if (myName.equals("Default")) {
        resetToBase();
        return;
      }
      if (myFile == null || !myFile.exists()) {
        if (myBaseProfile != null) {
          loadAdditionalSettingsFromBaseProfile();
        }
        return;
      }

      Document document = JDOMUtil.loadDocument(myFile);
      readExternal(document.getRootElement(), readLocalTools);
      myInitialized = true;
    }
    catch (JDOMException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public void loadAdditionalSettingsFromBaseProfile() {//load additional settings from base profile
    if (myBaseProfile == null) return;
    try {
      final ArrayList<String> toolNames = new ArrayList<String>(myTools.keySet());
      toolNames.addAll(myLocalTools.keySet());
      for (Iterator<String> iterator = toolNames.iterator(); iterator.hasNext();) {
        final String key = iterator.next();
        if (myDisplayLevelMap.containsKey(HighlightDisplayKey.find(key))) {
          continue;
        }
        Element root = new Element("root");
        final InspectionTool baseInspectionTool = myBaseProfile.getInspectionTool(key);
        if (baseInspectionTool != null) {
          baseInspectionTool.writeExternal(root);
          InspectionTool tool = getInspectionTool(key);
          tool.readExternal(root);
        }
      }
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public File getFile() {
    return myFile;
  }

  public InspectionTool[] getInspectionTools(Project project) {
    if (myBaseProfile != null && myBaseProfile.myTools.isEmpty()) {
      myBaseProfile.getInspectionTools(project);
    }
    if (myTools.isEmpty()) {
      boolean localToolsWereInitialized = true;
      final InspectionTool[] tools = InspectionToolRegistrar.getInstance().createTools(project);
      for (int i = 0; i < tools.length; i++) {
        if (!(tools[i] instanceof LocalInspectionToolWrapper)) {
          myTools.put(tools[i].getShortName(), tools[i]);
        }
        else {
          if (!myLocalTools.containsKey(tools[i].getShortName())) {//do not touch exist local tools
            localToolsWereInitialized = false;
            myLocalTools.put(tools[i].getShortName(), (LocalInspectionToolWrapper)tools[i]);
          }
        }
      }
      load(!localToolsWereInitialized);
      loadAdditionalSettingsFromBaseProfile();
    }
    ArrayList<InspectionTool> result = new ArrayList<InspectionTool>();
    result.addAll(myLocalTools.values());
    result.addAll(myTools.values());
    return result.toArray(new InspectionTool[result.size()]);
  }

  public LocalInspectionToolWrapper[] getLocalInspectionToolWrappers() {
    if (myBaseProfile != null && myBaseProfile.myLocalTools.isEmpty()) {
      myBaseProfile.getLocalInspectionToolWrappers();
    }
    if (myLocalTools.isEmpty()) {
      final LocalInspectionTool[] localTools = InspectionToolRegistrar.getInstance().createLocalTools();
      for (int i = 0; i < localTools.length; i++) {
        myLocalTools.put(localTools[i].getShortName(), new LocalInspectionToolWrapper(localTools[i]));
      }
      load(true);
      loadAdditionalSettingsFromBaseProfile();
    }
    return myLocalTools.values().toArray(new LocalInspectionToolWrapper[myLocalTools.values().size()]);
  }

  public LocalInspectionTool[] getHighlightingLocalInspectionTools() {
    ArrayList<LocalInspectionTool> enabled = new ArrayList<LocalInspectionTool>();
    final LocalInspectionToolWrapper[] tools = myLocalTools.isEmpty()
                                               ? getLocalInspectionToolWrappers()
                                               : myLocalTools.values().toArray(
                                                 new LocalInspectionToolWrapper[myLocalTools.values().size()]);
    for (int i = 0; i < tools.length; i++) {
      LocalInspectionToolWrapper tool = tools[i];
      final ToolState state = getToolState(HighlightDisplayKey.find(tool.getShortName()));
      if (state.isEnabled()) {
        enabled.add(tool.getTool());
      }
    }
    return enabled.toArray(new LocalInspectionTool[enabled.size()]);
  }

  public ModifiableModel getModifiableModel() {
    return new InspectionProfileImpl(this);
  }

  public String getAdditionalJavadocTags() {
    if (myBaseProfile != null) {
      return myBaseProfile.getAdditionalJavadocTags().length() > 0 ? myBaseProfile.getAdditionalJavadocTags() +
                                                                     (myAdditionalJavadocTags.length() > 0
                                                                      ? "," + myAdditionalJavadocTags
                                                                      : "") :
             myAdditionalJavadocTags;
    }
    return myAdditionalJavadocTags;
  }

  public void copyFrom(InspectionProfileImpl profile) {
    myDisplayLevelMap = new LinkedHashMap<HighlightDisplayKey, ToolState>(profile.myDisplayLevelMap);
    myBaseProfile = profile.myBaseProfile;
    myAdditionalJavadocTags = profile.myAdditionalJavadocTags;
  }

  public void inheritFrom(InspectionProfileImpl profile) {
    myBaseProfile = profile;
  }

  public void cleanup() {
    if (myTools.isEmpty()) return;
    if (!myTools.isEmpty()) {
      for (Iterator<String> iterator = myTools.keySet().iterator(); iterator.hasNext();) {
        myTools.get(iterator.next()).cleanup();
      }
    }
    myTools.clear();
  }

  public boolean wasInitialized() {
    return myInitialized;
  }

  public void enableTool(String inspectionTool) {
    final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionTool);
    setState(key,
             new ToolState(getErrorLevel(key), true));
  }

  public void disableTool(String inspectionTool) {
    final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionTool);
    setState(key,
             new ToolState(getErrorLevel(key), false));
  }


  public void setErrorLevel(HighlightDisplayKey key, HighlightDisplayLevel level) {
    setState(key, new ToolState(level, isToolEnabled(key)));
  }

  private void setState(HighlightDisplayKey key, ToolState state) {
    if (myBaseProfile != null &&
        state.equals(myBaseProfile.getToolState(key))) {
      myDisplayLevelMap.remove(key);
    }
    else {
      myDisplayLevelMap.put(key, state);
    }
  }

  public boolean isToolEnabled(HighlightDisplayKey key) {
    final ToolState toolState = getToolState(key);
    if (toolState != null) {
      return toolState.isEnabled();
    }
    return false;
  }

  //invoke when isChanged() == true
  public void commit() {
    LOG.assertTrue(mySource != null);
    mySource.commit(this);
    mySource = null;
    myManager.initProfile(this);
  }

  private void commit(InspectionProfileImpl inspectionProfile) {
    myName = inspectionProfile.myName;
    myDisplayLevelMap = inspectionProfile.myDisplayLevelMap;
    myVisibleTreeState = inspectionProfile.myVisibleTreeState;
    myBaseProfile = inspectionProfile.myBaseProfile;
    myTools = inspectionProfile.myTools;
    myLocalTools = inspectionProfile.myLocalTools;
    myAdditionalJavadocTags = inspectionProfile.myAdditionalJavadocTags;
    save(new File(InspectionProfileManager.getProfileDirectory(), myName + ".xml"), myName);
  }

  private static class ToolState {
    private HighlightDisplayLevel myLevel;
    private boolean myEnabled;

    public ToolState(final HighlightDisplayLevel level, final boolean enabled) {
      myLevel = level;
      myEnabled = enabled;
    }

    public ToolState(final HighlightDisplayLevel level) {
      myLevel = level;
      myEnabled = true;
    }

    public HighlightDisplayLevel getLevel() {
      return myLevel;
    }

    public void setLevel(final HighlightDisplayLevel level) {
      myLevel = level;
    }

    public boolean isEnabled() {
      return myEnabled;
    }

    public void setEnabled(final boolean enabled) {
      myEnabled = enabled;
    }

    public boolean equals(Object object) {
      if (!(object instanceof ToolState)) return false;
      final ToolState state = (ToolState)object;
      return myLevel == state.getLevel() &&
             myEnabled == state.isEnabled();
    }

    public int hashCode() {
      return myLevel.hashCode();
    }
  }

}
