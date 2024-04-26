// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.branchConfig

import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil.syncPublisher
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.committed.VcsConfigurationChangeListener.BRANCHES_CHANGED
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.ProgressManagerQueue
import org.jetbrains.idea.svn.SvnBundle.message
import org.jetbrains.idea.svn.SvnUtil.createUrl
import org.jetbrains.idea.svn.SvnVcs
import org.jetbrains.idea.svn.api.Url
import org.jetbrains.idea.svn.commandLine.SvnBindException
import java.util.*

private val LOG = logger<SvnBranchConfigurationManager>()

@Service(Service.Level.PROJECT)
@State(name = "SvnBranchConfigurationManager")
internal class SvnBranchConfigurationManager(private val project: Project) : PersistentStateComponent<SvnBranchConfigurationManager.ConfigurationBean> {
  private val branchesLoader = ProgressManagerQueue(project, message("progress.title.svn.branches.preloader"))
  val svnBranchConfigManager: NewRootBunch = NewRootBunch(project, branchesLoader)
  private var isInitialized = false

  val supportValue: Long?
    get() = configurationBean.myVersion

  private var configurationBean = ConfigurationBean()

  class ConfigurationBean {
    @JvmField
    var myConfigurationMap: MutableMap<String, SvnBranchConfiguration> = TreeMap()
    /**
     * version of "support SVN in IDEA". for features tracking. should grow
     */
    @JvmField
    var myVersion: Long? = null
  }

  fun get(vcsRoot: VirtualFile): SvnBranchConfigurationNew = svnBranchConfigManager.getConfig(vcsRoot)

  fun setConfiguration(vcsRoot: VirtualFile, configuration: SvnBranchConfigurationNew) {
    svnBranchConfigManager.updateForRoot(vcsRoot, InfoStorage(configuration, InfoReliability.setByUser), true)

    SvnBranchMapperManager.getInstance().notifyBranchesChanged(project, vcsRoot, configuration)
    syncPublisher(project, BRANCHES_CHANGED).execute(project, vcsRoot)
  }

  override fun getState(): ConfigurationBean = ConfigurationBean().apply {
    myVersion = configurationBean.myVersion

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
    configurationBean = state
  }

  @Synchronized
  private fun initialize() {
    if (!isInitialized) {
      isInitialized = true

      preloadBranches(resolveAllBranchPoints())
    }
  }

  private fun resolveAllBranchPoints(): Set<Pair<VirtualFile, SvnBranchConfigurationNew>> {
    val lfs = LocalFileSystem.getInstance()
    val branchPointsToLoad = mutableSetOf<Pair<VirtualFile, SvnBranchConfigurationNew>>()

    for ((path, persistedConfiguration) in configurationBean.myConfigurationMap) {
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
    val userInfo = if (persistedConfiguration.isUserinfoInUrl) SvnVcs.getInstance(project).svnFileUrlMapping.getUrlForFile(
      virtualToIoFile(root))?.userInfo
    else null
    val result = SvnBranchConfigurationNew().apply {
      // trunk url could be either decoded or encoded depending on the code flow
      trunk = persistedConfiguration.trunkUrl?.let { addUserInfo(it, true, userInfo) }
      isUserInfoInUrl = persistedConfiguration.isUserinfoInUrl
    }

    val storage = SvnLoadedBranchesStorage.getInstance(project)
    for (branchLocation in persistedConfiguration.branchUrls.mapNotNull { addUserInfo(it, false, userInfo) }) {
      val storedBranches = storage.get(branchLocation)?.sorted() ?: mutableListOf()
      result.addBranches(branchLocation,
                         InfoStorage(storedBranches, if (storedBranches.isNotEmpty()) InfoReliability.setByUser else InfoReliability.empty))
      if (storedBranches.isEmpty()) {
        branchPointsToLoad.add(root to result)
      }
    }

    return result
  }

  private fun preloadBranches(branchPoints: Collection<Pair<VirtualFile, SvnBranchConfigurationNew>>) {
    ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
      getApplication().executeOnPooledThread {
        runReadAction {
          if (!project.isDisposed) branchesLoader.start()
        }

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
    fun getInstance(project: Project): SvnBranchConfigurationManager =
      project.getService(SvnBranchConfigurationManager::class.java)!!.apply { initialize() }
  }
}
