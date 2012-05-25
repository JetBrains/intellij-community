/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.jetbrains.typoscript;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.*;
import com.intellij.util.containers.hash.HashMap;
import com.jetbrains.typoscript.lang.TypoScriptFileType;
import com.jetbrains.typoscript.lang.TypoScriptFileTypeFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;


public class TypoScriptExtensionMappingChecker implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance(TypoScriptExtensionMappingChecker.class);
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

  public void disposeComponent() {
  }

  private void checkFileTypeAssociation() {
    final Map<FileNameMatcher, FileType> fileNames = getNonTSMatchers(TypoScriptFileTypeFactory.FILE_NAME_MATCHERS);
    if (!fileNames.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      boolean addComma = false;
      for (Map.Entry<FileNameMatcher, FileType> entry : fileNames.entrySet()) {
        if (addComma) {
          sb.append(", ");
        }
        sb.append(entry.getKey().getPresentableString());
        addComma |= true;
      }
      final String fileMatchers = sb.toString();

      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Notifications.Bus.notify(
            new Notification(TypoScriptBundle.NOTIFICATION_ID, TypoScriptBundle.message("plugin.title"),
                             TypoScriptBundle.message("file.type.registration.warning", fileMatchers),
                             NotificationType.WARNING, new NotificationListener() {
              public void hyperlinkUpdate(@NotNull final Notification notification, @NotNull final HyperlinkEvent event) {
                fixAssociation(fileNames.keySet());
                notification.expire();
              }
            }), null);
        }
      });
    }
  }

  @NotNull
  private Map<FileNameMatcher, FileType> getNonTSMatchers(@NotNull Collection<? extends FileNameMatcher> matchers) {
    final Map<FileNameMatcher, FileType> fileNames = new HashMap<FileNameMatcher, FileType>();
    for (FileNameMatcher matcher : matchers) {
      FileType registered;
      if (matcher instanceof ExtensionFileNameMatcher) {
        registered = myFileTypeManager.getFileTypeByExtension(((ExtensionFileNameMatcher)matcher).getExtension());
      }
      else if (matcher instanceof ExactFileNameMatcher) {
        registered = myFileTypeManager.getFileTypeByFileName(((ExactFileNameMatcher)matcher).getFileName());
      }
      else {
        LOG.error("Unexpected FileNameMatcher class " + matcher.getClass() + " for " + matcher.getPresentableString());
        break;
      }
      if (registered != TypoScriptFileType.INSTANCE) {
        fileNames.put(matcher, registered);
      }
    }
    return fileNames;
  }

  private void fixAssociation(@NotNull Collection<FileNameMatcher> fileNameMatchers) {
    final Map<FileNameMatcher, FileType> refreshedFileNames = getNonTSMatchers(fileNameMatchers);
    if (!refreshedFileNames.isEmpty()) {
      final AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(getClass());
      try {
        doFixAssociation(refreshedFileNames);
      }
      finally {
        token.finish();
      }
    }
  }

  @SuppressWarnings("deprecation")
  private void doFixAssociation(@NotNull Map<FileNameMatcher, FileType> fileNames) {
    for (Map.Entry<FileNameMatcher, FileType> entry : fileNames.entrySet()) {
      myFileTypeManager.removeAssociation(entry.getValue(), entry.getKey());
    }
    myFileTypeManager.registerFileType(TypoScriptFileType.INSTANCE, new ArrayList<FileNameMatcher>(fileNames.keySet()));
  }
}