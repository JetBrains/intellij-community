package com.intellij.ide.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.xmlb.Accessor;
import com.intellij.util.xmlb.SerializationFilter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.io.File;
import java.util.Map;

@State(
  name = "UISettings",
  storages = {
    @Storage(
      id ="uilnf",
      file = "$APP_CONFIG$/ui.lnf.xml"
    )}
)
public class UISettings implements PersistentStateComponent<UISettings>, ExportableApplicationComponent {
  private EventListenerList myListenerList;

  @Property(filter = FontFilter.class)
  @NonNls
  public String FONT_FACE;
  @Property(filter = FontFilter.class)
  public int FONT_SIZE;

  public int RECENT_FILES_LIMIT = 15;
  public int EDITOR_TAB_LIMIT = 10;
  public boolean ANIMATE_WINDOWS = true;
  public int ANIMATION_SPEED = 2000; // Pixels per second
  public boolean SHOW_WINDOW_SHORTCUTS = true;
  public boolean HIDE_TOOL_STRIPES = false;
  public boolean SHOW_MEMORY_INDICATOR = true;
  public boolean SHOW_MAIN_TOOLBAR = true;
  public boolean SHOW_STATUS_BAR = true;
  public boolean SHOW_NAVIGATION_BAR = true;
  public boolean ALWAYS_SHOW_WINDOW_BUTTONS = false;
  public boolean CYCLE_SCROLLING = true;
  public boolean SCROLL_TAB_LAYOUT_IN_EDITOR = false;
  public int EDITOR_TAB_PLACEMENT = 1;
  public boolean HIDE_KNOWN_EXTENSION_IN_TABS = false;
  public boolean SHOW_ICONS_IN_QUICK_NAVIGATION = true;
  public boolean CLOSE_NON_MODIFIED_FILES_FIRST = false;
  public boolean ACTIVATE_MRU_EDITOR_ON_CLOSE = false;
  public boolean ANTIALIASING_IN_EDITOR;
  public boolean MOVE_MOUSE_ON_DEFAULT_BUTTON = false;
  public boolean ENABLE_ALPHA_MODE = false;
  public int ALPHA_MODE_DELAY = 1500;
  public float ALPHA_MODE_RATIO = 0.5f;
  public int MAX_CLIPBOARD_CONTENTS = 5;
  public boolean OVERRIDE_NONIDEA_LAF_FONTS = false;

  /**
   * Defines whether asterisk is shown on modified editor tab or not
   */
  public boolean MARK_MODIFIED_TABS_WITH_ASTERISK = false;

  /**
   * Not tabbed pane
   */
  public static final int TABS_NONE = 0;

  /** Invoked by reflection */
  public UISettings(){
    myListenerList=new EventListenerList();
    ANTIALIASING_IN_EDITOR = SystemInfo.isMac;
    setSystemFontFaceAndSize();
  }

  public void addUISettingsListener(UISettingsListener listener){
    myListenerList.add(UISettingsListener.class,listener);
  }

  /**
   * Notifies all registered listeners that UI settings has been changed.
   */
  public void fireUISettingsChanged(){
    UISettingsListener[] listeners= myListenerList.getListeners(UISettingsListener.class);
    for (UISettingsListener listener : listeners) {
      listener.uiSettingsChanged(this);
    }
  }

  public static UISettings getInstance() {
    return ApplicationManager.getApplication().getComponent(UISettings.class);
  }

  public void removeUISettingsListener(UISettingsListener listener){
    myListenerList.remove(UISettingsListener.class,listener);
  }

  private void setDefaultFontSettings(){
    FONT_FACE = "dialog";
    FONT_SIZE = 12;
  }

  private static boolean isValidFont(final Font font){
    try {
      return
        font.canDisplay('a') &&
        font.canDisplay('z') &&
        font.canDisplay('A') &&
        font.canDisplay('Z') &&
        font.canDisplay('0') &&
        font.canDisplay('1');
    }
    catch (Exception e) {
      // JRE has problems working with the font. Just skip.
      return false;
    }
  }

  /**
   * Under Win32 it's possible to determine face and size of default fount.
   */
  private void setSystemFontFaceAndSize(){
    if(FONT_FACE == null || FONT_SIZE <= 0){
      if(SystemInfo.isWindows){
        //noinspection HardCodedStringLiteral
        Font font=(Font)Toolkit.getDefaultToolkit().getDesktopProperty("win.messagebox.font");
        if(font != null){
          FONT_FACE = font.getName();
          FONT_SIZE = font.getSize();
        }else{
          setDefaultFontSettings();
        }
      }else{ // UNIXes go here
        setDefaultFontSettings();
      }
    }
  }

  private static boolean hasDefaultFontSetting(final UISettings settings) {
    Font font=(Font)Toolkit.getDefaultToolkit().getDesktopProperty("win.messagebox.font");
    return SystemInfo.isWindows && font != null && settings.FONT_FACE.equals(font.getName()) && settings.FONT_SIZE == font.getSize();

  }

  public UISettings getState() {
    return this;
  }

  public void loadState(UISettings object) {
    XmlSerializerUtil.copyBean(object, this);

    // Check tab placement in editor
    if(
      EDITOR_TAB_PLACEMENT != TABS_NONE &&
      EDITOR_TAB_PLACEMENT != SwingConstants.TOP&&
      EDITOR_TAB_PLACEMENT != SwingConstants.LEFT&&
      EDITOR_TAB_PLACEMENT != SwingConstants.BOTTOM&&
      EDITOR_TAB_PLACEMENT != SwingConstants.RIGHT
    ){
      EDITOR_TAB_PLACEMENT=SwingConstants.TOP;
    }
    // Check that alpha ration in in valid range
    if(ALPHA_MODE_DELAY<0){
      ALPHA_MODE_DELAY=1500;
    }
    if(ALPHA_MODE_RATIO< 0.0f ||ALPHA_MODE_RATIO>1.0f){
      ALPHA_MODE_RATIO=0.5f;
    }

    setSystemFontFaceAndSize();
    // 1. Sometimes system font cannot display standard ASCI symbols. If so we have
    // find any other suitable font withing "preferred" fonts first.
    boolean fontIsValid = isValidFont(new Font(FONT_FACE, Font.PLAIN, FONT_SIZE));
    if(!fontIsValid){
      @NonNls final String[] preferredFonts = new String[]{"dialog", "Arial", "Tahoma"};
      for (String preferredFont : preferredFonts) {
        if (isValidFont(new Font(preferredFont, Font.PLAIN, FONT_SIZE))) {
          FONT_FACE = preferredFont;
          fontIsValid = true;
          break;
        }
      }

      // 2. If all preferred fonts are not valid in current environment
      // we have to find first valid font (if any)
      if(!fontIsValid){
        Font[] fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        for (Font font : fonts) {
          if (isValidFont(font)) {
            FONT_FACE = font.getName();
            break;
          }
        }
      }
    }

    if (MAX_CLIPBOARD_CONTENTS <= 0) {
      MAX_CLIPBOARD_CONTENTS = 5;
    }


    fireUISettingsChanged();
  }

  public static class FontFilter implements SerializationFilter {
    public boolean accepts(Accessor accessor, Object bean) {
      UISettings settings = (UISettings)bean;

      return !hasDefaultFontSetting(settings);
    }

  }

  public static void setupAntialiasing(final Graphics g) {
    Graphics2D g2d=(Graphics2D)g;
    UISettings uiSettings=getInstance();

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
    if(uiSettings.ANTIALIASING_IN_EDITOR) {
      Toolkit tk = Toolkit.getDefaultToolkit();
      //noinspection HardCodedStringLiteral
      Map map = (Map)tk.getDesktopProperty("awt.font.desktophints");
      if (map != null) {
        g2d.addRenderingHints(map);
      }
      else {
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      }
    }
    else {
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    }
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("ui.lnf")};
  }

  @NotNull
  public String getPresentableName() {
    return IdeBundle.message("ui.settings");
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "UISettings";
  }

  public void initComponent() {

  }

  public void disposeComponent() {

  }
}