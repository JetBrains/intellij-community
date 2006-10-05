package com.intellij.find;

import com.intellij.openapi.application.ApplicationManager;

import java.util.ArrayList;

public abstract class FindSettings{

  public static FindSettings getInstance() {
    return ApplicationManager.getApplication().getComponent(FindSettings.class);
  }

  public abstract boolean isSkipResultsWithOneUsage();

  public abstract void setSkipResultsWithOneUsage(boolean skip);

  public abstract boolean isSearchForTextOccurences();

  public abstract void setSearchForTextOccurences(boolean search);

  public abstract String getDefaultScopeName();

  public abstract void setDefaultScopeName(String scope);

  public abstract boolean isSearchOverloadedMethods();

  public abstract void setSearchOverloadedMethods (boolean search);

  public abstract boolean isForward();

  public abstract void setForward(boolean findDirectionForward);

  public abstract boolean isFromCursor();

  public abstract void setFromCursor(boolean findFromCursor);

  public abstract boolean isGlobal();

  public abstract void setGlobal(boolean findGlobalScope);

  public abstract boolean isCaseSensitive();

  public abstract void setCaseSensitive(boolean caseSensitiveSearch);

  public abstract boolean isPreserveCase();

  public abstract void setPreserveCase(boolean preserveCase);

  public abstract boolean isWholeWordsOnly();

  public abstract void setWholeWordsOnly(boolean wholeWordsOnly);

  public abstract boolean isRegularExpressions();

  public abstract void setRegularExpressions(boolean regularExpressions);

  public abstract void addStringToFind(String s);

  public abstract void addStringToReplace(String s);

  public abstract void addDirectory(String s);

  public abstract String[] getRecentFindStrings();

  public abstract String[] getRecentReplaceStrings();

  /**
   * Returns the list of file masks used by the user in the "File name filter"
   * group box.
   *
   * @return the recent file masks list
   * @since 5.0.2
   */
  public abstract String[] getRecentFileMasks();

  public abstract ArrayList<String> getRecentDirectories();

  public abstract void setWithSubdirectories(boolean b);

  public abstract void initModelBySetings(FindModel model);

  public abstract String getFileMask();

  public abstract void setFileMask(String fileMask);
}
