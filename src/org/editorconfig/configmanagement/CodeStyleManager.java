package org.editorconfig.configmanagement;

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
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.utils.ConfigConverter;

import java.util.Collection;
import java.util.List;

public class CodeStyleManager implements FileEditorManagerListener {
    private static final Logger LOG = 
            Logger.getInstance("#org.editorconfig.configmanagement.CodeStyleManager");
    private final CodeStyleSettingsManager codeStyleSettingsManager;

    public CodeStyleManager(Project project) {
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
        applyCodeStyleSettings(outPairs, newSettings);
        codeStyleSettingsManager.setTemporarySettings(newSettings);
        LOG.debug("Applied code style settings for: " + filePath);
    }

    private void applyCodeStyleSettings(List<OutPair> outPairs,
                                              CodeStyleSettings codeStyleSettings) {
        // Apply indent options
        // TODO: Find a good way to reliably get the language of the current editor
        Collection<Language> registeredLanguages = Language.getRegisteredLanguages();
        for (Language language: registeredLanguages) {
            CommonCodeStyleSettings commonSettings = codeStyleSettings.getCommonSettings(language);
            CommonCodeStyleSettings.IndentOptions indentOptions = commonSettings.getIndentOptions();
            applyIndentOptions(outPairs, indentOptions);
        }
    }

    private void applyIndentOptions (List<OutPair> outPairs, CommonCodeStyleSettings.IndentOptions indentOptions) {
        String indentSize = ConfigConverter.valueForKey(outPairs, "indent_size");
        String tabWidth = ConfigConverter.valueForKey(outPairs, "tab_width");
        String indentStyle = ConfigConverter.valueForKey(outPairs, "indent_style");

        if (!indentSize.isEmpty()) {
            try {
                indentOptions.INDENT_SIZE = Integer.parseInt(indentSize);
            } catch (Exception error){
                LOG.error("Unable to parse indent_size");
            }
        }
        if (!tabWidth.isEmpty()) {
            try {
                indentOptions.TAB_SIZE = Integer.parseInt(tabWidth);
            } catch (Exception error) {
                LOG.error("Unable to parse tab_size");
            }
        }
        if (!indentStyle.isEmpty()) {
            if (indentStyle.equals("tab") || indentStyle.equals("space")) {
                indentOptions.USE_TAB_CHARACTER = indentStyle.equals("tab");
            } else {
                LOG.error("Value of use_tab_character is invalid");
            }
        }
    }
}
