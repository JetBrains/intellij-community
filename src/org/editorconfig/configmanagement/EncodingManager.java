package org.editorconfig.configmanagement;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.editorconfig.plugincomponents.SettingsProviderComponent;
import org.editorconfig.core.EditorConfig.OutPair;
import org.editorconfig.utils.ConfigConverter;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EncodingManager implements FileDocumentManagerListener {
    private final Logger LOG = Logger.getInstance("#org.editorconfig.codestylesettings.EncodingManager");
    private final Project project;

    private static final Map<String, Charset> encodingMap;
    static {
        Map<String, Charset> map = new HashMap<String, Charset>();
        map.put("latin1", Charset.forName("ISO-8859-1"));
        map.put("utf-8", Charset.forName("UTF-8"));
        map.put("utf-16be", Charset.forName("UTF-16BE"));
        map.put("utf-16le", Charset.forName("UTF-16LE"));
        encodingMap = Collections.unmodifiableMap(map);
    }

    public EncodingManager(Project project) {
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
        return "EncodingManager";
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
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        applySettings(file);
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

    private void applySettings(VirtualFile file) {
        String filePath = file.getCanonicalPath();
        List<OutPair> outPairs = SettingsProviderComponent.getInstance().getOutPairs(filePath);
        EncodingProjectManager encodingProjectManager = EncodingProjectManager.getInstance(project);
        String charset = ConfigConverter.valueForKey(outPairs, "charset");
        if (!charset.isEmpty()) {
            if (encodingMap.containsKey(charset)) {
                encodingProjectManager.setEncoding(file, encodingMap.get(charset));
            } else {
                LOG.error("Value of end_of_line is invalid");
            }
        }
        LOG.debug("Applied encoding settings for: " + filePath);
    }
}
