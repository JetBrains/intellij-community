"""The bundled Maven distribution and its libraries, which the Maven plugin layout copies.

`bundledMavenVersion`, `bundledMaven3Libraries` and `bundledMavenTelemetryLibraries` pin them.
"""

load("@community//build:download_helpers.bzl", "MAVEN_CENTRAL_URL", "maven_coordinates_urls", "maven_url", "pinned", "read_properties")
load("@community//build:download_rules.bzl", "download_repo")

_DEPENDENCIES = Label("@community//build:dependencies/dependencies.properties")

def _maven_downloads_impl(module_ctx):
    dependencies = read_properties(module_ctx, _DEPENDENCIES)

    # BundledMavenDownloader.downloadMavenDistribution - the one archive of `:files`, so a plugin layout names it
    # without its version
    download_repo(
        name = "bundled_maven",
        urls = [maven_url(
            MAVEN_CENTRAL_URL,
            "org.apache.maven",
            "apache-maven",
            pinned(dependencies, _DEPENDENCIES, "bundledMavenVersion"),
            "zip",
            classifier = "bin",
        )],
    )

    # BundledMavenDownloader.resolveMaven3Libs - a plugin layout copies the whole tree, so it names no jar
    download_repo(
        name = "bundled_maven3_libraries",
        urls = maven_coordinates_urls(MAVEN_CENTRAL_URL, pinned(dependencies, _DEPENDENCIES, "bundledMaven3Libraries")),
    )

    # BundledMavenDownloader.resolveMavenTelemetryDependencies - a plugin layout copies the whole tree
    download_repo(
        name = "bundled_maven_telemetry_libraries",
        urls = maven_coordinates_urls(MAVEN_CENTRAL_URL, pinned(dependencies, _DEPENDENCIES, "bundledMavenTelemetryLibraries")),
    )
    return module_ctx.extension_metadata(reproducible = True)

maven_downloads = module_extension(implementation = _maven_downloads_impl)
