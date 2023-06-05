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

job("Mermaid / Build for 231.8770+") {
  startOn {
    gitPush {
      enabled = true
    }
    schedule {
      cron("0 6 * * *")
    }
  }
  container("openjdk:17") {
    productionBuild()
    shellScript {
      content = "./gradlew build"
    }
  }
}

job("Mermaid / Plugin Verifier") {
  container("openjdk:17") {
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
  container("openjdk:17") {
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
  container("openjdk:17") {
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
      content = "qodana"
    }
  }
}
