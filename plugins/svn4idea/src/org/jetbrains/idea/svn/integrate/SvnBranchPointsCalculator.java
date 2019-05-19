// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SmallMapSerializer;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.history.CopyData;
import org.jetbrains.idea.svn.history.FirstInBranch;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.jetbrains.idea.svn.branchConfig.UrlDescriptor.DECODED_URL_DESCRIPTOR;
import static org.jetbrains.idea.svn.branchConfig.UrlDescriptor.ENCODED_URL_DESCRIPTOR;

public class SvnBranchPointsCalculator {

  private static final Logger LOG = Logger.getInstance(SvnBranchPointsCalculator.class);

  @NotNull private final SmallMapSerializer<Url, TreeMap<String, BranchCopyData>> myPersistentMap;
  @NotNull private final Object myPersistenceLock = new Object();
  @NotNull private final SvnVcs myVcs;

  public SvnBranchPointsCalculator(@NotNull SvnVcs vcs) {
    myVcs = vcs;
    File directory = new File(new File(PathManager.getSystemPath(), "vcs"), "svn_copy_sources");
    directory.mkdirs();
    File file = new File(directory, myVcs.getProject().getLocationHash());
    myPersistentMap = new SmallMapSerializer<>(file, DECODED_URL_DESCRIPTOR, new BranchDataExternalizer());
  }

  @Nullable
  public WrapperInvertor getBestHit(@NotNull Url repoUrl, @NotNull Url sourceUrl, @NotNull Url targetUrl) {
    synchronized (myPersistenceLock) {
      WrapperInvertor result = null;
      TreeMap<String, BranchCopyData> map = myPersistentMap.get(repoUrl);

      if (map != null) {
        BranchCopyData sourceData = getBranchData(map, sourceUrl);
        BranchCopyData targetData = getBranchData(map, targetUrl);

        if (sourceData != null && targetData != null) {
          boolean inverted = sourceData.getTargetRevision() > targetData.getTargetRevision();
          result = new WrapperInvertor(inverted, inverted ? sourceData : targetData);
        }
        else if (sourceData != null) {
          result = new WrapperInvertor(true, sourceData);
        }
        else if (targetData != null) {
          result = new WrapperInvertor(false, targetData);
        }
      }

      return result;
    }
  }

  public void deactivate() {
    synchronized (myPersistenceLock) {
      myPersistentMap.force();
    }
  }

  private void persist(@NotNull Url repoUrl, @NotNull BranchCopyData data) {
    // todo - rewrite of rather big piece; consider rewriting
    synchronized (myPersistenceLock) {
      TreeMap<String, BranchCopyData> map = myPersistentMap.get(repoUrl);
      if (map == null) {
        map = new TreeMap<>();
      }
      map.put(data.getTarget().toString(), data);
      myPersistentMap.put(repoUrl, map);
      myPersistentMap.force();
    }
  }

  @Nullable
  private static BranchCopyData getBranchData(@NotNull NavigableMap<String, BranchCopyData> map, @NotNull Url url) {
    Map.Entry<String, BranchCopyData> branchData = map.floorEntry(url.toString());
    return branchData != null && url.toString().startsWith(branchData.getKey()) ? branchData.getValue() : null;
  }

  private static class BranchDataExternalizer implements DataExternalizer<TreeMap<String, BranchCopyData>> {
    @Override
    public void save(@NotNull DataOutput out, @NotNull TreeMap<String, BranchCopyData> value) throws IOException {
      out.writeInt(value.size());
      for (Map.Entry<String, BranchCopyData> entry : value.entrySet()) {
        out.writeUTF(entry.getKey());
        save(out, entry.getValue());
      }
    }

    private static void save(@NotNull DataOutput out, @NotNull BranchCopyData value) throws IOException {
      ENCODED_URL_DESCRIPTOR.save(out, value.getSource());
      ENCODED_URL_DESCRIPTOR.save(out, value.getTarget());
      out.writeLong(value.getSourceRevision());
      out.writeLong(value.getTargetRevision());
    }

    @Override
    @NotNull
    public TreeMap<String, BranchCopyData> read(@NotNull DataInput in) throws IOException {
      TreeMap<String, BranchCopyData> result = new TreeMap<>();
      int size = in.readInt();

      for (int i = 0; i < size; i++) {
        result.put(in.readUTF(), readCopyPoint(in));
      }

      return result;
    }

    @NotNull
    private static BranchCopyData readCopyPoint(@NotNull DataInput in) throws IOException {
      Url source = ENCODED_URL_DESCRIPTOR.read(in);
      Url target = ENCODED_URL_DESCRIPTOR.read(in);
      long sourceRevision = in.readLong();
      long targetRevision = in.readLong();

      return new BranchCopyData(source, sourceRevision, target, targetRevision);
    }
  }

  public static class WrapperInvertor {
    private final BranchCopyData myWrapped;
    private final boolean myInvertedSense;

    public WrapperInvertor(boolean invertedSense, BranchCopyData wrapped) {
      myInvertedSense = invertedSense;
      myWrapped = wrapped;
    }

    public boolean isInvertedSense() {
      return myInvertedSense;
    }

    public BranchCopyData getWrapped() {
      return myWrapped;
    }

    public BranchCopyData getTrue() {
      return myInvertedSense ? myWrapped.invertSelf() : myWrapped;
    }

    public BranchCopyData inverted() {
      return myWrapped.invertSelf();
    }

    @Override
    public String toString() {
      return "inverted: " + myInvertedSense + " wrapped: " + myWrapped.toString();
    }
  }

  @Nullable
  public WrapperInvertor calculateCopyPoint(@NotNull Url repoUrl, @NotNull Url sourceUrl, @NotNull Url targetUrl)
    throws VcsException {
    WrapperInvertor result = getBestHit(repoUrl, sourceUrl, targetUrl);

    if (result == null) {
      CopyData copyData = new FirstInBranch(myVcs, repoUrl, targetUrl, sourceUrl).run();

      if (copyData != null) {
        BranchCopyData branchCopyData =
          copyData.isTrunkSupposedCorrect()
          ? new BranchCopyData(sourceUrl, copyData.getCopySourceRevision(), targetUrl, copyData.getCopyTargetRevision())
          : new BranchCopyData(targetUrl, copyData.getCopySourceRevision(), sourceUrl, copyData.getCopyTargetRevision());

        persist(repoUrl, branchCopyData);
        result = new WrapperInvertor(!copyData.isTrunkSupposedCorrect(), branchCopyData);
      }
    }

    logCopyData(repoUrl, sourceUrl, targetUrl, result);

    return result;
  }

  private static void logCopyData(@NotNull Url repoUrl,
                                  @NotNull Url sourceUrl,
                                  @NotNull Url targetUrl,
                                  @Nullable WrapperInvertor inverter) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("repoURL: " +
                repoUrl.toDecodedString() +
                ", sourceUrl:" +
                sourceUrl.toDecodedString() +
                ", targetUrl: " +
                targetUrl.toDecodedString() +
                ", inverter: " +
                inverter);
    }
  }

  public static class BranchCopyData {
    @NotNull private final Url mySource;
    @NotNull private final Url myTarget;
    private final long mySourceRevision;
    private final long myTargetRevision;

    public BranchCopyData(@NotNull Url source, long sourceRevision, @NotNull Url target, long targetRevision) {
      mySource = source;
      mySourceRevision = sourceRevision;
      myTarget = target;
      myTargetRevision = targetRevision;
    }

    @Override
    public String toString() {
      return "source: " + mySource + "@" + mySourceRevision + " target: " + myTarget + "@" + myTargetRevision;
    }

    @NotNull
    public Url getSource() {
      return mySource;
    }

    public long getSourceRevision() {
      return mySourceRevision;
    }

    @NotNull
    public Url getTarget() {
      return myTarget;
    }

    public long getTargetRevision() {
      return myTargetRevision;
    }

    public BranchCopyData invertSelf() {
      return new BranchCopyData(myTarget, myTargetRevision, mySource, mySourceRevision);
    }
  }
}
