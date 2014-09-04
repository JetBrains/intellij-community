/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath.xslt.run;

import com.intellij.diagnostic.logging.AdditionalTabComponent;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.EditorHighlighterProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class HighlightingOutputConsole extends AdditionalTabComponent implements DataProvider {
    public static final String TAB_TITLE = "XSLT Output";

    private final ConsoleView myConsole;
    private final JComponent myConsoleComponent;

    public HighlightingOutputConsole(Project project, @Nullable FileType fileType) {
        super(new BorderLayout());

        myConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();

        myConsoleComponent = myConsole.getComponent();
        add(myConsoleComponent, BorderLayout.CENTER);

        final EditorEx editorEx = getEditor();
        assert editorEx != null;

        if (fileType != null) {
          EditorHighlighterProvider provider = FileTypeEditorHighlighterProviders.INSTANCE.forFileType(fileType);
          final EditorHighlighter highlighter = provider.getEditorHighlighter(project, fileType, null, editorEx.getColorsScheme());
          editorEx.setHighlighter(highlighter);
        }
    }

    @Nullable
    public JComponent getSearchComponent() {
        return null;
    }

    @Nullable
    public ActionGroup getToolbarActions() {
        return null;
    }

    @Nullable
    public JComponent getToolbarContextComponent() {
        return null;
    }

    @Nullable
    public String getToolbarPlace() {
        return null;
    }

    public boolean isContentBuiltIn() {
        return true;
    }

    @Nullable
    private EditorEx getEditor() {
        return (EditorEx)((DataProvider)myConsole).getData(LangDataKeys.EDITOR.getName());
    }

    public JComponent getPreferredFocusableComponent() {
        return myConsoleComponent;
    }

    @Nullable
    public Object getData(@NonNls String dataId) {
        if (LangDataKeys.EDITOR.is(dataId)) {
            return getEditor();
        }
        return null;
    }

    void selectOutputTab() {
        final Container parent = getParent();
        if (parent instanceof JTabbedPane) {
            // run
            ((JTabbedPane)parent).setSelectedComponent(this);
        }
    }

    @NotNull
    public String getTabTitle() {
        return TAB_TITLE;
    }

    public void dispose() {
      Disposer.dispose(myConsole);
    }

    public ConsoleView getConsole() {
        return myConsole;
    }
}
