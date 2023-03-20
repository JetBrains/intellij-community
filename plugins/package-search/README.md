# Package Search IntelliJ plugin

This directory contains the IJ plugin for Package Search.

## Testing against non-prod backend

By default, the plugin will use the production backend. If you ever needed to test against a different deployment of the APIs, you can temporarily
change the `ServerURLs.base` property value in the
[`DefaultPackageServiceConfig.kt`](community/plugins/repository-search/src/main/kotlin/org/jetbrains/idea/packagesearch/DefaultPackageServiceConfig.kt) file, for example pointing it to a local
instance of the backend such as `http://localhost:7676`, or a staging environment.

Please note that some tests in the project are not unit tests, but rather integration tests that hit the backend. By default, those tests will be run
against the production environment as per the `ServerURLs.base` value, so changing that will impact those tests. Also, if you make changes to the
backend and want to run the plugin integration tests, you should be temporarily changing those tests to hit a local deploy of the backend, not the
production one.

You can identify integration tests as being subclasses of the
[`SearchClientTestsBase`](test-src/com/jetbrains/packagesearch/intellij/plugin/api/SearchClientTestsBase.kt) class.
