package com.jetbrains.typoscript;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.jetbrains.typoscript.lang.TypoScriptFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

/**
 * @author lene
 *         Date: 24.04.12
 */
public class TypoScriptExtensionMappingChecker implements ApplicationComponent {
  private final FileTypeManager myFileTypeManager;

  public TypoScriptExtensionMappingChecker(final FileTypeManager fileTypeManager) {
    myFileTypeManager = fileTypeManager;
  }

  @NotNull
  public String getComponentName() {
    return getClass().getName();
  }

  public void initComponent() {
    checkFileTypeAssociation();
  }

  public void disposeComponent() { }

  private void checkFileTypeAssociation() {
    final FileType registered = myFileTypeManager.getFileTypeByExtension(TypoScriptFileType.DEFAULT_EXTENSION);
    if (registered != TypoScriptFileType.INSTANCE) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Notifications.Bus.notify(
            new Notification("TypoScript", TypoScriptBundle.message("plugin.title"),
                             TypoScriptBundle.message("file.type.registration.warning", TypoScriptFileType.DEFAULT_EXTENSION),
                             NotificationType.WARNING, new NotificationListener() {
                public void hyperlinkUpdate(@NotNull final Notification notification, @NotNull final HyperlinkEvent event) {
                  fixAssociation();
                  notification.expire();
                }
              }));
        }
      });
    }
  }

  private void fixAssociation() {
    final FileType registered = myFileTypeManager.getFileTypeByExtension(TypoScriptFileType.DEFAULT_EXTENSION);
    if (registered != TypoScriptFileType.INSTANCE) {
      final AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
      try {
        doFixAssociation(registered);
      }
      finally {
        token.finish();
      }
    }
  }

  @SuppressWarnings("deprecation")
  private void doFixAssociation(FileType registered) {
    myFileTypeManager.removeAssociatedExtension(registered, TypoScriptFileType.DEFAULT_EXTENSION);
    myFileTypeManager.registerFileType(TypoScriptFileType.INSTANCE, TypoScriptFileType.DEFAULT_EXTENSION);
  }
}