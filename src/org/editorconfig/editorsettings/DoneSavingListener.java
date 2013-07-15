package org.editorconfig.editorsettings;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface DoneSavingListener extends EventListener {
    void doneSavingAllDocuments();
    void doneSavingDocument(@NotNull Document document);
}
