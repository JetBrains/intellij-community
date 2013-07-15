package org.editorconfig.utils;

import java.util.List;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import org.editorconfig.core.EditorConfig.OutPair;

public class ConfigConverter {
    public static void appplyCodeStyleSettings(List<OutPair> outPairs,
                                               CommonCodeStyleSettings commonCodeStyleSettings) {
        applyIndentOptions(outPairs, commonCodeStyleSettings.getIndentOptions());    
    }
    
    private static void applyIndentOptions (List<OutPair> outPairs, IndentOptions indentOptions) {
        String indentSize = valueForKey(outPairs, "indent_size");
        String tabWidth = valueForKey(outPairs, "tab_width");
        String indentStyle = valueForKey(outPairs, "indent_style");
        
        if (!indentSize.isEmpty()) {
            try {
                indentOptions.INDENT_SIZE = Integer.parseInt(indentSize);
            } catch (Exception error){
                logError("Unable to parse indent_size");
            }
        }
        if (!tabWidth.isEmpty()) {
            try {
                indentOptions.TAB_SIZE = Integer.parseInt(tabWidth);
            } catch (Exception error) {
                logError("Unable to parse tab_size");
            }
        }
        if (!indentStyle.isEmpty()) {
            if (indentStyle.equals("tab") || indentStyle.equals("space")) {
                indentOptions.USE_TAB_CHARACTER = indentStyle.equals("tab");
            } else {
                logError("Value of use_tab_character is invalid");
            }
        }
    }

    public static void applyEditorSettings(List<OutPair> outPairs) {
        EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
        String trimTrailingWhitespace = valueForKey(outPairs, "trim_trailing_whitespace");
        String insertFinalNewline = valueForKey(outPairs, "insert_final_newline");
        if (!trimTrailingWhitespace.isEmpty()) {
            if (trimTrailingWhitespace.equals("true")) {
                editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
            } else if (trimTrailingWhitespace.equals("false")) {
                editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE);
            } else {
                logError("Value of trim_trailing_whitespace is invalid");
            }
        }
        if (!insertFinalNewline.isEmpty()) {
            if (insertFinalNewline.equals("true") || insertFinalNewline.equals("false")) {
                editorSettings.setEnsureNewLineAtEOF(insertFinalNewline.equals("true"));
            }
        } else {
            logError("Value of trim_trailing_whitespace is invalid");
        }
    }

    private static String valueForKey(List<OutPair> outPairs, String key) {
        for (OutPair outPair: outPairs) {
            if (outPair.getKey().equals(key)) {
                return outPair.getVal();
            }
        }
        return "";
    }

    private static void logError(String string) {
        Logger LOG = Logger.getInstance("#org.editorconfig.utils.ConfigConverter");
        LOG.error(string);
    }
}
