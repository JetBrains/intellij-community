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
            new AttributesDescriptor("Meta info//git patch header", DiffSyntaxHighlighter.GIT_HEAD),
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
        return "From 4c763966942c5a7376c6cd299d2ef7e617a0957b Mon Sep 17 00:00:00 2001\n" +
                "From: John Doe <john.doe@example.com>\n" +
                "Date: Wed, 21 Mar 2018 10:49:11 +0000\n" +
                "Subject: [PATCH 28/35] Fixed broken links\n" +
                "\n" +
                "---\n" +
                " .gitignore                    |   3 +-\n" +
                " .travis.yml                   |   6 +-\n" +
                " commanderConfig.js            |  17 +++--\n" +
                " package.json                  |   2 +-\n" +
                " test/Cli.js                   | 139 ++++++++++++++++++++++++++++++++++\n" +
                " test/{index.js => Factory.js} |   4 +-\n" +
                " test/ProvidersCLI.js          |  41 ++++++++++\n" +
                " test/helpers.js               |  31 ++++++++\n" +
                " test/mocha.opts               |   1 +\n" +
                " 9 files changed, 231 insertions(+), 13 deletions(-)\n" +
                " create mode 100644 test/Cli.js\n" +
                " rename test/{index.js => Factory.js} (94%)\n" +
                " create mode 100644 test/ProvidersCLI.js\n" +
                " create mode 100644 test/helpers.js\n" +
                " create mode 100644 test/mocha.opts\n" +
                "\n" +
                "diff --git a/test/index.js b/test/Factory.js\n" +
                "similarity index 94%\n" +
                "rename from test/index.js\n" +
                "rename to test/Factory.js\n" +
                "index e77e4b8..6a3c7f5 100644\n" +
                "--- a/test/index.js\n" +
                "+++ b/test/Factory.js\n" +
                "@@ -1,6 +1,6 @@\n" +
                " var chai = require('chai');\n" +
                " var expect = require('chai').expect;\n" +
                "-var Image = require('../image/Image.js');\n" +
                "+var Image = require('../image/Image');\n" +
                " \n" +
                " describe('Image Factory', function () {\n" +
                "   'use strict';\n" +
                "@@ -61,7 +61,7 @@ describe('Image Providers', function () {\n" +
                "     Image.setProvider('UnsplashIt');\n" +
                " \n" +
                "     expect(Image.getImageUrl(size))\n" +
                "-        .to.equal('https://unsplash.it/400/400');\n" +
                "+      .to.equal('https://unsplash.it/400/400');\n" +
                "   });\n" +
                " \n" +
                "   it('returns FakeImg URL', function () {\n" +
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
