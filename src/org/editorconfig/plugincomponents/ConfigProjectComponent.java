package org.editorconfig.plugincomponents;

import com.intellij.AppTopics;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;
import org.editorconfig.configmanagement.CodeStyleManager;
import org.editorconfig.configmanagement.EncodingManager;
import org.editorconfig.configmanagement.EndOfLineManager;
import org.jetbrains.annotations.NotNull;

public class ConfigProjectComponent implements ProjectComponent {
    public ConfigProjectComponent(Project project) {
        // Register project-level config managers
        MessageBus bus = project.getMessageBus();
        CodeStyleManager codeStyleManager = new CodeStyleManager(project);
        EncodingManager encodingManager = new EncodingManager(project);
        EndOfLineManager endOfLineManager = new EndOfLineManager(project);
        bus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, codeStyleManager);
        bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, encodingManager);
        bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, endOfLineManager);
        bus.connect().subscribe(DoneSavingTopic.DONE_SAVING, endOfLineManager);

    }

    public void initComponent() {
        // TODO: insert component initialization logic here
    }

    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @NotNull
    public String getComponentName() {
        return "ConfigProjectComponent";
    }

    public void projectOpened() {
        // called when project is opened
    }

    public void projectClosed() {
        // called when project is being closed
    }
}
