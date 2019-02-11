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

package de.thomasrosenau.diffplugin.highlighter;

import java.util.Map;
import javax.swing.*;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import de.thomasrosenau.diffplugin.DiffIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiffColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[] {
            new AttributesDescriptor("Changed lines//Inserted line", DiffSyntaxHighlighter.ADDED),
            new AttributesDescriptor("Changed lines//Deleted line", DiffSyntaxHighlighter.DELETED),
            new AttributesDescriptor("Changed lines//Changed line", DiffSyntaxHighlighter.MODIFIED),
            new AttributesDescriptor("Meta info//Console command", DiffSyntaxHighlighter.COMMAND),
            new AttributesDescriptor("Meta info//File names", DiffSyntaxHighlighter.FILE),
            new AttributesDescriptor("Meta info//Newline hint", DiffSyntaxHighlighter.EOLHINT),
            new AttributesDescriptor("Hunk header", DiffSyntaxHighlighter.HUNK_HEAD),
            new AttributesDescriptor("Separator", DiffSyntaxHighlighter.SEPARATOR),
            new AttributesDescriptor("Text", DiffSyntaxHighlighter.TEXT)
    };

    @Nullable
    @Override
    public Icon getIcon() {
        return DiffIcons.FILE;
    }

    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return new DiffSyntaxHighlighter();
    }

    @NotNull
    @Override
    public String getDemoText() {
        return "#contextual diff\n" +
                "diff -c y/a z/a\n" +
                "*** y/a\t2019-02-10 05:29:03.000000000 +0100\n" +
                "--- z/a\t2019-02-10 05:28:44.000000000 +0100\n" +
                "***************\n" +
                "*** 1,5 ****\n" +
                "  foo\n" +
                "! bar\n" +
                "  baz\n" +
                "  world\n" +
                "- end\n" +
                "--- 1,5 ----\n" +
                "  foo\n" +
                "! qux\n" +
                "  baz\n" +
                "+ hello\n" +
                "  world\n" +
                "\n" +
                "\n" +
                "#Git patch\n" +
                "-- \n" +
                "diff --git a/image/providers/UnsplashIt.js b/image/providers/LoremPicsum.js\n" +
                "similarity index 57%\n" +
                "rename from image/providers/UnsplashIt.js\n" +
                "rename to image/providers/LoremPicsum.js\n" +
                "index f549aae..0393bca 100644\n" +
                "--- a/image/providers/UnsplashIt.js\n" +
                "+++ b/image/providers/LoremPicsum.js\n" +
                "@@ -4,6 +4,6 @@ module.exports = {\n" +
                " \n" +
                "     var pieces = size.split('x');\n" +
                " \n" +
                "-    return 'https://unsplash.it/' + pieces[0] + '/' + pieces[1];\n" +
                "+    return 'https://picsum.photos/' + pieces[0] + '/' + pieces[1] + '/?random';\n" +
                "   }\n" +
                " };\n" +
                "\\ No newline at end of file\n" +
                "-- \n" +
                "2.20.1\n";
    }

    @Nullable
    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }

    @NotNull
    @Override
    public AttributesDescriptor[] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @NotNull
    @Override
    public ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return ".diff & .patch Files";
    }
}
