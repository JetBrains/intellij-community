### Updating the WebP JNI library

1. Open the _IntelliJ Platform Products | IntelliJ Project Dependencies | WebP_ project in TeamCity.
2. Open VCS settings and change the _Default branch_ to a tag of a new version (e.g., `refs/tags/v1.3.2`).
3. Run all builds, download the artifacts, and replace libraries in `community/plugins/webp/lib/libwebp/<os>`
   directories (for macOS, combine artifacts into a universal binary first with `lipo`).
4. Update `community/plugins/webp/lib/libwebp.jar` ...
5. Update the version constant in `WebpNativeLibHelper#getDecoderVersion`.
