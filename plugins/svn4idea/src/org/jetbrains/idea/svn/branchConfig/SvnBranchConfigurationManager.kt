// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.svn.branchConfig

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.committed.VcsConfigurationChangeListener
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.ProgressManagerQueue
import org.jetbrains.idea.svn.SvnVcs

import java.io.File
import java.util.*

/**
 * @author yole
 */
@State(name = "SvnBranchConfigurationManager")
class SvnBranchConfigurationManager(private val myProject: Project,
                                    private val myVcsManager: ProjectLevelVcsManager,
                                    private val myStorage: SvnLoadedBranchesStorage) : PersistentStateComponent<SvnBranchConfigurationManager.ConfigurationBean> {
  private val myBranchesLoader = ProgressManagerQueue(myProject, "Subversion Branches Preloader")
  val svnBranchConfigManager = NewRootBunch(myProject, myBranchesLoader)
  private var myIsInitialized = false

  val supportValue get() = myConfigurationBean.myVersion
  private var myConfigurationBean = ConfigurationBean()

  init {
    // TODO: Seems that ProgressManagerQueue is not suitable here at least for some branches loading tasks. For instance,
    // TODO: for DefaultConfigLoader it would be better to run modal cancellable task - so branches structure could be detected and
    // TODO: shown in dialog. Currently when "Configure Branches" is invoked for the first time - no branches are shown.
    // TODO: If "Cancel" is pressed and "Configure Branches" invoked once again - already detected (in background) branches are shown.
    (myVcsManager as ProjectLevelVcsManagerImpl).addInitializationRequest(VcsInitObject.BRANCHES) {
      ApplicationManager.getApplication().runReadAction {
        if (!myProject.isDisposed) myBranchesLoader.start()
      }
    }
  }

  class ConfigurationBean {
    @JvmField
    var myConfigurationMap: MutableMap<String, SvnBranchConfiguration> = TreeMap()
    /**
     * version of "support SVN in IDEA". for features tracking. should grow
     */
    @JvmField
    var myVersion: Long? = null
  }

  fun get(vcsRoot: VirtualFile): SvnBranchConfigurationNew {
    return svnBranchConfigManager.getConfig(vcsRoot)
  }

  fun setConfiguration(vcsRoot: VirtualFile, configuration: SvnBranchConfigurationNew) {
    svnBranchConfigManager.updateForRoot(vcsRoot, InfoStorage(configuration, InfoReliability.setByUser), true)

    SvnBranchMapperManager.getInstance().notifyBranchesChanged(myProject, vcsRoot, configuration)

    BackgroundTaskUtil.syncPublisher<VcsConfigurationChangeListener.Notification>(
      myProject, VcsConfigurationChangeListener.BRANCHES_CHANGED).execute(myProject, vcsRoot)
  }

  override fun getState(): ConfigurationBean {
    val result = ConfigurationBean()
    result.myVersion = myConfigurationBean.myVersion
    val helper = UrlSerializationHelper(SvnVcs.getInstance(myProject))

    for (root in svnBranchConfigManager.mapCopy.keys) {
      val key = root.path
      val configOrig = svnBranchConfigManager.getConfig(root)
      val configuration = SvnBranchConfiguration(configOrig.trunkUrl, configOrig.branchUrls, configOrig.isUserInfoInUrl)

      result.myConfigurationMap[key] = helper.prepareForSerialization(configuration)
    }
    return result
  }

  override fun loadState(`object`: ConfigurationBean) {
    myConfigurationBean = `object`
  }

  @Synchronized
  private fun initialize() {
    if (!myIsInitialized) {
      myIsInitialized = true

      preloadBranches(resolveAllBranchPoints())
    }
  }

  private fun resolveAllBranchPoints(): Set<Pair<VirtualFile, SvnBranchConfigurationNew>> {
    val lfs = LocalFileSystem.getInstance()
    val helper = UrlSerializationHelper(SvnVcs.getInstance(myProject))
    val branchPointsToLoad = ContainerUtil.newHashSet<Pair<VirtualFile, SvnBranchConfigurationNew>>()
    for ((key, configuration) in myConfigurationBean.myConfigurationMap) {
      val root = lfs.refreshAndFindFileByIoFile(File(key))
      if (root == null) {
        LOG.info("root not found: $key")
        continue
      }

      val configToConvert: SvnBranchConfiguration
      if (configuration.isUserinfoInUrl) {
        configToConvert = helper.afterDeserialization(key, configuration)
      }
      else {
        configToConvert = configuration
      }
      val newConfig = SvnBranchConfigurationNew()
      newConfig.trunkUrl = configToConvert.trunkUrl
      newConfig.isUserInfoInUrl = configToConvert.isUserinfoInUrl
      for (branchUrl in configToConvert.branchUrls) {
        val stored = getStored(branchUrl)
        if (stored != null && !stored.isEmpty()) {
          newConfig.addBranches(branchUrl, InfoStorage(stored, InfoReliability.setByUser))
        }
        else {
          branchPointsToLoad.add(Pair.create(root, newConfig))
          newConfig.addBranches(branchUrl, InfoStorage(ArrayList(), InfoReliability.empty))
        }
      }

      svnBranchConfigManager.updateForRoot(root, InfoStorage(newConfig, InfoReliability.setByUser), false)
    }
    return branchPointsToLoad
  }

  private fun preloadBranches(branchPoints: Collection<Pair<VirtualFile, SvnBranchConfigurationNew>>) {
    (myVcsManager as ProjectLevelVcsManagerImpl).addInitializationRequest(VcsInitObject.BRANCHES) {
      ApplicationManager.getApplication().executeOnPooledThread {
        for (pair in branchPoints) {
          svnBranchConfigManager.reloadBranches(pair.getFirst(), null, pair.getSecond())
        }
      }
    }
  }

  private fun getStored(branchUrl: String): List<SvnBranchItem>? {
    val collection = myStorage[branchUrl] ?: return null
    val items = ArrayList(collection)
    Collections.sort(items)
    return items
  }

  companion object {
    private val LOG = Logger.getInstance("#org.jetbrains.idea.svn.branchConfig.SvnBranchConfigurationManager")

    @JvmStatic
    fun getInstance(project: Project): SvnBranchConfigurationManager {
      val result = ServiceManager.getService(project, SvnBranchConfigurationManager::class.java)

      result?.initialize()

      return result
    }
  }
}
