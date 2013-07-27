package org.editorconfig.configmanagement;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.editorconfig.core.EditorConfig;
import org.editorconfig.utils.ConfigConverter;
import org.editorconfig.plugincomponents.DoneSavingListener;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

public class EndOfLineManager implements FileDocumentManagerListener, DoneSavingListener {
    private final Logger LOG = Logger.getInstance("#org.editorconfig.codestylesettings.EndOfLineManager");
    private final Project project;
    private final List<Document> documentsToChange = new ArrayList<Document>();


    private static final String CR = Character.toString((char) 13);
    private static final String LF = Character.toString((char) 10);
    private static final Map<String, String> endOfLineMap;
    static {
        Map<String, String> map = new HashMap<String, String>();
        map.put("lf", LF);
        map.put("cr", CR);
        map.put("crlf", CR + LF);
        endOfLineMap = Collections.unmodifiableMap(map);
    }


    public EndOfLineManager(Project project) {
        this.project = project;
    }

    public void initComponent() {
        // TODO: insert component initialization logic here
    }

    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @NotNull
    public String getComponentName() {
        return "EndOfLineManager";
    }

    public void projectOpened() {
        // Not used
    }

    public void projectClosed() {
        // Not used
    }

    @Override
    public void beforeAllDocumentsSaving() {
        // Not used
    }

    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        // Keep track of documents to change here, because we can't get a list
        // of changed documents at the end of a save-all operation
        documentsToChange.add(document);
    }

    @Override
    public void beforeFileContentReload(VirtualFile file, @NotNull Document document) {
        // Not used
    }

    @Override
    public void fileWithNoDocumentChanged(@NotNull VirtualFile file) {
        // Not used
    }

    @Override
    public void fileContentReloaded(VirtualFile file, @NotNull Document document) {
        // Not used
    }

    @Override
    public void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {
        // Not used
    }

    @Override
    public void unsavedDocumentsDropped() {
        // Not used
    }


    @Override
    public void doneSavingAllDocuments() {
        applySettings();
    }

    @Override
    public void doneSavingDocument(@NotNull Document document) {
        applySettings();
    }
    private void applySettings() {
        for (Document document:documentsToChange){
            final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
            if (file == null) {
                return;
            }
            String filePath = file.getCanonicalPath();
            final List<EditorConfig.OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(filePath);
            String endOfLine = ConfigConverter.valueForKey(outPairs, "end_of_line");
            if (!endOfLine.isEmpty()) {
                if (endOfLineMap.containsKey(endOfLine)) {
                    String lineSeparator = endOfLineMap.get(endOfLine);
                    changeSeparators(file, document, lineSeparator);
                } else {
                    LOG.error("Value of charset is invalid");
                }
            }
            LOG.debug("Applied end of line settings for: " + filePath);
        }
        documentsToChange.clear();
    }

    private void changeSeparators(VirtualFile file, Document document, String lineSeparator) {
        String text = document.getText();
        if (!lineSeparator.equals("\n")) {
            text = StringUtil.convertLineSeparators(text, lineSeparator);
        }
        final AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
        try {
            LoadTextUtil.write(project, file, this, text, document.getModificationStamp());
        } catch (IOException error) {
            LOG.error("IOException while changing line separator");
        }
        finally {
            token.finish();
        }
    }
}