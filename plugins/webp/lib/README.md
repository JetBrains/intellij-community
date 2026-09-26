### Updating the WebP JNI library

1. Open the _Ultimate | IntelliJ Project Dependencies | WebP_ project in TeamCity.
2. Open VCS settings and change the _Default branch_ to a tag of a new version (e.g., `refs/tags/v1.6.0`).
3. In the _Assemble_ build step script of the _Publish_ build configuration, update the `<version>` tag.
4. Run the build configuration and verify the new version is published to the [intellij-dependencies](https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/org/jetbrains/intellij/deps/libwebp/) repository.
5. Update the `libwebpVersion` property in `community/build/dependencies/dependencies.properties`.
6. Update the version constant in `WebpNativeLibHelper#getDecoderVersion`.
