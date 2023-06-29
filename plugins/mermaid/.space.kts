fun Container.productionBuild() {
  env["AUTOMATED_PRODUCTION_BUILD"] = "true"
}

fun Container.platformVersion(version: String) {
  env["PLATFORM_VERSION"] = version
}

object PublishChannels {
  const val NIGHTLY: String = "nightly"
  const val STABLE: String = ""
}

fun Container.publishingEnvironment(channel: String = PublishChannels.STABLE) {
  env["MARKETPLACE_TOKEN"] = marketplaceToken
  if (channel != PublishChannels.STABLE) {
    env["MARKETPLACE_CHANNEL"] = channel
  }
}

val defaultImage = "registry.jetbrains.team/p/grazi/grazie-automation/mermaid-ci:1.0.0"

job("Mermaid / Build for 232") {
  startOn {
    gitPush {
      enabled = true
    }
    schedule {
      cron("0 6 * * *")
    }
  }
  container(defaultImage) {
    productionBuild()
    shellScript {
      content = """
      ./gradlew build || exit
      export DISPLAY=:0.0
      Xvfb -ac :0 -screen 0 1920x1080x16 &
      ./gradlew previewTests || exit
      """.trimIndent()
    }
  }
}

job("Mermaid / Plugin Verifier") {
  container(defaultImage) {
    productionBuild()
    shellScript {
      content = "./gradlew runPluginVerifier"
    }
  }
}

val marketplaceToken
  get() = Secrets("mermaid_marketplace_token")

val qodanaCloudToken
  get() = Secrets("mermaid_qodana_cloud_token")

job("Mermaid / Release / Stable") {
  startOn {
    gitPush {
      enabled = false
    }
  }
  container(defaultImage) {
    productionBuild()
    publishingEnvironment(channel = PublishChannels.STABLE)
    shellScript {
      content = "./gradlew test publishPlugin"
    }
  }
}

job("Mermaid / Release / Nightly") {
  startOn {
    gitPush {
      enabled = false
    }
  }
  container(defaultImage) {
    productionBuild()
    publishingEnvironment(channel = PublishChannels.NIGHTLY)
    shellScript {
      content = "./gradlew test publishPlugin"
    }
  }
}

job("Mermaid / Qodana Analysis") {
  startOn {
    gitPush {
      enabled = true
    }
    codeReviewOpened()
  }
  container("jetbrains/qodana-jvm") {
    env["QODANA_TOKEN"] = qodanaCloudToken
    shellScript {
      content = "./gradlew :plugin:generateLexerAndParser && qodana"
    }
  }
}
