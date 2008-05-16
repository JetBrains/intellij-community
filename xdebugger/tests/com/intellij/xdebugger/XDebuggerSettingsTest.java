package com.intellij.xdebugger;

import com.intellij.openapi.options.Configurable;
import com.intellij.testFramework.LiteFixture;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.settings.XDebuggerSettingsManager;
import com.intellij.xdebugger.settings.XDebuggerSettings;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XDebuggerSettingsTest extends LiteFixture {
  protected void setUp() throws Exception {
    super.setUp();
    initApplication();
    registerExtensionPoint(XDebuggerSettings.EXTENSION_POINT, XDebuggerSettings.class);
    registerExtension(XDebuggerSettings.EXTENSION_POINT, new MyDebuggerSettings());
    getApplication().registerService(XDebuggerUtil.class, XDebuggerUtilImpl.class);
    getApplication().registerService(XDebuggerSettingsManager.class, XDebuggerSettingsManager.class);
  }

  public void testSerialize() throws Exception {
    XDebuggerSettingsManager settingsManager = XDebuggerSettingsManager.getInstance();

    MyDebuggerSettings settings = MyDebuggerSettings.getInstance();
    assertNotNull(settings);
    settings.myOption = "239";

    Element element = XmlSerializer.serialize(settingsManager.getState());
    //System.out.println(JDOMUtil.writeElement(element, SystemProperties.getLineSeparator()));

    settings.myOption = "42";
    assertSame(settings, MyDebuggerSettings.getInstance());

    settingsManager.loadState(XmlSerializer.deserialize(element, XDebuggerSettingsManager.SettingsState.class));
    assertSame(settings, MyDebuggerSettings.getInstance());
    assertEquals("239", settings.myOption);
  }


  public static class MyDebuggerSettings extends XDebuggerSettings<MyDebuggerSettings> {
    @Attribute("option")
    public String myOption;

    public MyDebuggerSettings() {
      super("test");
    }

    public static MyDebuggerSettings getInstance() {
      return getInstance(MyDebuggerSettings.class);
    }

    public MyDebuggerSettings getState() {
      return this;
    }

    public void loadState(final MyDebuggerSettings state) {
      myOption = state.myOption;
    }

    @NotNull
    public Configurable createConfigurable() {
      throw new UnsupportedOperationException("'createConfigurable' not implemented in " + getClass().getName());
    }
  }
}
