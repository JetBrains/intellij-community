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

class DiffColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[] {
            new AttributesDescriptor("Changed lines//Inserted line", DiffSyntaxHighlighter.INSERTED),
            new AttributesDescriptor("Changed lines//Deleted line", DiffSyntaxHighlighter.DELETED),
            new AttributesDescriptor("Changed lines//Changed line", DiffSyntaxHighlighter.CHANGED),
            new AttributesDescriptor("Meta info//Console command", DiffSyntaxHighlighter.COMMAND),
            new AttributesDescriptor("Meta info//File names", DiffSyntaxHighlighter.FILE),
            new AttributesDescriptor("Meta info//Newline hint", DiffSyntaxHighlighter.EOL_HINT),
            new AttributesDescriptor("Hunk header", DiffSyntaxHighlighter.HUNK_HEAD),
            new AttributesDescriptor("Separator", DiffSyntaxHighlighter.SEPARATOR),
            new AttributesDescriptor("Text (default)", DiffSyntaxHighlighter.TEXT)
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
        // TODO: provide better example for multiple formats
        return "Only in lao: preamble\n" + "diff -c lao/quote tzu/quote\n" +
                "*** lao/quote\t2019-02-18 08:26:38.000000000 +0100\n" +
                "--- tzu/quote\t2019-02-18 08:23:58.000000000 +0100\n" + "***************\n" + "*** 1,7 ****\n" +
                "- The Way that can be told of is not the eternal Way;\n" +
                "- The name that can be named is not the eternal name.\n" +
                "  The Nameless is the origin of Heaven and Earth;\n" + "! The Named is the mother of all things.\n" +
                "  Therefore let there always be non-being,\n" + "    so we may see their subtlety,\n" +
                "  And let there always be being,\n" + "--- 1,6 ----\n" +
                "  The Nameless is the origin of Heaven and Earth;\n" + "! The named is the mother of all things.\n" +
                "!\n" + "  Therefore let there always be non-being,\n" + "    so we may see their subtlety,\n" +
                "  And let there always be being,\n" + "***************\n" + "*** 9,11 ****\n" + "--- 8,13 ----\n" +
                "  The two are the same,\n" + "  But after they are produced,\n" + "    they have different names.\n" +
                "+ They both may be called deep and profound.\n" + "+ Deeper and more profound,\n" +
                "+ The door of all subtleties!\n" +
                "Only in tzu: unquote\n" +
                "\\ No newline at end of file\n";
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
