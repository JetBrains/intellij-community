1. If needed, update `patchPluginXml.untilBuild`
1. Update `patchPluginXml.changeNotes` and `patchPluginXml.pluginDescription`
1. Temporarily set `intellij.version` to `patchPluginXml.sinceBuild` and run tests and runIDE. Then restore `intellij.version`.
1. Run tests and runIDE
1. Merge branch *master* to *releases*
1. In *releases* branch, set `version`
1. Make sure `publishPlugin.channels` is unset
1. Double check `patchPluginXml.changeNotes` and `patchPluginXml.pluginDescription`
1. Run `publishPlugin` task
1. `git commit -a …`, `git tag …`, `git push --tags`
1. Upload _build/distributions/*.zip_ to [github](https://github.com/ThomasR/intellij-diff-plugin/releases/new)
