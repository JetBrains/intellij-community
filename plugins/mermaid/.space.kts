job("Mermaid / Build") {
    container("openjdk:17") {
        shellScript {
            content = "./gradlew build"
        }
    }
}
