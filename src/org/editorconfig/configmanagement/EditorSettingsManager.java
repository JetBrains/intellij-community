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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

public class EditorSettingsManager implements DoneSavingListener, FileDocumentManagerListener, PropertyChangeListener {
    // Handles the following EditorConfig settings:
    private static final String trimTrailingWhitespaceKey = "trim_trailing_whitespace";
    private static final String insertFinalNewlineKey = "insert_final_newline";

    private static final Logger LOG = Logger.getInstance("#org.editorconfig.configmanagement.EditorSettingsManager");

    private boolean defaultEnsureNewline;
    private String defaultStripTrailingSpaces;

    public EditorSettingsManager() {
        saveDefaultEditorSettings();
    }
    
    @Override
    public void doneSavingDocument(@NotNull Document document) {
        // This is only fired after the end of a save-one operation (not after a save-all operation)
        LOG.debug("Done saving one document");
        revertToDefaultEditorSettings();
    }

    @Override
    public void doneSavingAllDocuments() {
        LOG.debug("Done saving all documents");
        revertToDefaultEditorSettings();
    }

    @Override
    public void beforeAllDocumentsSaving() {
        // Not used
    }

    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        // This is fired when any document is saved, regardless of whether it is part of a save-all or
        // a save-one operation
        LOG.debug("Saving one document");
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

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        // This should only happen when ensureEOL or stripSpaces changes
        saveDefaultEditorSettings();
    }

    private void applySettings(VirtualFile file) {
        // Get editorconfig settings
        String filePath = file.getCanonicalPath();
        SettingsProviderComponent settingsProvider = SettingsProviderComponent.getInstance();
        List<EditorConfig.OutPair> outPairs = settingsProvider.getOutPairs(filePath);
        applyEditorSettings(outPairs, filePath);
        LOG.debug("Applied editor settings for: " + filePath);
        
    }

    private void saveDefaultEditorSettings() {
        EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
        defaultEnsureNewline = editorSettings.isEnsureNewLineAtEOF();
        defaultStripTrailingSpaces = editorSettings.getStripTrailingSpaces();
    }

    private void revertToDefaultEditorSettings() {
        EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
        editorSettings.setEnsureNewLineAtEOF(defaultEnsureNewline);
        editorSettings.setStripTrailingSpaces(defaultStripTrailingSpaces);
    }

    private void applyEditorSettings(List<EditorConfig.OutPair> outPairs, String filePath) {
        EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
        String trimTrailingWhitespace = ConfigConverter.valueForKey(outPairs, trimTrailingWhitespaceKey);
        String insertFinalNewline = ConfigConverter.valueForKey(outPairs, insertFinalNewlineKey);
        try {
            applyTrimTrailingWhitespace(editorSettings, trimTrailingWhitespace, filePath);
        }
        catch(InvalidConfigException e) {
            LOG.warn(e.getMessage());
        }
        try {
            applyInsertFinalNewline(editorSettings, insertFinalNewline, filePath);
        }
        catch(InvalidConfigException e) {
            LOG.warn(e.getMessage());
        }
    }

    private void applyTrimTrailingWhitespace(EditorSettingsExternalizable editorSettings,
                                             String trimTrailingWhitespace, String filePath)
                                                throws InvalidConfigException {
        if (trimTrailingWhitespace.isEmpty()) {
            editorSettings.setStripTrailingSpaces(defaultStripTrailingSpaces);
        } else {
            if (trimTrailingWhitespace.equals("true")) {
                editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
            } else if (trimTrailingWhitespace.equals("false")) {
                editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
            } else {
                editorSettings.setStripTrailingSpaces(defaultStripTrailingSpaces);
                throw new InvalidConfigException(trimTrailingWhitespaceKey, trimTrailingWhitespace, filePath);
            }
        }
    }

    private void applyInsertFinalNewline(EditorSettingsExternalizable editorSettings, String insertFinalNewline,
                                         String filePath) throws InvalidConfigException {
        if (insertFinalNewline.isEmpty()) {
            editorSettings.setEnsureNewLineAtEOF(defaultEnsureNewline);
        } else {
            if (insertFinalNewline.equals("true") || insertFinalNewline.equals("false")) {
                editorSettings.setEnsureNewLineAtEOF(insertFinalNewline.equals("true"));
            }
            else {
                editorSettings.setEnsureNewLineAtEOF(defaultEnsureNewline);
                throw new InvalidConfigException(insertFinalNewlineKey, insertFinalNewline, filePath);
            }
        }
    }
}
