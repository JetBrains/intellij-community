package com.intellij.application.options.colors;

import com.intellij.openapi.application.ApplicationBundle;
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
import com.intellij.openapi.options.SearchableConfigurable;
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
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.*;

public class ColorAndFontOptions extends BaseConfigurable implements SearchableConfigurable, ApplicationComponent {
  private ColorAndFontPanel myPanel;
  private HashMap<String,MyColorScheme> mySchemes;
  private MyColorScheme mySelectedScheme;
  public static final String DIFF_GROUP = ApplicationBundle.message("title.diff");
  public static final String FILE_STATUS_GROUP = ApplicationBundle.message("title.file.status");
  public static final String SCOPES_GROUP = ApplicationBundle.message("title.scope.based");

  public void disposeComponent() {
  }

  public void initComponent() { }

  public boolean isModified() {
    if (!mySelectedScheme.getName().equals(EditorColorsManager.getInstance().getGlobalScheme().getName())) return true;

    for (MyColorScheme scheme : mySchemes.values()) {
      if (scheme.isModified()) return true;
    }

    return false;
  }

  public EditorColorsScheme selectScheme(String name) {
    mySelectedScheme = getScheme(name);
    return mySelectedScheme;
  }

  private MyColorScheme getScheme(String name) {
    return mySchemes.get(name);
  }

  public EditorColorsScheme getSelectedScheme() {
    return mySelectedScheme;
  }

  public EditorSchemeAttributeDescriptor[] getCurrentDescriptions() {
    return mySelectedScheme.getDescriptors();
  }

  public static boolean isDefault(EditorColorsScheme scheme) {
    return ((MyColorScheme)scheme).isDefault();
  }

  public String[] getSchemeNames() {
    ArrayList<MyColorScheme> schemes = new ArrayList<MyColorScheme>(mySchemes.values());
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
    for (MyColorScheme scheme : schemes) {
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
      //noinspection HardCodedStringLiteral
      myPanel.changeToScheme(selectScheme("Default"));
    }

    mySchemes.remove(name);
    myPanel.resetSchemesCombo();
  }

  public void apply() throws ConfigurationException {
    try {
      EditorColorsManager myColorsManager = EditorColorsManager.getInstance();

      myColorsManager.removeAllSchemes();
      for (MyColorScheme scheme : mySchemes.values()) {
        if (!scheme.isDefault()) {
          scheme.apply();
          myColorsManager.addColorsScheme(scheme.getOriginalScheme());
        }
      }

      EditorColorsScheme originalScheme = mySelectedScheme.getOriginalScheme();
      myColorsManager.setGlobalScheme(originalScheme);

      myColorsManager.saveAllSchemes();

      EditorFactory.getInstance().refreshAllEditors();

      initAll();
      myPanel.resetSchemesCombo();

      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      for (Project openProject : openProjects) {
        FileStatusManager.getInstance(openProject).fileStatusesChanged();
      }

      myPanel.apply();
    }
    catch (IOException e) {
      throw new ConfigurationException(e.getMessage());
    }
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

    mySchemes = new HashMap<String, MyColorScheme>();
    for (EditorColorsScheme allScheme : allSchemes) {
      MyColorScheme schemeDelegate = new MyColorScheme(allScheme);
      initScheme(schemeDelegate);
      mySchemes.put(schemeDelegate.getName(), schemeDelegate);
    }

    mySelectedScheme = mySchemes.get(EditorColorsManager.getInstance().getGlobalScheme().getName());
  }

  private static void initScheme(MyColorScheme scheme) {
    ArrayList<EditorSchemeAttributeDescriptor> descriptions = new ArrayList<EditorSchemeAttributeDescriptor>();
    initPluggedDescriptions(descriptions, scheme);
    initDiffDescriptors(descriptions, scheme);
    initFileStatusDescriptors(descriptions, scheme);
    initScopesDescriptors(descriptions, scheme);

    scheme.setDescriptors(descriptions.toArray(new EditorSchemeAttributeDescriptor[descriptions.size()]));
  }

  private static void initPluggedDescriptions(ArrayList<EditorSchemeAttributeDescriptor> descriptions, MyColorScheme scheme) {
    ColorSettingsPage[] pages = ColorSettingsPages.getInstance().getRegisteredPages();
    for (ColorSettingsPage page : pages) {
      initDescriptions(page, descriptions, scheme);
    }
  }

  private static void initDescriptions(ColorSettingsPage page,
                                       ArrayList<EditorSchemeAttributeDescriptor> descriptions,
                                       MyColorScheme scheme) {
    String group = page.getDisplayName();
    AttributesDescriptor[] attributeDescriptors = page.getAttributeDescriptors();
    for (AttributesDescriptor descriptor : attributeDescriptors) {
      addSchemedDescription(descriptions, descriptor.getDisplayName(), group, descriptor.getKey(), scheme);
    }

    ColorDescriptor[] colorDescriptors = page.getColorDescriptors();
    for (ColorDescriptor descriptor : colorDescriptors) {
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

    for (FileStatus fileStatus : statuses) {
      addEditorSettingDescription(descriptions,
                                  fileStatus.getText(),
                                  FILE_STATUS_GROUP,
                                  null,
                                  fileStatus.getColorKey(),
                                  scheme);

    }
  }
  private static void initScopesDescriptors(ArrayList<EditorSchemeAttributeDescriptor> descriptions, MyColorScheme scheme) {
    Collection<NamedScope> namedScopes = new THashSet<NamedScope>();
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      final NamedScopesHolder[] holders = project.getComponents(NamedScopesHolder.class);
      for (NamedScopesHolder holder : holders) {
        NamedScope[] scopes = holder.getEditableScopes();
        namedScopes.addAll(Arrays.asList(scopes));
      }
    }
    for (NamedScope namedScope : namedScopes) {
      String name = namedScope.getName();
      TextAttributesKey textAttributesKey = getScopeTextAttributeKey(name);
      if (scheme.getAttributes(textAttributesKey) == null) {
        scheme.setAttributes(textAttributesKey, new TextAttributes());
      }
      addSchemedDescription(descriptions,
                            name,
                            SCOPES_GROUP,
                            textAttributesKey,
                            scheme);
    }
  }

  public static TextAttributesKey getScopeTextAttributeKey(final String scope) {
    return TextAttributesKey.find("SCOPE_KEY_" + scope);
  }

  private static ColorAndFontDescription addEditorSettingDescription(ArrayList<EditorSchemeAttributeDescriptor> array,
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
    return ApplicationBundle.message("title.colors.and.fonts");
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
            : scheme.getAttributes(key).clone(),
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

      for (EditorSchemeAttributeDescriptor descriptor : myDescriptors) {
        if (descriptor.isModified()) return true;
      }

      return false;
    }

    public void apply() {
      myParentScheme.setEditorFontSize(myFontSize);
      myParentScheme.setEditorFontName(myFontName);
      myParentScheme.setLineSpacing(myLineSpacing);

      for (EditorSchemeAttributeDescriptor descriptor : myDescriptors) {
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

  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    myPanel.clearSearch();
    return true;
  }

  @Nullable
  public Runnable enableSearch(String option) {
    return myPanel.showOption(this, option);
  }

  public Map<String, String> processListOptions(){
    return myPanel.processListOptions();
  }
}
