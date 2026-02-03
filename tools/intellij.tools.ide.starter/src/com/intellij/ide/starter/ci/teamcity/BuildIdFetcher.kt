package com.intellij.ide.starter.ci.teamcity

import java.net.URI

interface BuildIdFetcher{
  fun getBuildId(buildType: String): String?
}

abstract class AbstractBuildIdFetcher : BuildIdFetcher {
  protected fun getBuildIdFromUrl(fullUrl: URI): String? {
    val build = TeamCityClient.get(fullUrl).properties().first { it.key == "build" }.value
    val buildId = build.findValue("id")
    if (buildId != null) {
      return buildId.asText()
    }
    else {
      return null
    }
  }
}

class LastBuildInBranch(val branch: String): AbstractBuildIdFetcher(){
  override fun getBuildId(buildType: String): String? {
    val fullUrl = TeamCityClient.guestAuthUri.resolve("builds?locator=buildType:${buildType},branch:$branch,state:(finished:true),count:1")
    return getBuildIdFromUrl(fullUrl)
  }
}

class LastSuccessfulBuildId : AbstractBuildIdFetcher(){
  override fun getBuildId(buildType: String): String? {
    val fullUrl = TeamCityClient.guestAuthUri.resolve("builds?locator=buildType:${buildType},status:SUCCESS,state:(finished:true),count:1")
    return getBuildIdFromUrl(fullUrl)
  }
}
