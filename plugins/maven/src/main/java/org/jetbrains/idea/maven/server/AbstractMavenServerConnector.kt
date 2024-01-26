// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.server

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.util.ExceptionUtil
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenModel
import org.jetbrains.idea.maven.server.MavenServerManager.Companion.getInstance
import java.io.File
import java.nio.file.Path
import java.rmi.RemoteException
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractMavenServerConnector(override val project: Project?,  // to be removed in future
                                            override val jdk: Sdk,
                                            override val vmOptions: String,
                                            override val mavenDistribution: MavenDistribution,
                                            multimoduleDirectory: String) : MavenServerConnector {
  @JvmField
  protected val myMultimoduleDirectories: MutableSet<String> = ConcurrentHashMap.newKeySet()
  private val embedderLock = Any()
  private val myCreationTrace = Exception()

  init {
    myMultimoduleDirectories.add(multimoduleDirectory)
  }

  override fun addMultimoduleDir(multimoduleDirectory: String): Boolean {
    return myMultimoduleDirectories.add(multimoduleDirectory)
  }

  protected abstract fun getServer(): MavenServer

  @Throws(RemoteException::class)
  override fun createEmbedder(settings: MavenEmbedderSettings): MavenServerEmbedder {
    synchronized(embedderLock) {
      try {
        return getServer().createEmbedder(settings, MavenRemoteObjectWrapper.ourToken)
      }
      catch (e: Exception) {
        val cause = ExceptionUtil.findCause(e, MavenCoreInitializationException::class.java)
        if (cause != null) {
          return MisconfiguredPlexusDummyEmbedder(project!!, cause.message!!,
                                                  myMultimoduleDirectories,
                                                  mavenDistribution.version,
                                                  cause.unresolvedExtensionId)
        }
        throw e
      }
    }
  }

  @Throws(RemoteException::class)
  override fun createIndexer(): MavenServerIndexer {
    synchronized(embedderLock) {
      return getServer().createIndexer(MavenRemoteObjectWrapper.ourToken)
    }
  }

  override fun interpolateAndAlignModel(model: MavenModel, basedir: Path, pomDir: Path): MavenModel {
    return perform {
        val transformer = RemotePathTransformerFactory.createForProject(project!!)
        val targetBasedir = File(transformer.toRemotePathOrSelf(basedir.toString()))
        val targetPomDir = File(transformer.toRemotePathOrSelf(pomDir.toString()))
      val m = getServer().interpolateAndAlignModel(model, targetBasedir, targetPomDir, MavenRemoteObjectWrapper.ourToken)
        if (transformer !== RemotePathTransformerFactory.Transformer.ID) {
          MavenBuildPathsChange({ s: String? -> transformer.toIdePath(s!!)!! }, { s: String? -> transformer.canBeRemotePath(s) }).perform(m)
        }
        m
      }
  }

  override fun assembleInheritance(model: MavenModel, parentModel: MavenModel): MavenModel {
    return perform { getServer().assembleInheritance(model, parentModel, MavenRemoteObjectWrapper.ourToken) }
  }

  override fun applyProfiles(model: MavenModel,
                             basedir: Path,
                             explicitProfiles: MavenExplicitProfiles,
                             alwaysOnProfiles: Collection<String>): ProfileApplicationResult {
    return perform {
      val transformer = RemotePathTransformerFactory.createForProject(project!!)
      val targetBasedir = File(transformer.toRemotePathOrSelf(basedir.toString()))
      getServer().applyProfiles(model, targetBasedir, explicitProfiles, HashSet(alwaysOnProfiles), MavenRemoteObjectWrapper.ourToken)
      }
  }

  protected abstract fun <R> perform(r: () -> R): R

  override fun dispose() {
    getInstance().shutdownConnector(this, true)
  }


  override val multimoduleDirectories: List<String>
    get() = ArrayList(myMultimoduleDirectories)

  override fun getDebugStatus(clean: Boolean): MavenServerStatus {
    return perform { getServer().getDebugStatus(clean) }
  }

  override fun toString(): String {
    return javaClass.simpleName + "{" +
           Integer.toHexString(this.hashCode()) +
           ", myDistribution=" + mavenDistribution.mavenHome +
           ", myJdk=" + jdk.name +
           ", myMultimoduleDirectories=" + myMultimoduleDirectories +
           ", myCreationTrace = " + ExceptionUtil.getThrowableText(myCreationTrace) +
           '}'
  }
}
