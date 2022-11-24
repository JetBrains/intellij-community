job("Mermaid / Build") {
  container("openjdk:17") {
    shellScript {
      content = "./gradlew build"
    }
  }
}

job("Mermaid / Plugin Verifier") {
  container("openjdk:17") {
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
    env["MARKETPLACE_TOKEN"] = marketplaceToken
    env["MARKETPLACE_CHANNEL"] = "Nightly"
    shellScript {
      content = "./gradlew test publishPlugin"
    }
  }
}
