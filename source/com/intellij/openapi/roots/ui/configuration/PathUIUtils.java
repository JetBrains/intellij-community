package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.util.JavaUtilForVfs;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.DetectedSourceRootsDialog;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * This utility class contains utility methods for selecting paths.
 *
 * @author Constantine.Plotnikov
 */
public class PathUIUtils {
  /** a private constructor */
  private PathUIUtils() {}

  /**
   * This method takes a candidates for the project root, then scans the candidates and
   * if multiple candidates or non root source directories are found whithin some
   * directories, it shows a dialog that allows selecting or deselecting them.
   * @param component a parent component
   * @param rootCandidates a candidates for roots
   * @return a array of source folders or empty array if non was selected or dialog was canceled.
   */
  public static VirtualFile[] scandAndSelectDetectedJavaSourceRoots(Component component, final VirtualFile[] rootCandidates) {
    final Set<VirtualFile> result = new HashSet<VirtualFile>();
    final Map<VirtualFile, List<VirtualFile>> detectedRootsMap = new LinkedHashMap<VirtualFile, List<VirtualFile>>();
    // scan for roots
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        for (final VirtualFile candidate : rootCandidates) {
          List<VirtualFile> detectedRoots = JavaUtilForVfs.suggestRoots(candidate);
          if (!detectedRoots.isEmpty() && (detectedRoots.size() > 1 || detectedRoots.get(0) != candidate)) {
            detectedRootsMap.put(candidate, detectedRoots);
          } else {
            result.add(candidate);
          }
        }
      }
    }, "Scanning for source roots", true, null);
    if(!detectedRootsMap.isEmpty()) {
      DetectedSourceRootsDialog dlg = new DetectedSourceRootsDialog(component, detectedRootsMap);
      dlg.show();
      if (dlg.isOK()) {
        result.addAll(dlg.getChosenRoots());
      }
      else {
        // the empty result means that the entire root adding process will be cancelled.
        result.clear();
      }
    }
    return result.toArray(new VirtualFile[result.size()]);
  }
}
