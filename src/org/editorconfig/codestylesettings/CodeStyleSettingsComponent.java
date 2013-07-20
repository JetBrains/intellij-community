package org.editorconfig.codestylesettings;

import static java.lang.System.out;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

public class CodeStyleSettingsComponent implements ProjectComponent {
    private static final Logger LOG =
            Logger.getInstance("#org.editorconfig.codestylesettings.CodeStyleSettingsComponent");

    public CodeStyleSettingsComponent(Project project) {
        EditorChangeEventHandler editorChangeEventHandler = new EditorChangeEventHandler(project);
        MessageBus bus = project.getMessageBus();
        bus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, editorChangeEventHandler);
    }

    public void initComponent() {
        // TODO: insert component initialization logic here
    }

    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @NotNull
    public String getComponentName() {
        return "CodeStyleSettingsComponent";
    }

    public void projectOpened() {
        // called when project is opened
    }

    public void projectClosed() {
        // called when project is being closed
        out.println("Project closed");
    }
}
