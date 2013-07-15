package org.editorconfig.codestylesettings;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.messages.MessageBus;
import org.editorconfig.SettingsProviderComponent;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.utils.ConfigConverter;

import java.util.Collection;
import java.util.List;

public class EditorChangeEventHandler implements FileEditorManagerListener {
    private static final Logger LOG = 
            Logger.getInstance("#org.editorconfig.codestylesettings.EditorChangeEventHandler");
    private final CodeStyleSettingsManager codeStyleSettingsManager;

    public EditorChangeEventHandler(Project project) {
        MessageBus bus = project.getMessageBus();
        bus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
        codeStyleSettingsManager = CodeStyleSettingsManager.getInstance(project);
    }

    @Override
    public void fileOpened(FileEditorManager source, VirtualFile file) {
        applySettings(file);
    }

    @Override
    public void fileClosed(FileEditorManager source, VirtualFile file) {
        // Not used
    }

    @Override
    public void selectionChanged(FileEditorManagerEvent event) {
        VirtualFile file = event.getNewFile();
        applySettings(file);
    }
    
    private void applySettings(VirtualFile file) {
        // Always drop any current temporary settings so that the defaults will be applied if
        // this is a non-editorconfig-managed file
        codeStyleSettingsManager.dropTemporarySettings();
        // Prepare a new settings object, which will maintain the standard settings if no
        // editorconfig settings apply
        CodeStyleSettings currentSettings = codeStyleSettingsManager.getCurrentSettings();
        CodeStyleSettings newSettings = new CodeStyleSettings();
        newSettings.copyFrom(currentSettings);
        // Get editorconfig settings
        String filePath = file.getCanonicalPath();
        SettingsProviderComponent settingsProvider = SettingsProviderComponent.getInstance();
        List<OutPair> outPairs = settingsProvider.getOutPairs(filePath);
        // Apply editorconfig settings for the current editor
        // TODO: Find a good way to reliably get the language of the current editor
        Collection<Language> registeredLanguages = Language.getRegisteredLanguages();
        for (Language language: registeredLanguages) {
            CommonCodeStyleSettings commonSettings = newSettings.getCommonSettings(language);
            ConfigConverter.appplyCodeStyleSettings(outPairs, commonSettings);
        }
        codeStyleSettingsManager.setTemporarySettings(newSettings);
        LOG.debug("Applied settings for: " + filePath);
    }
}
