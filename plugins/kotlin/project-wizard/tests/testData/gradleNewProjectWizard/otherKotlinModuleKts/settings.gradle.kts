plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "FOOJAY_VERSION"
}
rootProject.name = "project"
include("othermodule")
include("module")
