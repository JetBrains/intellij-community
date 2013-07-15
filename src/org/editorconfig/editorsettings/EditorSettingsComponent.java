package org.editorconfig.editorsettings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

public class EditorSettingsComponent implements ApplicationComponent {
    private final SaveEventHandler saveHandler;
    
    public EditorSettingsComponent() {
        String fileDocumentManagerKey = FileDocumentManager.class.getName();
        ComponentConfig config = new ComponentConfig();
        config.setInterfaceClass(FileDocumentManager.class.getName());
        config.setImplementationClass(ReplacementFileDocumentManager.class.getName());
        MutablePicoContainer container = (MutablePicoContainer)ApplicationManager.getApplication().getPicoContainer();
        container.unregisterComponent(fileDocumentManagerKey);
        container.registerComponentImplementation(fileDocumentManagerKey, ReplacementFileDocumentManager.class); 
        saveHandler = new SaveEventHandler();
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
