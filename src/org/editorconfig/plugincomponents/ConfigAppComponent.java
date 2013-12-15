package org.editorconfig.plugincomponents;

import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.util.messages.MessageBus;
import org.editorconfig.configmanagement.EditorSettingsManager;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

public class ConfigAppComponent implements ApplicationComponent {
    public ConfigAppComponent() {
        // Load a couple replacement classes to provide extra events
        ComponentManagerImpl componentManager = (ComponentManagerImpl) ApplicationManager.getApplication();
        MutablePicoContainer container = (MutablePicoContainer) ApplicationManager.getApplication().getPicoContainer();

        // Register replacement FileDocumentManager
        String fileDocumentManagerKey = FileDocumentManager.class.getName();
        container.unregisterComponent(fileDocumentManagerKey);
        componentManager.registerComponentImplementation(FileDocumentManager.class, ReplacementFileDocumentManager.class);

        // Create EditorSettingsManager and register event listeners
        MessageBus bus = ApplicationManager.getApplication().getMessageBus();
        EditorSettingsManager editorSettingsManager = new EditorSettingsManager();
        bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, editorSettingsManager);
        EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
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
