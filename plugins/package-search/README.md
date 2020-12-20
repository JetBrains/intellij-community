# Package Search IntelliJ plugin

This directory contains the IJ plugin for Package Search.

## Testing against non-prod backend
By default, the plugin will use the REST endpoints located at`https://package-search.services.jetbrains.com/api`. If you ever needed to
test against a different deployment of the APIs, you can temporarily change the `ServerURLs.base` property value in the
[`SearchClient.kt`](src/com/jetbrains/packagesearch/intellij/plugin/api/SearchClient.kt) file, for example pointing it to a local
instance of the backend such as `http://localhost:7676`, or a staging environment.

Please note that some of the tests in the project are not unit tests but rather integration tests that hit the backend. By default, those
tests will be run against the production environment as per the `ServerURLs.base` value, so changing that will impact those tests. Also, if you
make changes to the backend and want to run the plugin integration tests, you should be temporarily changing those tests to hit a local deploy
of the backend, not the production one.

You can identify integration tests as being subclasses of the 
[`SearchClientTestsBase`](tests/com/jetbrains/packagesearch/intellij/plugin/api/SearchClientTestsBase.kt) class.

## Releasing a new version of the plugin
The plugin is bundled with IJC/IJU starting from v203, but was distributed via the [Plugin Marketplace](https://plugins.jetbrains.com/plugin/12507-package-search/)
for previous versions, while in EAP. We'll still be able to distribute hotfixes via the marketplace if need be, but we'll only implement
new features in the bundled versions of the plugin.

To release a new version, you will have to first make sure all required changes are on `master`, **including the changelog** for the new
version. Make sure the [`CHANGELOG.md`](CHANGELOG.md) file has a `## NEW VERSION` section at the top, and that section contains all the
highlights for the release. Use the same format other entries have.

NOTE: the following process refers to the pre-bundling releases, the process is different for releases.

It's important that the changelog is up-to-date and committed/pushed to `master` _before_ you cut the release as the plugin binary will inherit
the release notes from that file. When cutting the release, the release notes will be tagged with a version number and date of publishing by the
build system, and the changelog updated automatically — no manual changes required there.

Once you're sure the new version changelog is up to date and has been pushed, you need to head to
[the CI](https://buildserver.labs.intellij.net/buildConfiguration/kpm_intellij_release_publish?branch=%3Cdefault%3E) and start the
_Package Search / IntelliJ Plugin / IntelliJ Plugin Releases / Publish Release of the IntelliJ Plugin_ job.

If the job and its dependencies complete correctly, you should find the new plugin version pushed and available on the Marketplace. You can go to
[the listing page](https://plugins.jetbrains.com/plugin/edit?pluginId=12507) for the plugin and check from there. Note that it may take a few minutes
for the new version to show up, in some cases.
