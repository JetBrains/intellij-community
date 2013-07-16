package org.editorconfig;

import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.util.messages.MessageBus;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.utils.ConfigConverter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EncodingComponent implements ProjectComponent, FileDocumentManagerListener {
    private final Logger LOG = Logger.getInstance("#org.editorconfig.codestylesettings.EncodingComponent");
    private final Project project;

    public EncodingComponent(Project project) {
        this.project = project;
        MessageBus bus = ApplicationManager.getApplication().getMessageBus();
        bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, this);
    }

    public void initComponent() {
        // TODO: insert component initialization logic here
    }

    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @NotNull
    public String getComponentName() {
        return "EncodingComponent";
    }

    public void projectOpened() {
        // Not used
    }

    public void projectClosed() {
        // Not used
    }

    @Override
    public void beforeAllDocumentsSaving() {
        // Not used
    }

    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        applySettings(file);
    }

    @Override
    public void beforeFileContentReload(VirtualFile file, @NotNull Document document) {
        // Not used
    }

    @Override
    public void fileWithNoDocumentChanged(@NotNull VirtualFile file) {
        // Not used
    }

    @Override
    public void fileContentReloaded(VirtualFile file, @NotNull Document document) {
        // Not used
    }

    @Override
    public void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {
        // Not used
    }

    @Override
    public void unsavedDocumentsDropped() {
        // Not used
    }

    private void applySettings(VirtualFile file) {
        String filePath = file.getCanonicalPath();
        List<OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(filePath);
        EncodingProjectManager encodingManager = EncodingProjectManager.getInstance(project);
        ConfigConverter.applyEncodingSettings(outPairs, encodingManager, file);
        LOG.debug("Applied encoding settings for: " + filePath);
    }
}
