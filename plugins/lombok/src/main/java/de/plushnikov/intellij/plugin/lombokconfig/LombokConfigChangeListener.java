package de.plushnikov.intellij.plugin.lombokconfig;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import de.plushnikov.intellij.plugin.language.LombokConfigFileType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class LombokConfigChangeListener implements BulkFileListener {
  private static final AtomicLong CONFIG_CHANGE_COUNTER = new AtomicLong(1);
  public static final ModificationTracker CONFIG_CHANGE_TRACKER = CONFIG_CHANGE_COUNTER::get;

  @Override
  public void before(@NotNull List<? extends @NotNull VFileEvent> events) {
    for (VFileEvent event : events) {
      VirtualFile eventFile = event.getFile();
      if (null != eventFile) {
        final CharSequence nameSequence = eventFile.getNameSequence();
        if (Strings.endsWith(nameSequence, "lombok.config")) {
          final FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(nameSequence);

          if (LombokConfigFileType.INSTANCE.equals(fileType)) {
            CONFIG_CHANGE_COUNTER.incrementAndGet();
            break;
          }
        }
      }
    }
  }
}
