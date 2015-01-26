package com.jetbrains.edu.learning.course;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.edu.learning.StudyNames;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Course {
  private static final Logger LOG = Logger.getInstance(Course.class.getName());
  public static final String SANDBOX_DIR = "Sandbox";
  public List<Lesson> lessons = new ArrayList<Lesson>();
  private String description;
  private String name;
  private String myCourseDirectory = "";
  private String author="";
  private boolean myUpToDate;
  private String myLanguage;


  public List<Lesson> getLessons() {
    return lessons;
  }

  /**
   * Initializes state of course
   */
  public void init(boolean isRestarted) {
    for (Lesson lesson : lessons) {
      lesson.init(this, isRestarted);
    }
  }

  public String getAuthor() {
    return author;
  }

  /**
   * Creates course directory in project user created
   *
   * @param baseDir      project directory
   * @param resourceRoot directory where original course is stored
   */
  public void create(@NotNull final VirtualFile baseDir, @NotNull final File resourceRoot,
                     @NotNull final Project project) {
    ApplicationManager.getApplication().invokeLater(
      new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              try {
                for (int i = 0; i < lessons.size(); i++) {
                  Lesson lesson = lessons.get(i);
                  lesson.setIndex(i);
                  lesson.create(baseDir, resourceRoot, project);
                }
                baseDir.createChildDirectory(this, SANDBOX_DIR);
                File[] files = resourceRoot.listFiles(new FilenameFilter() {
                  @Override
                  public boolean accept(File dir, String name) {
                    return !name.contains(StudyNames.LESSON_DIR) && !name.equals("course.json") && !name.equals("hints");
                  }
                });
                for (File file : files) {
                  FileUtil.copy(file, new File(baseDir.getPath(), file.getName()));
                }
              }
              catch (IOException e) {
                LOG.error(e);
              }
            }
          });
        }
      });
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setCourseDirectory(@NotNull final String courseDirectory) {
    myCourseDirectory = courseDirectory;
  }

  public String getCourseDirectory() {
    return myCourseDirectory;
  }

  public String getDescription() {
    return description;
  }

  public boolean isUpToDate() {
    return myUpToDate;
  }

  public void setUpToDate(boolean upToDate) {
    myUpToDate = upToDate;
  }

  public Language getLanguage() {
    return Language.findLanguageByID(myLanguage);
  }

  public void setLanguage(@NotNull final String language) {
    myLanguage = language;
  }

  public void setAuthor(String author) {
    this.author = author;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
