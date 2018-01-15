package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.ide.ui.laf.intellij.MacIntelliJTextFieldUI;
import com.intellij.ide.ui.laf.intellij.WinIntelliJTextFieldUI;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.util.Consumer;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;

class RunAnythingIconHandler implements PropertyChangeListener {
  public static final String DEBUG_ICON_TEXT = "DEBUG_ICON_TEXT";
  public static final String MODULE_CONTEXT_ICON_TEXT = "MODULE_CONTEXT_ICON_TEXT";
  private static final String FOREGROUND_PROPERTY = "foreground";
  protected static final String MATCHED_CONFIGURATION_PROPERTY = "JTextField.match";
  protected static final String EXEC_TYPE_PROPERTY = "JTextField.execType";

  private final NotNullLazyValue<Map<String, Icon>> myIconsMap;

  private final Consumer<ExtendableTextField.Extension> myConsumer;

  private final JTextComponent myComponent;

  public RunAnythingIconHandler(@NotNull NotNullLazyValue<Map<String, Icon>> iconsMap,
                               @NotNull Consumer<ExtendableTextField.Extension> consumer,
                               @NotNull JTextComponent component) {
    myIconsMap = iconsMap;
    myConsumer = consumer;
    myComponent = component;

    setConfigurationIcon(component.getClientProperty(MATCHED_CONFIGURATION_PROPERTY));
    setExecutionTypeIcon(component.getClientProperty(EXEC_TYPE_PROPERTY));
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (myComponent == null) return;

    if (MATCHED_CONFIGURATION_PROPERTY.equals(event.getPropertyName())) {
      setConfigurationIcon(event.getNewValue());
    }
    else if (EXEC_TYPE_PROPERTY.equals(event.getPropertyName())) {
      setExecutionTypeIcon(event.getNewValue());
    }
    else if (FOREGROUND_PROPERTY.equals(event.getPropertyName())) {
      myComponent.setForeground(UIUtil.getTextFieldForeground());
    }

    myComponent.repaint();
  }

  private void setExecutionTypeIcon(Object variant) {
    if (!(variant instanceof String)) return;

    myConsumer.consume(new ExecutionTypeExtension(((String)variant)));
  }

  private void setConfigurationIcon(Object variant) {
    if (!(variant instanceof String)) return;

    myConsumer.consume(new RunConfigurationTypeExtension(((String)variant)));
  }

  private static void installIconListeners(@NotNull NotNullLazyValue<Map<String, Icon>> iconsMap,
                                           @NotNull Consumer<ExtendableTextField.Extension> extensionConsumer,
                                           @NotNull JTextComponent component) {
    RunAnythingIconHandler handler = new RunAnythingIconHandler(iconsMap, extensionConsumer, component);
    component.addPropertyChangeListener(handler);
  }

  public static class MyDarcula extends DarculaTextFieldUI {
    private NotNullLazyValue<Map<String, Icon>> myIconsMap;

    public MyDarcula(NotNullLazyValue<Map<String, Icon>> map) {myIconsMap = map;}

    @Override
    protected Icon getSearchIcon(boolean hovered, boolean clickable) {
      return EmptyIcon.ICON_0;
    }

    @Override
    protected Icon getClearIcon(boolean hovered, boolean clickable) {
      return EmptyIcon.ICON_0;
    }

    @Override
    protected void installListeners() {
      super.installListeners();
      installIconListeners(myIconsMap, this::addExtension, getComponent());
    }
  }

  public static class MyMacUI extends MacIntelliJTextFieldUI {
    private NotNullLazyValue<Map<String, Icon>> myIconsMap;

    public MyMacUI(NotNullLazyValue<Map<String, Icon>> map) {myIconsMap = map;}

    @Override
    protected Icon getSearchIcon(boolean hovered, boolean clickable) {
      return EmptyIcon.ICON_0;
    }

    @Override
    protected Icon getClearIcon(boolean hovered, boolean clickable) {
      return EmptyIcon.ICON_0;
    }

    @Override
    protected void installListeners() {
      super.installListeners();
      installIconListeners(myIconsMap, this::addExtension, getComponent());
    }
  }

  public static class MyWinUI extends WinIntelliJTextFieldUI {
    private NotNullLazyValue<Map<String, Icon>> myIconsMap;

    public MyWinUI(NotNullLazyValue<Map<String, Icon>> map) {myIconsMap = map;}

    @Override
    protected Icon getSearchIcon(boolean hovered, boolean clickable) {
      return EmptyIcon.ICON_0;
    }

    @Override
    protected Icon getClearIcon(boolean hovered, boolean clickable) {
      return EmptyIcon.ICON_0;
    }

    @Override
    public void installListeners() {
      super.installListeners();
      installIconListeners(myIconsMap, this::addExtension, getComponent());
    }
  }

  private static class ExecutionTypeExtension implements ExtendableTextField.Extension {
    private final String myVariant;

    public ExecutionTypeExtension(String variant) {myVariant = variant;}

    @Override
    public Icon getIcon(boolean hovered) {
      if (MODULE_CONTEXT_ICON_TEXT.equals(myVariant)) {
        return AllIcons.Actions.Rerun;
      } else if (DEBUG_ICON_TEXT.equals(myVariant)) {
        return AllIcons.Toolwindows.ToolWindowDebugger;
      }
      else {
        return AllIcons.Toolwindows.ToolWindowRun;
      }
    }

    @Override
    public String toString() {
      return EXEC_TYPE_PROPERTY;
    }
  }

  private class RunConfigurationTypeExtension implements ExtendableTextField.Extension {
    private final String myVariant;

    public RunConfigurationTypeExtension(String variant) {myVariant = variant;}

    @Override
    public Icon getIcon(boolean hovered) {
      return myIconsMap.getValue().getOrDefault(myVariant, AllIcons.RunConfigurations.Unknown);
    }

    @Override
    public boolean isIconBeforeText() {
      return true;
    }

    @Override
    public String toString() {
      return MATCHED_CONFIGURATION_PROPERTY;
    }
  }

}


