package org.jetbrains.idea.svn.difftool;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentableException;
import com.intellij.openapi.util.diff.impl.DiffViewerWrapper;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.diff.requests.ErrorDiffRequest;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestPresentable;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnChangeProvider;
import org.jetbrains.idea.svn.difftool.properties.SvnPropertiesDiffRequest;
import org.jetbrains.idea.svn.history.PropertyRevision;
import org.jetbrains.idea.svn.properties.PropertyData;

import java.util.List;
import java.util.Map;

public class SvnChangeDiffRequestProvider implements ChangeDiffRequestProvider {
  private static final Logger LOG = Logger.getInstance(SvnChangeDiffRequestProvider.class);

  @Override
  public boolean canCreate(@NotNull Project project, @NotNull Change change) {
    return getSvnChangeLayer(change) != null; // TODO: do not show, if no properties are set in both revisions ?
  }

  @NotNull
  @Override
  public DiffRequest process(@NotNull ChangeDiffRequestPresentable presentable,
                             @NotNull UserDataHolder context,
                             @NotNull ProgressIndicator indicator) throws DiffRequestPresentableException, ProcessCanceledException {
    DiffRequestPresentableException e1 = null;
    DiffRequestPresentableException e2 = null;

    DiffRequest propertyRequest;
    try {
      propertyRequest = createPropertyRequest(presentable.getChange(), indicator);
    }
    catch (DiffRequestPresentableException e) {
      e1 = e;
      propertyRequest = new ErrorDiffRequest(presentable, e);
    }

    DiffRequest contentRequest;
    try {
      contentRequest = ChangeDiffRequestPresentable.createRequest(presentable.getProject(), presentable.getChange(), context, indicator);
    }
    catch (DiffRequestPresentableException e) {
      e2 = e;
      contentRequest = new ErrorDiffRequest(presentable, e);
    }

    if (e1 != null && e2 != null) {
      LOG.info(e1);
      LOG.info(e2);
      throw new DiffRequestPresentableException(e1.getMessage() + "\n\n" + e2.getMessage());
    }

    contentRequest.putUserData(DiffViewerWrapper.KEY, new SvnDiffViewerWrapper(propertyRequest));

    return contentRequest;
  }

  @NotNull
  private static SvnPropertiesDiffRequest createPropertyRequest(@NotNull Change change, @NotNull ProgressIndicator indicator)
    throws DiffRequestPresentableException {
    try {
      Change propertiesChange = getSvnChangeLayer(change);
      if (propertiesChange == null) throw new DiffRequestPresentableException(SvnBundle.getString("diff.cant.get.properties.changes"));

      ContentRevision bRevRaw = propertiesChange.getBeforeRevision();
      ContentRevision aRevRaw = propertiesChange.getAfterRevision();

      if (bRevRaw != null && !(bRevRaw instanceof PropertyRevision)) {
        LOG.warn("Before change is not PropertyRevision");
        throw new DiffRequestPresentableException(SvnBundle.getString("diff.cant.get.properties.changes"));
      }
      if (aRevRaw != null && !(aRevRaw instanceof PropertyRevision)) {
        LOG.warn("After change is not PropertyRevision");
        throw new DiffRequestPresentableException(SvnBundle.getString("diff.cant.get.properties.changes"));
      }

      PropertyRevision bRev = (PropertyRevision)bRevRaw;
      PropertyRevision aRev = (PropertyRevision)aRevRaw;

      indicator.checkCanceled();
      List<PropertyData> bContent = bRev != null ? bRev.getProperties() : null;

      indicator.checkCanceled();
      List<PropertyData> aContent = aRev != null ? aRev.getProperties() : null;

      if (aRev == null && bRev == null) throw new DiffRequestPresentableException(SvnBundle.getString("diff.cant.get.properties.changes"));

      ContentRevision bRevMain = change.getBeforeRevision();
      ContentRevision aRevMain = change.getAfterRevision();
      String title1 = bRevMain != null ? StringUtil.nullize(bRevMain.getRevisionNumber().asString()) : null;
      String title2 = aRevMain != null ? StringUtil.nullize(aRevMain.getRevisionNumber().asString()) : null;

      return new SvnPropertiesDiffRequest(bContent, aContent, title1, title2);
    }
    catch (VcsException e) {
      throw new DiffRequestPresentableException(e);
    }
  }

  @Nullable
  private static Change getSvnChangeLayer(@NotNull Change change) {
    for (Map.Entry<String, Change> entry : change.getOtherLayers().entrySet()) {
      if (SvnChangeProvider.PROPERTY_LAYER.equals(entry.getKey())) {
        if (change.getOtherLayers().size() != 1) LOG.warn("Some of change layers ignored");
        return entry.getValue();
      }
    }
    return null;
  }
}
