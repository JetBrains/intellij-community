package com.intellij.application.options.colors;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.ui.DebuggerPanelsManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diff.impl.settings.DiffColorsForm;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.colors.ex.DefaultColorSchemesManager;
import com.intellij.openapi.editor.colors.impl.DefaultColorsScheme;
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import com.intellij.openapi.options.colors.ColorSettingsPages;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.peer.PeerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

public class ColorAndFontOptions extends BaseConfigurable implements ApplicationComponent {
  private ColorAndFontPanel myPanel;
  private com.intellij.util.containers.HashMap mySchemes;
  private MyColorScheme mySelectedScheme;
  public static final String DIFF_GROUP = "Diff";
  private static final String FILE_STATUS_GROUP = "File Status";

  public void disposeComponent() {
  }

  public void initComponent() { }

  public boolean isModified() {
    if (!mySelectedScheme.getName().equals(EditorColorsManager.getInstance().getGlobalScheme().getName())) return true;

    for (Iterator iterator = mySchemes.values().iterator(); iterator.hasNext();) {
      MyColorScheme scheme = (MyColorScheme)iterator.next();
      if (scheme.isModified()) return true;
    }

    return false;
  }

  public EditorColorsScheme selectScheme(String name) {
    mySelectedScheme = getScheme(name);
    return mySelectedScheme;
  }

  private MyColorScheme getScheme(String name) {
    return (MyColorScheme)mySchemes.get(name);
  }

  public EditorColorsScheme getSelectedScheme() {
    return mySelectedScheme;
  }

  public EditorSchemeAttributeDescriptor[] getCurrentDescriptions() {
    return mySelectedScheme.getDescriptors();
  }

  public boolean isDefault(EditorColorsScheme scheme) {
    return ((MyColorScheme)scheme).isDefault();
  }

  public String[] getSchemeNames() {
    ArrayList schemes = new ArrayList(mySchemes.values());
    Collections.sort(schemes, new Comparator() {
      public int compare(Object o1, Object o2) {
        EditorColorsScheme s1 = (EditorColorsScheme)o1;
        EditorColorsScheme s2 = (EditorColorsScheme)o2;

        if (isDefault(s1) && !isDefault(s2)) return -1;
        if (!isDefault(s1) && isDefault(s2)) return 1;

        return s1.getName().compareToIgnoreCase(s2.getName());
      }
    });

    ArrayList<String> names = new ArrayList<String>(schemes.size());
    for (int i = 0; i < schemes.size(); i++) {
      EditorColorsScheme scheme = (EditorColorsScheme)schemes.get(i);
      names.add(scheme.getName());
    }

    return names.toArray(new String[names.size()]);
  }

  public void saveSchemeAs(String name) {
    MyColorScheme scheme = mySelectedScheme;
    if (scheme == null) return;

    EditorColorsScheme clone = (EditorColorsScheme)scheme.getOriginalScheme().clone();
    clone.setName(name);
    MyColorScheme newScheme = new MyColorScheme(clone);
    initScheme(newScheme);

    mySchemes.put(name, newScheme);
    myPanel.resetSchemesCombo();
    myPanel.changeToScheme(newScheme);
    selectScheme(newScheme.getName());
  }

  public void removeScheme(String name) {
    if (mySelectedScheme.getName().equals(name)) {
      myPanel.changeToScheme(selectScheme("Default"));
    }

    mySchemes.remove(name);
    myPanel.resetSchemesCombo();
  }

  public void apply() throws ConfigurationException {
    try {
      EditorColorsManager myColorsManager = EditorColorsManager.getInstance();

      myColorsManager.removeAllSchemes();
      for (Iterator iterator = mySchemes.values().iterator(); iterator.hasNext();) {
        MyColorScheme scheme = (MyColorScheme)iterator.next();
        if (!scheme.isDefault()) {
          scheme.apply();
          myColorsManager.addColorsScheme(scheme.getOriginalScheme());
        }
      }

      EditorColorsScheme originalScheme = mySelectedScheme.getOriginalScheme();
      myColorsManager.setGlobalScheme(originalScheme);

      myColorsManager.saveAllSchemes();

      EditorFactory.getInstance().refreshAllEditors();

      Project[] projects = ProjectManager.getInstance().getOpenProjects();
      for (int i = 0; i < projects.length; i++) {
        Project project1 = projects[i];
        // Update breakpoints for debugger.
        updateBreakpoints(project1);
      }

      initAll();
      myPanel.resetSchemesCombo();

      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      for (int i = 0; i < openProjects.length; i++) {
        FileStatusManager.getInstance(openProjects[i]).fileStatusesChanged();
      }

      myPanel.apply();
    }
    catch (IOException e) {
      throw new ConfigurationException(e.getMessage());
    }
  }

  private static void updateBreakpoints(Project project) {
    if (project == null) return;
    DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
    if (debuggerManager == null) return;
    debuggerManager.getBreakpointManager().updateBreakpointsUI();
    DebuggerPanelsManager.getInstance(project).updateContextPointDescription();
  }

  public JComponent createComponent() {
    initAll();
    myPanel = new ColorAndFontPanel(this);
    myPanel.setPreferredSize(new Dimension(650, 600));
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferedFocusComponent();
  }

  private void initAll() {
    EditorColorsManager colorsManager = EditorColorsManager.getInstance();
    EditorColorsScheme[] allSchemes = colorsManager.getAllSchemes();

    mySchemes = new com.intellij.util.containers.HashMap();
    for (int i = 0; i < allSchemes.length; i++) {
      MyColorScheme schemeDelegate = new MyColorScheme(allSchemes[i]);
      initScheme(schemeDelegate);
      mySchemes.put(schemeDelegate.getName(), schemeDelegate);
    }

    mySelectedScheme = (MyColorScheme)mySchemes.get(EditorColorsManager.getInstance().getGlobalScheme().getName());
  }

  private static void initScheme(MyColorScheme scheme) {
    ArrayList<EditorSchemeAttributeDescriptor> descriptions = new ArrayList<EditorSchemeAttributeDescriptor>();
    initPluggedDescriptions(descriptions, scheme);
    initDiffDescriptors(descriptions, scheme);
    initFileStatusDescriptors(descriptions, scheme);

    scheme.setDescriptors(descriptions.toArray(new EditorSchemeAttributeDescriptor[descriptions.size()]));
  }

  private static void initPluggedDescriptions(ArrayList<EditorSchemeAttributeDescriptor> descriptions,
                                              MyColorScheme scheme) {
    ColorSettingsPage[] pages = ColorSettingsPages.getInstance().getRegisteredPages();
    for (int i = 0; i < pages.length; i++) {
      ColorSettingsPage page = pages[i];
      initDescriptions(page, descriptions, scheme);
    }
  }

  private static void initDescriptions(ColorSettingsPage page,
                                       ArrayList<EditorSchemeAttributeDescriptor> descriptions,
                                       MyColorScheme scheme) {
    String group = page.getDisplayName();
    AttributesDescriptor[] attributeDescriptors = page.getAttributeDescriptors();
    for (int i = 0; i < attributeDescriptors.length; i++) {
      AttributesDescriptor descriptor = attributeDescriptors[i];
      addSchemedDescription(descriptions, descriptor.getDisplayName(), group, descriptor.getKey(), scheme);
    }

    ColorDescriptor[] colorDescriptors = page.getColorDescriptors();
    for (int i = 0; i < colorDescriptors.length; i++) {
      ColorDescriptor descriptor = colorDescriptors[i];
      ColorKey back = descriptor.getKind() == ColorDescriptor.Kind.BACKGROUND ? descriptor.getKey() : null;
      ColorKey fore = descriptor.getKind() == ColorDescriptor.Kind.FOREGROUND ? descriptor.getKey() : null;
      addEditorSettingDescription(descriptions, descriptor.getDisplayName(), group, back, fore, scheme);
    }
  }

  private static void initDiffDescriptors(ArrayList<EditorSchemeAttributeDescriptor> descriptions, MyColorScheme scheme) {
    DiffColorsForm.addSchemeDescriptions(descriptions, scheme);
  }

  private static void initFileStatusDescriptors(ArrayList<EditorSchemeAttributeDescriptor> descriptions, MyColorScheme scheme) {

    FileStatus[] statuses = PeerFactory.getInstance().getFileStatusFactory().getAllFileStatuses();

    for (int i = 0; i < statuses.length; i++) {
      FileStatus fileStatus = statuses[i];
      addEditorSettingDescription(descriptions,
                                  fileStatus.getText(),
                                  FILE_STATUS_GROUP,
                                  null,
                                  fileStatus.getColorKey(),
                                  scheme);

    }
  }

  private static ColorAndFontDescription addEditorSettingDescription(ArrayList array,
                                           String name,
                                           String group,
                                           ColorKey backgroundKey,
                                           ColorKey foregroundKey,
                                           EditorColorsScheme scheme) {
    String type = null;
    if (foregroundKey != null) {
      type = foregroundKey.getExternalName();
    }
    else {
      if (backgroundKey != null) {
        type = backgroundKey.getExternalName();
      }
    }
    ColorAndFontDescription descr = new EditorSettingColorDescription(name, group, backgroundKey, foregroundKey, type, scheme);
    array.add(descr);
    return descr;
  }

  private static ColorAndFontDescription addSchemedDescription(ArrayList<EditorSchemeAttributeDescriptor> array,
                                                        String name,
                                                        String group,
                                                        TextAttributesKey key,
                                                        EditorColorsScheme scheme) {
    ColorAndFontDescription descr = new SchemeTextAttributesDescription(name, group, key, scheme);
    array.add(descr);
    return descr;
  }

  public String getDisplayName() {
    return "Colors & Fonts";
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableColorsAndFonts.png");
  }

  public void reset() {
    initAll();
    myPanel.reset();
  }

  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel.dispose();
    }
    myPanel = null;
  }


  private static class SchemeTextAttributesDescription extends TextAttributesDescription {
    private TextAttributes myAttributesToApply;
    private TextAttributesKey key;

    public SchemeTextAttributesDescription(String name, String group, TextAttributesKey key, EditorColorsScheme scheme) {
      super(name, group,
            scheme.getAttributes(key) == null
            ? new TextAttributes()
            : (TextAttributes)scheme.getAttributes(key).clone(),
            key, scheme);
      this.key = key;
      myAttributesToApply = scheme.getAttributes(key);
      initCheckedStatus();
    }

    public void apply(EditorColorsScheme scheme) {
      if (scheme == null) scheme = getScheme();
      if (myAttributesToApply != null) {
        scheme.setAttributes(key, getTextAttributes());
      }
    }

    public boolean isModified() {
      return !Comparing.equal(myAttributesToApply, getTextAttributes());
    }

    public boolean isErrorStripeEnabled() {
      return true;
    }
  }

  private static class GetSetColor {
    private final ColorKey myKey;
    private EditorColorsScheme myScheme;
    private boolean isModified = false;
    private Color myColor;

    public GetSetColor(ColorKey key, EditorColorsScheme scheme) {
      myKey = key;
      myScheme = scheme;
      myColor = myScheme.getColor(myKey);
    }

    public Color getColor() {
      return myColor;
    }

    public void setColor(Color col) {
      if (getColor() == null || !getColor().equals(col)) {
        isModified = true;
        myColor = col;
      }
    }

    public void apply(EditorColorsScheme scheme) {
      if (scheme == null) scheme = myScheme;
      scheme.setColor(myKey, myColor);
    }

    public boolean isModified() {
      return isModified;
    }
  }

  private static class EditorSettingColorDescription extends ColorAndFontDescription {
    private GetSetColor myGetSetForeground;
    private GetSetColor myGetSetBackground;

    public EditorSettingColorDescription(String name,
                                         String group,
                                         ColorKey backgroundKey,
                                         ColorKey foregroundKey,
                                         String type,
                                         EditorColorsScheme scheme) {
      super(name, group, type, scheme);
      if (backgroundKey != null) {
        myGetSetBackground = new GetSetColor(backgroundKey, scheme);
      }
      if (foregroundKey != null) {
        myGetSetForeground = new GetSetColor(foregroundKey, scheme);
      }
      initCheckedStatus();
    }

    public int getFontType() {
      return 0;
    }

    public void setFontType(int type) {
    }

    public Color getExternalEffectColor() {
      return null;
    }

    public void setExternalEffectColor(Color color) {
    }

    public void setExternalEffectType(EffectType type) {
    }

    public EffectType getExternalEffectType() {
      return EffectType.LINE_UNDERSCORE;
    }

    public Color getExternalForeground() {
      if (myGetSetForeground == null) {
        return null;
      }
      return myGetSetForeground.getColor();
    }

    public void setExternalForeground(Color col) {
      if (myGetSetForeground == null) {
        return;
      }
      myGetSetForeground.setColor(col);
    }

    public Color getExternalBackground() {
      if (myGetSetBackground == null) {
        return null;
      }
      return myGetSetBackground.getColor();
    }

    public void setExternalBackground(Color col) {
      if (myGetSetBackground == null) {
        return;
      }
      myGetSetBackground.setColor(col);
    }

    public Color getExternalErrorStripe() {
      return null;
    }

    public void setExternalErrorStripe(Color col) {
    }

    public boolean isFontEnabled() {
      return false;
    }

    public boolean isForegroundEnabled() {
      return myGetSetForeground != null;
    }

    public boolean isBackgroundEnabled() {
      return myGetSetBackground != null;
    }

    public boolean isEffectsColorEnabled() {
      return false;
    }

    public boolean isModified() {
      if (myGetSetBackground != null && myGetSetBackground.isModified()) return true;
      if (myGetSetForeground != null && myGetSetForeground.isModified()) return true;
      return false;
    }

    public void apply(EditorColorsScheme scheme) {
      if (myGetSetBackground != null) {
        myGetSetBackground.apply(scheme);
      }
      if (myGetSetForeground != null) {
        myGetSetForeground.apply(scheme);
      }
    }
  }

  public String getHelpTopic() {
    return "preferences.colorsFonts";
  }

  public String getComponentName() {
    return "ColorAndFontOptions";
  }

  private static class MyColorScheme extends EditorColorsSchemeImpl {
    private int myFontSize;
    private float myLineSpacing;
    private String myFontName;
    private EditorSchemeAttributeDescriptor[] myDescriptors;
    private String myName;

    public MyColorScheme(EditorColorsScheme parenScheme) {
      super(parenScheme, DefaultColorSchemesManager.getInstance());
      myFontSize = parenScheme.getEditorFontSize();
      myLineSpacing = parenScheme.getLineSpacing();
      myFontName = parenScheme.getEditorFontName();
      myName = parenScheme.getName();
      initFonts();
    }

    public String getName() {
      return myName;
    }

    public void setName(String name) {
      myName = name;
    }

    public void setDescriptors(EditorSchemeAttributeDescriptor[] descriptors) {
      myDescriptors = descriptors;
    }

    public EditorSchemeAttributeDescriptor[] getDescriptors() {
      return myDescriptors;
    }

    public boolean isDefault() {
      return myParentScheme instanceof DefaultColorsScheme;
    }

    public boolean isModified() {
      if (myFontSize != myParentScheme.getEditorFontSize()) return true;
      if (myLineSpacing != myParentScheme.getLineSpacing()) return true;
      if (!myFontName.equals(myParentScheme.getEditorFontName())) return true;

      for (int i = 0; i < myDescriptors.length; i++) {
        EditorSchemeAttributeDescriptor descriptor = myDescriptors[i];
        if (descriptor.isModified()) return true;
      }

      return false;
    }

    public void apply() {
      myParentScheme.setEditorFontSize(myFontSize);
      myParentScheme.setEditorFontName(myFontName);
      myParentScheme.setLineSpacing(myLineSpacing);

      for (int i = 0; i < myDescriptors.length; i++) {
        EditorSchemeAttributeDescriptor descriptor = myDescriptors[i];
        descriptor.apply(myParentScheme);
      }
    }

    public String getEditorFontName() {
      return myFontName;
    }

    public int getEditorFontSize() {
      return myFontSize;
    }

    public float getLineSpacing() {
      return myLineSpacing;
    }

    public void setEditorFontSize(int fontSize) {
      myFontSize = fontSize;
      initFonts();
    }

    public void setLineSpacing(float lineSpacing) {
      myLineSpacing = lineSpacing;
    }

    public void setEditorFontName(String fontName) {
      myFontName = fontName;
      initFonts();
    }

    public Object clone() {
      return null;
    }

    public EditorColorsScheme getOriginalScheme() {
      return myParentScheme;
    }
  }
}
