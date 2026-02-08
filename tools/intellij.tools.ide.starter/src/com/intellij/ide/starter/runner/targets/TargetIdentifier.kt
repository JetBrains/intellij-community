package com.intellij.ide.starter.runner.targets

import com.intellij.ide.starter.runner.targets.TargetIdentifier.Companion.current
import com.intellij.ide.starter.runner.targets.TargetIdentifier.Docker
import com.intellij.ide.starter.runner.targets.TargetIdentifier.Local
import com.intellij.ide.starter.runner.targets.TargetIdentifier.WSL
import com.intellij.platform.eel.EelApi
import java.lang.annotation.Inherited

/** "Environment" in which the test project will be uploaded. */
interface TargetIdentifier {
  /** The fixed target name that doesn't depend on the runtime params. Eg: WSL, Local, Docker */
  val targetName: String

  /**
   * A sanitized and safe name for this target, used to compose the test name ultimately passed to
   * [com.intellij.ide.starter.runner.TestContainer.newContext].
   */
  val instanceName: String

  /** A simplified version of [instanceName] */
  val instanceSimpleName: String

  val eelApi: EelApi

  companion object {
    @JvmStatic
    val current: TargetIdentifier get() = TargetResolver.instance.current
  }

  /** Local machine */
  interface Local : TargetIdentifier {
    override val targetName: String get() = "Local"
    override val instanceName: String get() = targetName.lowercase()
    override val instanceSimpleName: String get() = instanceName
  }

  /** Project will be uploaded to a docker container.
   * @see [TargetDockerConfig] for specifying docker image
   **/
  interface Docker : TargetIdentifier {
    override val targetName: String get() = "Docker"
  }

  /** Project will be uploaded to Windows Subsystem for Linux */
  interface WSL : TargetIdentifier {
    override val targetName: String get() = "WSL"
    override val instanceName: String get() = targetName.lowercase()
    override val instanceSimpleName: String get() = instanceName
  }
}


/** True if the project opened on local machine */
fun TargetIdentifier.isLocal(): Boolean = current is Local

/** True if the project opened in Docker */
fun TargetIdentifier.isDocker(): Boolean = current is Docker

/** True if the project opened in WSL */
fun TargetIdentifier.isWsl(): Boolean = current is WSL

/** @see [TargetIdentifier.Docker] */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@Repeatable
annotation class TargetDockerConfig(val dockerImage: String)

const val TARGET_DEFAULT_DOCKER_IMAGE = "registry.jetbrains.team/p/ij/docker-hub/ubuntu:22.04"