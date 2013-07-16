package org.editorconfig.utils;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions;
import org.editorconfig.core.EditorConfig.OutPair;

public class ConfigConverter {
    private static final Map<String, Charset> encodingMap;
    static {
        Map<String, Charset> map = new HashMap<String, Charset>();
        map.put("latin1", Charset.forName("ISO-8859-1"));
        map.put("utf-8", Charset.forName("UTF-8"));
        map.put("utf-16be", Charset.forName("UTF-16BE"));
        map.put("utf-16le", Charset.forName("UTF-16LE"));
        encodingMap = Collections.unmodifiableMap(map);
    }

    public static void applyCodeStyleSettings(List<OutPair> outPairs,
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

    public static void applyEncodingSettings(List<OutPair> outPairs, EncodingProjectManager encodingProjectManager,
                                             VirtualFile file) {
        String charset = valueForKey(outPairs, "charset");
        if (!charset.isEmpty()) {
            if (encodingMap.containsKey(charset)) {
                encodingProjectManager.setEncoding(file, encodingMap.get(charset));
            } else {
                logError("Value of charset is invalid");
            }
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
