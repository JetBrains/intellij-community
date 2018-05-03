// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.idea.svn.branchConfig

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil.syncPublisher
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.committed.VcsConfigurationChangeListener
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.ProgressManagerQueue
import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.commandLine.SvnBindException
import java.util.*

private val LOG = logger<SvnBranchConfigurationManager>()

@State(name = "SvnBranchConfigurationManager")
class SvnBranchConfigurationManager(private val myProject: Project,
                                    vcsManager: ProjectLevelVcsManager,
                                    private val myStorage: SvnLoadedBranchesStorage) : PersistentStateComponent<SvnBranchConfigurationManager.ConfigurationBean> {
  private val myVcsManager = vcsManager as ProjectLevelVcsManagerImpl
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
    myVcsManager.addInitializationRequest(VcsInitObject.BRANCHES) {
      getApplication().runReadAction {
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

  fun get(vcsRoot: VirtualFile) = svnBranchConfigManager.getConfig(vcsRoot)

  fun setConfiguration(vcsRoot: VirtualFile, configuration: SvnBranchConfigurationNew) {
    svnBranchConfigManager.updateForRoot(vcsRoot, InfoStorage(configuration, InfoReliability.setByUser), true)

    SvnBranchMapperManager.getInstance().notifyBranchesChanged(myProject, vcsRoot, configuration)
    syncPublisher<VcsConfigurationChangeListener.Notification>(myProject, VcsConfigurationChangeListener.BRANCHES_CHANGED).execute(
      myProject, vcsRoot)
  }

  override fun getState() = ConfigurationBean().apply {
    myVersion = myConfigurationBean.myVersion

    for (root in svnBranchConfigManager.mapCopy.keys) {
      val configuration = svnBranchConfigManager.getConfig(root)
      val configurationToPersist = SvnBranchConfiguration().apply {
        isUserinfoInUrl = !configuration.trunk?.userInfo.isNullOrEmpty()
        trunkUrl = configuration.trunk?.let(::removeUserInfo)?.toDecodedString()
        branchUrls = configuration.branchLocations.map { removeUserInfo(it) }.map(Url::toString)
      }

      myConfigurationMap[root.path] = configurationToPersist
    }
  }

  override fun loadState(state: ConfigurationBean) {
    myConfigurationBean = state
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
    val branchPointsToLoad = mutableSetOf<Pair<VirtualFile, SvnBranchConfigurationNew>>()

    for ((path, persistedConfiguration) in myConfigurationBean.myConfigurationMap) {
      val root = lfs.refreshAndFindFileByPath(path)

      if (root != null) {
        val configuration = resolveConfiguration(root, persistedConfiguration, branchPointsToLoad)
        svnBranchConfigManager.updateForRoot(root, InfoStorage(configuration, InfoReliability.setByUser), false)
      }
      else {
        LOG.info("root not found: $path")
      }
    }

    return branchPointsToLoad
  }

  private fun resolveConfiguration(root: VirtualFile,
                                   persistedConfiguration: SvnBranchConfiguration,
                                   branchPointsToLoad: MutableSet<Pair<VirtualFile, SvnBranchConfigurationNew>>): SvnBranchConfigurationNew {
    val userInfo = if (persistedConfiguration.isUserinfoInUrl) SvnVcs.getInstance(myProject).svnFileUrlMapping.getUrlForFile(
      virtualToIoFile(root))?.userInfo
    else null
    val result = SvnBranchConfigurationNew().apply {
      // trunk url could be either decoded or encoded depending on the code flow
      trunk = persistedConfiguration.trunkUrl?.let { addUserInfo(it, true, userInfo) }
      isUserInfoInUrl = persistedConfiguration.isUserinfoInUrl
    }

    for (branchLocation in persistedConfiguration.branchUrls.mapNotNull { addUserInfo(it, false, userInfo) }) {
      val storedBranches = myStorage[branchLocation]?.sorted() ?: mutableListOf()

      result.addBranches(branchLocation,
                         InfoStorage(storedBranches, if (!storedBranches.isEmpty()) InfoReliability.setByUser else InfoReliability.empty))
      if (storedBranches.isEmpty()) {
        branchPointsToLoad.add(root to result)
      }
    }

    return result
  }

  private fun preloadBranches(branchPoints: Collection<Pair<VirtualFile, SvnBranchConfigurationNew>>) {
    myVcsManager.addInitializationRequest(VcsInitObject.BRANCHES) {
      getApplication().executeOnPooledThread {
        for ((root, configuration) in branchPoints) {
          svnBranchConfigManager.reloadBranches(root, null, configuration)
        }
      }
    }
  }

  private fun removeUserInfo(url: Url) = if (url.userInfo.isNullOrEmpty()) url
  else try {
    url.setUserInfo(null)
  }
  catch (e: SvnBindException) {
    LOG.info("Could not remove user info ${url.userInfo} from url ${url.toDecodedString()}", e)
    url
  }

  private fun addUserInfo(urlValue: String, smartParse: Boolean, userInfo: String?): Url? {
    val result: Url
    try {
      result = createUrl(urlValue, !smartParse || '%' in urlValue)
    }
    catch (e: SvnBindException) {
      LOG.info("Could not parse url $urlValue", e)
      return null
    }

    return if (userInfo.isNullOrEmpty()) result
    else try {
      result.setUserInfo(userInfo)
    }
    catch (e: SvnBindException) {
      LOG.info("Could not add user info $userInfo to url $urlValue", e)
      result
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) =
      ServiceManager.getService(project, SvnBranchConfigurationManager::class.java)!!.apply { initialize() }
  }
}
