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
import com.intellij.util.messages.MessageBus;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.editorsettings.ReplacementFileDocumentManager;
import org.editorconfig.utils.ConfigConverter;
import org.editorconfig.editorsettings.DoneSavingListener;
import org.editorconfig.editorsettings.DoneSavingTopic;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class EndOfLineComponent implements ProjectComponent, FileDocumentManagerListener, DoneSavingListener {
    private final Logger LOG = Logger.getInstance("#org.editorconfig.codestylesettings.EndOfLineComponent");
    private final Project project;
    private final List<Document> documentsToChange = new ArrayList<Document>();


    public EndOfLineComponent(Project project) {
        this.project = project;
        MessageBus bus = ApplicationManager.getApplication().getMessageBus();
        bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, this);
        bus.connect().subscribe(DoneSavingTopic.DONE_SAVING, this);
    }

    public void initComponent() {
        // TODO: insert component initialization logic here
    }

    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @NotNull
    public String getComponentName() {
        return "EndOfLineComponent";
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
        // Keep track of documents to change here, because we can't get a list
        // of changed documents at the end of a save-all operation
        documentsToChange.add(document);
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


    @Override
    public void doneSavingAllDocuments() {
        applyChanges();
    }

    @Override
    public void doneSavingDocument(@NotNull Document document) {
        applyChanges();
    }

    private void applyChanges () {
        ReplacementFileDocumentManager fileDocumentManager =
                (ReplacementFileDocumentManager) FileDocumentManager.getInstance();
        for (Document document: documentsToChange) {
            fileDocumentManager.setAlwaysReload(true);
            applySettings(document);
            fileDocumentManager.setAlwaysReload(false);
            documentsToChange.remove(document);
        }
    }

    private void applySettings(Document document) {
        final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        if (file == null) {
            return;
        }
        String filePath = file.getCanonicalPath();
        final List<EditorConfig.OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(filePath);
        ConfigConverter.applyEndOfLine(outPairs, project, file, this);
        LOG.debug("Applied end of line settings for: " + filePath);
    }
}
