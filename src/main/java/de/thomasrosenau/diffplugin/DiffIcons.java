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

package de.thomasrosenau.diffplugin;

import javax.swing.*;

import com.intellij.openapi.util.IconLoader;

public class DiffIcons {
    public static final Icon FILE = IconLoader.getIcon("/META-INF/pluginIcon.svg");
    public static final Icon MULTI_DIFF_PART = IconLoader.getIcon("/multiPartIcon.svg");
    public static final Icon HUNK = IconLoader.getIcon("/hunkIcon.svg");
    public static final Icon BINARY_PATCH = IconLoader.getIcon("/binaryPatchIcon.svg");
}
