package org.editorconfig.plugincomponents;

import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.util.messages.MessageBus;
import org.editorconfig.configmanagement.EditorSettingsManager;
import org.jetbrains.annotations.NotNull;

public class ConfigAppComponent implements ApplicationComponent {
    public ConfigAppComponent() {
        // Create EditorSettingsManager and register event listeners
        MessageBus bus = ApplicationManager.getApplication().getMessageBus();
        EditorSettingsManager editorSettingsManager = new EditorSettingsManager();
        bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, editorSettingsManager);
    }

    public void initComponent() {
        // TODO: insert component initialization logic here
    }

    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @NotNull
    public String getComponentName() {
        return "ConfigAppComponent";
    }
}
