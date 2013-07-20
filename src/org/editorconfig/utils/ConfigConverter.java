package org.editorconfig.utils;

import java.util.List;

import org.editorconfig.core.EditorConfig.OutPair;

public class ConfigConverter {
    public static String valueForKey(List<OutPair> outPairs, String key) {
        for (OutPair outPair: outPairs) {
            if (outPair.getKey().equals(key)) {
                return outPair.getVal();
            }
        }
        return "";
    }
}
