job("Mermaid / Build") {
    container("openjdk:17") {
        shellScript {
            content = "./gradlew build"
        }
    }
}

job("Mermaid / Run Plugin Verifier") {
    container("openjdk:17") {
        shellScript {
            content = "./gradlew runPluginVerifier"
        }
    }
}
