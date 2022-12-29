fun Container.productionBuild() {
  env["AUTOMATED_PRODUCTION_BUILD"] = "true"
}

fun Container.platformVersion(version: String) {
  env["PLATFORM_VERSION"] = version
}

job("Mermaid / Build for 231") {
  startOn {
    gitPush {
      enabled = true
    }
    schedule {
      cron("0 0 6 * * ?")
    }
  }
  container("openjdk:17") {
    productionBuild()
    platformVersion("231-SNAPSHOT")
    shellScript {
      content = "./gradlew build"
    }
  }
}

job("Mermaid / Build for 223") {
  container("openjdk:17") {
    productionBuild()
    platformVersion("223-EAP-SNAPSHOT")
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

job("Mermaid / Release / Stable") {
  startOn {
    gitPush {
      enabled = false
    }
  }
  container("openjdk:17") {
    productionBuild()
    env["MARKETPLACE_TOKEN"] = marketplaceToken
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
    env["MARKETPLACE_TOKEN"] = marketplaceToken
    env["MARKETPLACE_CHANNEL"] = "Nightly"
    shellScript {
      content = "./gradlew test publishPlugin"
    }
  }
}
