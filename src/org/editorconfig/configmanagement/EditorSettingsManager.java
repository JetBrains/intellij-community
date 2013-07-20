package org.editorconfig.configmanagement;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.plugincomponents.DoneSavingListener;
import org.editorconfig.utils.ConfigConverter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EditorSettingsManager implements DoneSavingListener, FileDocumentManagerListener {
    private static final Logger LOG = Logger.getInstance("#org.editorconfig.configmanagement.EditorSettingsManager");

    private String originalStripTrailingSpaces;
    private boolean originalEnsureNewline;
    private boolean originalSettingsSaved;

    public EditorSettingsManager() {
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
        if (originalSettingsSaved) {
            revertToOriginalEditorSettings();
        }
        saveOriginalEditorSettings();
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
        applyEditorSettings(outPairs);
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

    private void applyEditorSettings(List<EditorConfig.OutPair> outPairs) {
        EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
        String trimTrailingWhitespace = ConfigConverter.valueForKey(outPairs, "trim_trailing_whitespace");
        String insertFinalNewline = ConfigConverter.valueForKey(outPairs, "insert_final_newline");
        if (!trimTrailingWhitespace.isEmpty()) {
            if (trimTrailingWhitespace.equals("true")) {
                editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
            } else if (trimTrailingWhitespace.equals("false")) {
                editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
            } else {
                LOG.error("Value of trim_trailing_whitespace is invalid");
            }
        }
        if (!insertFinalNewline.isEmpty()) {
            if (insertFinalNewline.equals("true") || insertFinalNewline.equals("false")) {
                editorSettings.setEnsureNewLineAtEOF(insertFinalNewline.equals("true"));
            }
        } else {
            LOG.error("Value of trim_trailing_whitespace is invalid");
        }
    }
}
