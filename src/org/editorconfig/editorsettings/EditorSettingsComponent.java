package org.editorconfig.editorsettings;

import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

public class EditorSettingsComponent implements ApplicationComponent {

    public EditorSettingsComponent() {
        String fileDocumentManagerKey = FileDocumentManager.class.getName();
        ComponentConfig config = new ComponentConfig();
        config.setInterfaceClass(FileDocumentManager.class.getName());
        config.setImplementationClass(ReplacementFileDocumentManager.class.getName());
        MutablePicoContainer container = (MutablePicoContainer)ApplicationManager.getApplication().getPicoContainer();
        container.unregisterComponent(fileDocumentManagerKey);
        container.registerComponentImplementation(fileDocumentManagerKey, ReplacementFileDocumentManager.class); 
        SaveEventHandler saveEventHandler = new SaveEventHandler();
        MessageBus bus = ApplicationManager.getApplication().getMessageBus();
        bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, saveEventHandler);
        bus.connect().subscribe(DoneSavingTopic.DONE_SAVING, saveEventHandler);
    }

    public void initComponent() {
        // TODO: insert component initialization logic here
    }

    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @NotNull
    public String getComponentName() {
        return "EditorSettingsComponent";
    }
}
