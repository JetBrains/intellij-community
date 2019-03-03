/*
 Copyright 2019 Thomas Rosenau

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package de.thomasrosenau.diffplugin.psi;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;

public abstract class DiffGitHeaderBase extends DiffNavigationItem {
    public DiffGitHeaderBase(@NotNull ASTNode node) {
        super(node);
    }

    public String getPlaceholderText() {
        try {
            String mailHeader = this.getText().replaceAll("\\r", "").split("\\n---\\n")[0];
            Matcher subjectMatcher = Pattern.compile("^Subject: .*(\\n .*)*", Pattern.MULTILINE).matcher(mailHeader);
            if (subjectMatcher.find()) {
                String subjectLine = subjectMatcher.group().replaceFirst("^Subject: (\\[.*?] )?", "");
                if (subjectLine.startsWith("=?")) {
                    subjectLine = decode(subjectLine);
                }
                return shorten(subjectLine);
            }
            return shorten(mailHeader);
        } catch (RuntimeException | UnsupportedEncodingException e) {
            return shorten(this.getText());
        }
    }

    private String decode(String subjectLine) throws UnsupportedEncodingException {
        String result = subjectLine.replaceAll("\\?=\n =\\?(UTF|utf)-?8\\?q\\?", "")
                .replaceAll("^=\\?(UTF|utf)-?8\\?q\\?", "").replaceFirst("\\?=\\n?$", "");
        result = URLDecoder.decode(result.replaceAll("=", "%"), StandardCharsets.UTF_8.name());
        return result;
    }

    private String shorten(@NotNull String longString) {
        if (longString.length() > 80) {
            return longString.substring(0, 75) + "...";
        }
        return longString;
    }
}
