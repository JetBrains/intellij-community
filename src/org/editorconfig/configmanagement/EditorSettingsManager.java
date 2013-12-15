package org.editorconfig.configmanagement;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.impl.TrailingSpacesStripper;
import com.intellij.openapi.vfs.VirtualFile;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EditorSettingsManager implements FileDocumentManagerListener {
    // Handles the following EditorConfig settings:
    private static final String trimTrailingWhitespaceKey = "trim_trailing_whitespace";
    private static final String insertFinalNewlineKey = "insert_final_newline";

    private static final Logger LOG = Logger.getInstance("#org.editorconfig.configmanagement.EditorSettingsManager");

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

    private void applySettings(VirtualFile file) {
        // Get editorconfig settings
        String filePath = file.getCanonicalPath();
        SettingsProviderComponent settingsProvider = SettingsProviderComponent.getInstance();
        List<EditorConfig.OutPair> outPairs = settingsProvider.getOutPairs(filePath);
        String trimTrailingWhitespace = Utils.configValueForKey(outPairs, trimTrailingWhitespaceKey);
        String insertFinalNewline = Utils.configValueForKey(outPairs, insertFinalNewlineKey);
        // Apply trailing spaces setting
        if (trimTrailingWhitespace.equals("true")) {
            file.putUserData(TrailingSpacesStripper.OVERRIDE_STRIP_TRAILING_SPACES_KEY,
                             EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
        } else if (trimTrailingWhitespace.equals("false")) {
            file.putUserData(TrailingSpacesStripper.OVERRIDE_STRIP_TRAILING_SPACES_KEY,
                             EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
        } else {
            if (!trimTrailingWhitespace.isEmpty()) {
                LOG.warn(Utils.invalidConfigMessage(trimTrailingWhitespace, trimTrailingWhitespaceKey, filePath));
            }
            file.putUserData(TrailingSpacesStripper.OVERRIDE_STRIP_TRAILING_SPACES_KEY, null);
        }
        // Apply final newline setting
        if (insertFinalNewline.equals("true")) {
            file.putUserData(TrailingSpacesStripper.OVERRIDE_ENSURE_NEWLINE_KEY,
                             TrailingSpacesStripper.ENSURE_NEWLINE);
        } else if (insertFinalNewline.equals("false")) {
            file.putUserData(TrailingSpacesStripper.OVERRIDE_ENSURE_NEWLINE_KEY,
                             TrailingSpacesStripper.DONT_ENSURE_NEWLINE);
        } else {
            if (!insertFinalNewline.isEmpty()) {
                LOG.warn(Utils.invalidConfigMessage(insertFinalNewline, insertFinalNewlineKey, filePath));
            }
            file.putUserData(TrailingSpacesStripper.OVERRIDE_ENSURE_NEWLINE_KEY, null);
        }
        LOG.debug("Applied editor settings for: " + filePath);
        
    }
}
