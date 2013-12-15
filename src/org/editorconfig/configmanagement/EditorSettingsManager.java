package org.editorconfig.configmanagement;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.impl.TrailingSpacesStripper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditorSettingsManager implements FileDocumentManagerListener {
    // Handles the following EditorConfig settings:
    private static final String trimTrailingWhitespaceKey = "trim_trailing_whitespace";
    private static final String insertFinalNewlineKey = "insert_final_newline";
    private static final Map<String, String> trimMap;
    static {
        Map<String, String> map = new HashMap<String, String>();
        map.put("true", EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
        map.put("false", EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
        trimMap = Collections.unmodifiableMap(map);
    }
    private static final Map<String, String> newlineMap;
    static {
        Map<String, String> map = new HashMap<String, String>();
        map.put("true", TrailingSpacesStripper.ENSURE_NEWLINE);
        map.put("false", TrailingSpacesStripper.DONT_ENSURE_NEWLINE);
        newlineMap = Collections.unmodifiableMap(map);
    }

    private static final Logger LOG = Logger.getInstance("#org.editorconfig.configmanagement.EditorSettingsManager");

    @Override
    public void beforeAllDocumentsSaving() {
        // Not used
    }

    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        // This is fired when any document is saved, regardless of whether it is part of a save-all or
        // a save-one operation
        final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
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
        final String filePath = file.getCanonicalPath();
        final SettingsProviderComponent settingsProvider = SettingsProviderComponent.getInstance();
        final List<EditorConfig.OutPair> outPairs = settingsProvider.getOutPairs(filePath);
        // Apply trailing spaces setting
        final String trimTrailingWhitespace = Utils.configValueForKey(outPairs, trimTrailingWhitespaceKey);
        applyConfigValueToUserData(file, TrailingSpacesStripper.OVERRIDE_STRIP_TRAILING_SPACES_KEY,
                                   trimTrailingWhitespaceKey, trimTrailingWhitespace, trimMap);
        // Apply final newline setting
        final String insertFinalNewline = Utils.configValueForKey(outPairs, insertFinalNewlineKey);
        applyConfigValueToUserData(file, TrailingSpacesStripper.OVERRIDE_ENSURE_NEWLINE_KEY,
                                   insertFinalNewlineKey, insertFinalNewline, newlineMap);
    }

    private void applyConfigValueToUserData(VirtualFile file, Key userDataKey, String editorConfigKey,
                                            String configValue, Map<String, String> configMap) {
        if (configValue.isEmpty()) {
            file.putUserData(userDataKey, null);
        } else {
            final String data = configMap.get(configValue);
            if (data == null) {
                LOG.warn(Utils.invalidConfigMessage(configValue, editorConfigKey, file.getCanonicalPath()));
            } else {
                file.putUserData(userDataKey, data);
                LOG.debug("Applied " + editorConfigKey + " settings for: " + file.getCanonicalPath());
            }
        }
    }
}
