/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 20, 2002
 * Time: 9:50:29 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.export;

import com.intellij.codeInspection.ex.HTMLComposer;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class HTMLExporter {
  private final String myRootFolder;
  private Project myProject;
  private int myFileCounter;
  private String myCurrentFileName;
  private final com.intellij.util.containers.HashMap myElementToFilenameMap;
  private final HTMLComposer myComposer;
  private final HashSet myGeneratedReferences;
  private final HashSet myGeneratedPages;

  public HTMLExporter(String rootFolder, HTMLComposer composer, Project project) {
    myRootFolder = rootFolder;
    myProject = project;
    myElementToFilenameMap = new com.intellij.util.containers.HashMap();
    myFileCounter = 0;
    myComposer = composer;
    myGeneratedPages = new HashSet();
    myGeneratedReferences = new HashSet();
  }

  public void createPage(RefElement element) {
    myCurrentFileName = fileNameForElement(element);
    StringBuffer buf = new StringBuffer();
    appendNavBar(buf, element);
    myComposer.composeWithExporter(buf, element, this);
    writeFile(myRootFolder, myCurrentFileName, buf, myProject);
    myGeneratedPages.add(element);
  }

  private void appendNavBar(final StringBuffer buf, RefElement element) {
    buf.append("<a href=\"../index.html\" target=\"_top\">Inspections</a>  ");
    myComposer.appendElementReference(buf, element, "Open source", "_blank");
    buf.append("<hr>");
  }

  public static void writeFile(String folder, String fileName, StringBuffer buf, Project project) {
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    String fullPath = folder + File.separator + fileName;

    if (indicator != null) {
      ProgressManager.getInstance().checkCanceled();
      indicator.setText("Generating HTML:" + fullPath);
    }

    try {
      File myFolder = new File(folder);
      myFolder.mkdirs();
      FileWriter myWriter = new FileWriter(fullPath);
      myWriter.write(buf.toString().toCharArray());
      myWriter.close();
    } catch (IOException e) {
      Messages.showMessageDialog(
        project,
        "Error writing to " + fullPath,
        "Inspection Results Export",
        Messages.getErrorIcon()
      );
      throw new ProcessCanceledException();
    }
  }

  public String getURL(RefElement element) {
    myGeneratedReferences.add(element);
    return fileNameForElement(element);
  }

  private String fileNameForElement(RefElement element) {
    String fileName = (String) myElementToFilenameMap.get(element);

    if (fileName == null) {
      fileName = "e" + Integer.toString(++myFileCounter) + ".html";
      myElementToFilenameMap.put(element, fileName);
    }

    return fileName;
  }

  private Set getReferencesWithoutPages() {
    HashSet result = new HashSet();
    for (Iterator iterator = myGeneratedReferences.iterator(); iterator.hasNext();) {
      RefElement refElement = (RefElement) iterator.next();
      if (!myGeneratedPages.contains(refElement)) {
        result.add(refElement);
      }
    }

    return result;
  }

  public void generateReferencedPages() {
    Set extras = getReferencesWithoutPages();
    while (extras.size() > 0) {
      for (Iterator iterator = extras.iterator(); iterator.hasNext();) {
        RefElement refElement = (RefElement) iterator.next();
        createPage(refElement);
      }
      extras = getReferencesWithoutPages();
    }
  }

  public String getRootFolder() {
    return myRootFolder;
  }
}
