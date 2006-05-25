/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.ui.search;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 17-Mar-2006
 */
public class OptionDescription implements Comparable{
  private String myOption;
  private String myHit;
  private String myPath;
  private String myConfigurableId;

  public OptionDescription(final String option, final String hit, final String path) {
    myOption = option;
    myHit = hit;
    myPath = path;
  }

  public OptionDescription(String hit) {
    myHit = hit;
  }


  public OptionDescription(final String option, final String configurableId, final String hit, final String path) {
    myOption = option;
    myHit = hit;
    myPath = path;
    myConfigurableId = configurableId;
  }

  public String getOption() {
    return myOption;
  }

  @Nullable
  public String getHit() {
    return myHit;
  }

  @Nullable
  public String getPath() {
    return myPath;
  }


  public String getConfigurableId() {
    return myConfigurableId;
  }

  public String toString() {
    return myHit;
  }


  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final OptionDescription that = (OptionDescription)o;

    if (myConfigurableId != null ? !myConfigurableId.equals(that.myConfigurableId) : that.myConfigurableId != null) return false;
    if (myHit != null ? !myHit.equals(that.myHit) : that.myHit != null) return false;
    if (myOption != null ? !myOption.equals(that.myOption) : that.myOption != null) return false;
    if (myPath != null ? !myPath.equals(that.myPath) : that.myPath != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myOption != null ? myOption.hashCode() : 0);
    result = 31 * result + (myHit != null ? myHit.hashCode() : 0);
    result = 31 * result + (myPath != null ? myPath.hashCode() : 0);
    result = 31 * result + (myConfigurableId != null ? myConfigurableId.hashCode() : 0);
    return result;
  }

  public int compareTo(final Object o) {
    final OptionDescription description = ((OptionDescription)o);
    if (Comparing.strEqual(myHit, description.getHit())){
      return myOption != null ? myOption.compareTo(description.getOption()) : 0;
    }
    if (myHit != null && description.getHit() != null){
      return myHit.compareTo(description.getHit());
    }
    return 0;
  }
}
