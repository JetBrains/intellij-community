package org.jetbrains.idea.maven.core;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.WildcardFileNameMatcher;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.core.util.MavenEnv;

import java.util.Arrays;

/**
 * Author: Vladislav.Kaznacheev
 */
public class MavenApplicationComponent implements ApplicationComponent {

  private FileType fileType = new MavenFileType();

  @NonNls
  @NotNull
  public String getComponentName() {
    return "MavenApplicationComponent";
  }

  public void initComponent() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final FileNameMatcher matcher = new WildcardFileNameMatcher(MavenEnv.POM_FILE);
        FileTypeManagerEx.getInstance().registerFileType(fileType, Arrays.asList(matcher));
      }
    });
  }

  public void disposeComponent() {
  }
}

