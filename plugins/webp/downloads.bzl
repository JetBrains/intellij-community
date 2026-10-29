"""The libwebp natives of the WebP plugin, one archive for every platform. `libwebpVersion` pins it."""

load("@community//build:download_helpers.bzl", "INTELLIJ_DEPENDENCIES_URL", "maven_url", "pinned", "read_properties")
load("@community//build:download_rules.bzl", "download_repo")

_DEPENDENCIES = Label("@community//build:dependencies/dependencies.properties")

def _webp_downloads_impl(module_ctx):
    # The name `libwebp` is the Bazel module of the libwebp sources, so this repository is `libwebp_natives`.
    download_repo(
        name = "libwebp_natives",
        urls = [maven_url(
            INTELLIJ_DEPENDENCIES_URL,
            "org.jetbrains.intellij.deps",
            "libwebp",
            pinned(read_properties(module_ctx, _DEPENDENCIES), _DEPENDENCIES, "libwebpVersion"),
            "tar.gz",
        )],
    )
    return module_ctx.extension_metadata(reproducible = True)

webp_downloads = module_extension(implementation = _webp_downloads_impl)
