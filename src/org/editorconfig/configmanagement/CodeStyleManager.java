package org.editorconfig.configmanagement;

import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.editorconfig.utils.ConfigConverter;
import org.jetbrains.annotations.NotNull;

import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.Collection;
import java.util.List;

public class CodeStyleManager implements FileEditorManagerListener, WindowFocusListener {
    // Handles the following EditorConfig settings:
    private static final String indentSizeKey = "indent_size";
    private static final String tabWidthKey = "tab_width";
    private static final String indentStyleKey = "indent_style";

    private static final Logger LOG = 
            Logger.getInstance("#org.editorconfig.configmanagement.CodeStyleManager");
    private final CodeStyleSettingsManager codeStyleSettingsManager;
    private final Project project;

    public CodeStyleManager(Project project) {
        codeStyleSettingsManager = CodeStyleSettingsManager.getInstance(project);
        this.project = project;
    }

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        applySettings(file);
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        // Not used
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        VirtualFile file = event.getNewFile();
        applySettings(file);
    }

    @Override
    public void windowGainedFocus(WindowEvent e) {
        Editor currentEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        if (currentEditor != null) {
            Document currentDocument = currentEditor.getDocument();
            VirtualFile currentFile = FileDocumentManager.getInstance().getFile(currentDocument);
            applySettings(currentFile);
        }
    }

    @Override
    public void windowLostFocus(WindowEvent e) {
        // Not used
    }
    
    private void applySettings(VirtualFile file) {
        if (file != null) {
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
            applyCodeStyleSettings(outPairs, newSettings, filePath);
            codeStyleSettingsManager.setTemporarySettings(newSettings);
            LOG.debug("Applied code style settings for: " + filePath);
            EditorEx currentEditor = (EditorEx) FileEditorManager.getInstance(project).getSelectedTextEditor();
            if (currentEditor != null){
                currentEditor.reinitSettings();
            }
        }
    }

    private void applyCodeStyleSettings(List<OutPair> outPairs,
                                              CodeStyleSettings codeStyleSettings, String filePath) {
        // Apply indent options
        // TODO: Find a good way to reliably get the language of the current editor
        Collection<Language> registeredLanguages = Language.getRegisteredLanguages();
        for (Language language: registeredLanguages) {
            CommonCodeStyleSettings commonSettings = codeStyleSettings.getCommonSettings(language);
            CommonCodeStyleSettings.IndentOptions indentOptions = commonSettings.getIndentOptions();
            applyIndentOptions(outPairs, indentOptions, filePath);
        }
    }

    private void applyIndentOptions (List<OutPair> outPairs, CommonCodeStyleSettings.IndentOptions indentOptions,
                                     String filePath) {
        String indentSize = ConfigConverter.valueForKey(outPairs, indentSizeKey);
        String tabWidth = ConfigConverter.valueForKey(outPairs, tabWidthKey);
        String indentStyle = ConfigConverter.valueForKey(outPairs, indentStyleKey);
        try {
            applyIndentSize(indentOptions, indentSize, filePath);
        }
        catch(InvalidConfigException e) {
            LOG.warn(e.getMessage());
        }
        // Set indent_size to tab_width if indent_size == "tab"
        if (indentSize.equals("tab")) {
            indentSize = tabWidth;
        }
        // Apply tab_width if set, or fall back to indent_size
        if (tabWidth.isEmpty()) {
            tabWidth = indentSize;
        }
        try {
            applyTabWidth(indentOptions, tabWidth, filePath);
        }
        catch(InvalidConfigException e) {
            LOG.warn(e.getMessage());
        }
        try {
            applyIndentStyle(indentOptions, indentStyle, filePath);
        }
        catch(InvalidConfigException e) {
            LOG.warn(e.getMessage());
        }
    }

    private void applyIndentSize(CommonCodeStyleSettings.IndentOptions indentOptions, String indentSize,
                                 String filePath) throws InvalidConfigException {
        if (!indentSize.isEmpty()) {
            try {
                indentOptions.INDENT_SIZE = Integer.parseInt(indentSize);
            } catch (NumberFormatException e){
                throw new InvalidConfigException(indentSizeKey, indentSize, filePath);
            }
        }
    }

    private void applyTabWidth(CommonCodeStyleSettings.IndentOptions indentOptions, String tabWidth,
                               String filePath) throws InvalidConfigException {
        if (!tabWidth.isEmpty()) {
            try {
                indentOptions.TAB_SIZE = Integer.parseInt(tabWidth);
            } catch (Exception error) {
                throw new InvalidConfigException(tabWidthKey, tabWidth, filePath);
            }
        }
    }

    private void applyIndentStyle(CommonCodeStyleSettings.IndentOptions indentOptions, String indentStyle,
                                  String filePath) throws InvalidConfigException {
        if (!indentStyle.isEmpty()) {
            if (indentStyle.equals("tab") || indentStyle.equals("space")) {
                indentOptions.USE_TAB_CHARACTER = indentStyle.equals("tab");
            } else {
                throw new InvalidConfigException(indentStyleKey, indentStyle, filePath);
            }
        }
    }
}
