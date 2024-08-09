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
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.fileTypes.EditorHighlighterProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeEditorHighlighterProviders;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.intellij.plugins.xpathView.XPathBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class HighlightingOutputConsole extends AdditionalTabComponent implements UiDataProvider {

    private final ConsoleView myConsole;

    public HighlightingOutputConsole(Project project, @Nullable FileType fileType) {
        super(new BorderLayout());

        myConsole = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();

        add(myConsole.getComponent(), BorderLayout.CENTER);

        final EditorEx editorEx = getEditor();
        assert editorEx != null;

        if (fileType != null) {
          EditorHighlighterProvider provider = FileTypeEditorHighlighterProviders.getInstance().forFileType(fileType);
          final EditorHighlighter highlighter = provider.getEditorHighlighter(project, fileType, null, editorEx.getColorsScheme());
          editorEx.setHighlighter(highlighter);
        }
    }

    @Override
    @Nullable
    public JComponent getSearchComponent() {
        return null;
    }

    @Override
    @Nullable
    public ActionGroup getToolbarActions() {
        return null;
    }

    @Override
    @Nullable
    public JComponent getToolbarContextComponent() {
        return null;
    }

    @Override
    @Nullable
    public String getToolbarPlace() {
        return null;
    }

    @Override
    public boolean isContentBuiltIn() {
        return true;
    }

    @Nullable
    private EditorEx getEditor() {
      DataContext dataContext = DataManager.getInstance().getDataContext(myConsole.getComponent());
      return (EditorEx)CommonDataKeys.EDITOR.getData(dataContext);
    }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myConsole.getComponent();
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(CommonDataKeys.EDITOR, getEditor());
  }

    void selectOutputTab() {
        final Container parent = getParent();
        if (parent instanceof JTabbedPane) {
            // run
            ((JTabbedPane)parent).setSelectedComponent(this);
        }
    }

    @Override
    @NotNull
    public String getTabTitle() {
        return XPathBundle.message("tab.title.xslt.output");
    }

    @Override
    public void dispose() {
      Disposer.dispose(myConsole);
    }

    public ConsoleView getConsole() {
        return myConsole;
    }
}
