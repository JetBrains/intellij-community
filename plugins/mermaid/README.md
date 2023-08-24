# Mermaid

## Plugin description

**Note:** Links in the plugin description section have to be inlined.

[//]: # (Plugin description)

Provides support for creating diagrams with [Mermaid](https://mermaid-js.github.io/).

* Full support for Mermaid syntax: highlighting, completion, navigation, inspections, intentions, and much more
* Dedicated file types: `.mmd` and `.mermaid`
* Can be used with the [Markdown plugin](https://plugins.jetbrains.com/plugin/7793-markdown) to add assistance and rendering of Mermaid code blocks in Markdown files

[//]: # (Plugin description end)

## Version scheme

Plugins are required to use SemVer for its versioning.

* Nightly versions are marked with `nightly.X` qualifier, where `X` indicates current nightly iteration.
* Platform info is specified in the version metadata (stuff after the `+`).

## Releasing a new version of the plugin

> *Always release nightly version before the stable one*

1. Gather all meaningful changes that are appropriate for the public since the last stable release
   and add them to the [CHANGELOG.md](CHANGELOG.md). 
   Add the new version after the [Unreleased](CHANGELOG.md#unreleased) section.
2. Cherry-pick all the changes from `main` branch to other release branches.
3. Update plugin version in the [`gradle.properties`](gradle.properties) in `main` branch:
   * For nightly just increment the number in the `versionSuffix` property.
     * > Use `Update plugin version to 0.0.14-nightly.4` commit message as a template.
   * For stable release comment out the `versionSuffix` property and increment the `pluginVersion`.
     * > Use `[Release] Update plugin version to 0.0.14` commit message as a template.
4. Do the same thing for other release branches (please, don't cherry-pick version updates commits).
5. Push all the changes and wait for the CI to finish.
6. Run the corresponding release job from each release branch:
   * For nightly release - `Mermaid / Release / Nightly`
   * For stable release - `Mermaid / Release / Stable`
7. Make sure that new versions have been uploaded to the [Marketplace](https://plugins.jetbrains.com/plugin/20146-mermaid/versions/stable).
8. Verify that new versions are downloadable from the IDE and doesn't have obvious bugs.
