package org.jetbrains.builtInWebServer;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SimpleConfigurable;
import com.intellij.openapi.util.Getter;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.settings.DebuggerConfigurableProvider;
import com.intellij.xdebugger.settings.DebuggerSettingsCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManager;
import org.jetbrains.ide.CustomPortServerManager;
import org.jetbrains.io.CustomPortServerManagerBase;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

@State(
  name = "BuiltInServerOptions",
  storages = {
    @Storage(
      file = StoragePathMacros.APP_CONFIG + "/other.xml"
    )}
)
public class BuiltInServerOptions implements PersistentStateComponent<BuiltInServerOptions>, ExportableComponent, Getter<BuiltInServerOptions> {
  @Attribute
  public int builtInServerPort = 63342;
  @Attribute
  public boolean builtInServerAvailableExternally = false;

  public static BuiltInServerOptions getInstance() {
    return ServiceManager.getService(BuiltInServerOptions.class);
  }

  @Override
  public BuiltInServerOptions get() {
    return this;
  }

  static final class BuiltInServerDebuggerConfigurableProvider extends DebuggerConfigurableProvider {
    @NotNull
    @Override
    public Collection<? extends Configurable> getConfigurables(@NotNull DebuggerSettingsCategory category) {
      if (category == DebuggerSettingsCategory.GENERAL) {
        return Collections.singletonList(SimpleConfigurable.create("builtInServer", "", BuiltInServerConfigurableUi.class, getInstance()));
      }
      return Collections.emptyList();
    }
  }

  @NotNull
  @Override
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile("other")};
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Built-in server";
  }

  @Nullable
  @Override
  public BuiltInServerOptions getState() {
    return this;
  }

  @Override
  public void loadState(BuiltInServerOptions state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public int getEffectiveBuiltInServerPort() {
    MyCustomPortServerManager portServerManager = CustomPortServerManager.EP_NAME.findExtension(MyCustomPortServerManager.class);
    if (!portServerManager.isBound()) {
      return BuiltInServerManager.getInstance().getPort();
    }
    return builtInServerPort;
  }

  public static final class MyCustomPortServerManager extends CustomPortServerManagerBase {
    @Override
    public void cannotBind(Exception e, int port) {
      String groupDisplayId = "Built-in Web Server";
      Notifications.Bus.register(groupDisplayId, NotificationDisplayType.STICKY_BALLOON);
      new Notification(groupDisplayId, "Built-in HTTP server on custom port " + port + " disabled",
                       "Cannot start built-in HTTP server on custom port " + port + ". " +
                       "Please ensure that port is free (or check your firewall settings) and restart " + ApplicationNamesInfo.getInstance().getFullProductName(),
                       NotificationType.ERROR).notify(null);
    }

    @Override
    public int getPort() {
      return getInstance().builtInServerPort;
    }

    @Override
    public boolean isAvailableExternally() {
      return getInstance().builtInServerAvailableExternally;
    }
  }

  public static void onBuiltInServerPortChanged() {
    CustomPortServerManager.EP_NAME.findExtension(MyCustomPortServerManager.class).portChanged();
  }
}