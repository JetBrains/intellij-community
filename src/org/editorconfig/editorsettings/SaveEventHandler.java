package org.editorconfig.editorsettings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.editorconfig.SettingsProviderComponent;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.utils.ConfigConverter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SaveEventHandler implements DoneSavingListener, FileDocumentManagerListener {
    private static final Logger LOG = Logger.getInstance("#org.editorconfig.editorsettings.SaveEventHandler");

    private String originalStripTrailingSpaces;
    private boolean originalEnsureNewline;
    private boolean originalSettingsSaved;

    public SaveEventHandler() {
    }
    
    @Override
    public void doneSavingDocument(@NotNull Document document) {
        // This is only fired after the end of a save-one operation (not after a save-all operation)
        LOG.debug("Done saving one document");
        revertToOriginalEditorSettings();
    }

    @Override
    public void doneSavingAllDocuments() {
        LOG.debug("Done saving all documents");
        revertToOriginalEditorSettings();

    }

    @Override
    public void beforeAllDocumentsSaving() {
        LOG.debug("Saving all documents");
        if (originalSettingsSaved) {
            // There's no reason default settings should be stored now, because they are
            // wiped out after every save-one or save-all operation
            LOG.error("Unexpected default editor settings found");
        }
        saveOriginalEditorSettings();
    }

    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        // This is fired when any document is saved, regardless of whether it is part of a save-all or
        // a save-one operation
        LOG.debug("Saving one documents");
        if (!originalSettingsSaved) {
            saveOriginalEditorSettings();
        }
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        applySettings(file);
    }

    @Override
    public void beforeFileContentReload(VirtualFile file, @NotNull Document document) {
        //Not used
    }

    @Override
    public void fileWithNoDocumentChanged(@NotNull VirtualFile file) {
        //Not used
    }

    @Override
    public void fileContentReloaded(VirtualFile file, @NotNull Document document) {
        //Not used
    }

    @Override
    public void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {
        //Not used
    }

    @Override
    public void unsavedDocumentsDropped() {
        //Not used
    }
    
    private void applySettings(VirtualFile file) {
        // Get editorconfig settings
        String filePath = file.getCanonicalPath();
        SettingsProviderComponent settingsProvider = SettingsProviderComponent.getInstance();
        List<EditorConfig.OutPair> outPairs = settingsProvider.getOutPairs(filePath);
        ConfigConverter.applyEditorSettings(outPairs);
        LOG.debug("Applied editor settings for: " + filePath);
        
    }

    private void saveOriginalEditorSettings() {
        EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
        originalEnsureNewline = editorSettings.isEnsureNewLineAtEOF();
        originalStripTrailingSpaces = editorSettings.getStripTrailingSpaces();
        LOG.debug("Saved original editor settings");
    }

    private void revertToOriginalEditorSettings() {
        EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
        editorSettings.setEnsureNewLineAtEOF(originalEnsureNewline);
        editorSettings.setStripTrailingSpaces(originalStripTrailingSpaces);
        originalEnsureNewline = false;
        originalStripTrailingSpaces = null;
        originalSettingsSaved = false;
        LOG.debug("Reverted to original editor settings");
    }
}
