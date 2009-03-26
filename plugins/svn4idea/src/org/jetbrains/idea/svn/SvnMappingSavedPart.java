package org.jetbrains.idea.svn;

import java.util.List;
import java.util.ArrayList;

public class SvnMappingSavedPart {
  public List<SvnCopyRootSimple> myMappingRoots;
  public List<SvnCopyRootSimple> myMoreRealMappingRoots;

  public SvnMappingSavedPart() {
    myMappingRoots = new ArrayList<SvnCopyRootSimple>();
    myMoreRealMappingRoots = new ArrayList<SvnCopyRootSimple>();
  }

  public void add(final SvnCopyRootSimple copy) {
    myMappingRoots.add(copy);
  }

  public void addReal(final SvnCopyRootSimple copy) {
    myMoreRealMappingRoots.add(copy);
  }

  public List<SvnCopyRootSimple> getMappingRoots() {
    return myMappingRoots;
  }

  public List<SvnCopyRootSimple> getMoreRealMappingRoots() {
    return myMoreRealMappingRoots;
  }
}
