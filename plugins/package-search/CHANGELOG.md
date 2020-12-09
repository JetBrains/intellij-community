# Package Search Changelog

## NEW VERSION

 * Feature: Repositories tab in package search tool window shows indexed repositories
 * Feature: Multiple repositories can be searched and filtered (when indexed)

## Version 1.0.1191-eap (28 Aug 2020)

 * Bug: Plugin UI not showing metadata when unsupported platforms are present (PKGS-547)

## Version 1.0.1174-eap (6 Aug 2020)

 * Cosmetics: package operation confirmation dialog has been removed
 * Cosmetics: improve inspections names and description
 * Bug: the update package quick fix would not work correctly sometimes in Maven POMs (PKGS-489)

## Version 1.0.1145-eap (18 Jun 2020)

 * Bug: Up/down on search box/first result should navigate between controls
 * Bug: Updating the package deletes the line above and doesn't update version
 * Bug: Variables cause Update intentions to shown for all packages
 * Bug: Indicate incomplete Gradle sync state

## Version 1.0.1119-eap (29 May 2020)

 * Compatible with IDEA 2020.2 EAPs

## Version 1.0.1112-eap (20 May 2020)

 * Compatible with IDEA 2019.2+
 * Improvement: Reduce plugin size by about 8%
 * Improvement: Context menu on installed packages and search results
 * Bug: Failure to parse some Gradle repository declarations (PKGS-426)

## Version 1.0.921-eap (1 Apr 2020)

 * Improvement: Flag dependencies that can be updated in the editor, offer quick-fix
 * Improvement: Navigate to the definition of a dependency in pom.xml/build.gradle
 * Improvement: Quick Fix to add missing dependencies in editor
 * Improvement: Allow searching only MPP dependencies
 * Bug: "Reformat build file when dependency is added" doesn't seem to work
 * Bug: Package Search IllegalArgumentException 'gwt-lib' is not a valid Maven type value

## Version 1.0.845-eap (28 Jan 2020)

 * Improvement: add support for IntelliJ 2020.1 EAP
 * Improvement: minor copy improvements

## Version 1.0.838-eap (14 Jan 2020)

 * Improvement: in a plain project without Maven/Gradle, the package search tool window is now no longer available
 * Cosmetics: rename "Install" to "Add to Project"
 * Cosmetics: normalize package description text indentations
 * Cosmetics: normalize GitHub repository links in package description
 * Cosmetics: remove useless tooltips in packages list
 * Bug: the package search was not always focused after using the "Add package" action
 * Bug: sometimes clicking the GitHub/SCM link for a dependency could result in an error

## Version 1.0.826-eap (31 Dec 2019)

 * Improvement: added "only stable" filter to the plugin, which applies to searches and updates
 * Improvement: don't integrate "Package" in Generate menu for non-Gradle/non-POM files
 * Cosmetics: show indication on plugin that it's not available while the project is syncing
 * Cosmetics: wrap authors in package details for long lists

## Version 1.0.815-eap (22 Nov 2019)

 * Bug: the plugin was not able to handle default (missing) scope for dependencies in Maven POMs
 * Bug: the plugin was not able to handle non-ascii configuration names in Gradle build scripts

## Version 1.0.807-eap (19 Nov 2019)

 * Bug: plugin crashes when a Maven POM contains invalid/unparseable XML

## Version 1.0.804-eap (13 Nov 2019)

 * Bug: fix packages list rendering broken when searching in latest 2019.3 build
 * Bug: fix package search plugin adds `package` entry in `new` dialog

## Version 1.0.799-eap (13 Nov 2019)

 * Bug: fix packages list rendering broken when searching in latest 2019.3 build
 * Bug: fix package search plugin adds `package` entry in `new` dialog

## Version 1.0.785-eap (7 Nov 2019)

 * Bug: fix "Choose Destination Directory" dialog showing when right-clicking and multiple directories
   are selected in Project View ([PKGS-353](https://youtrack.jetbrains.com/issue/PKGS-353))
 * Bug: fix potential freeze when using Find Action
 * Bug: dependencies followed by comments were not picked up correctly in Gradle files
   ([PKGS-354](https://youtrack.jetbrains.com/issue/PKGS-354))
 * Improvement: attach additional info to errors when the Gradle build script parser fails, to help troubleshoot
   — please do submit reports from the IDE with the additional attachments if you can!

## Version 1.0.763-eap (21 Oct 2019)

 * First public EAP release
 * Bug: plugin crashes when using K&R braces style in Gradle files
 * Bug: fix potential crash in 19.1-based IDEs

## Version 0.11.741 (11 Oct 2019)

 * Improvement: Update default configurations scope list for Gradle projects
 * Bug: Removing a dependency and reimporting makes plugin get in a weird UI state
 * Bug: Typing a custom configuration and clicking the + button adds it as the default configuration

## Version 0.11.734 (10 Oct 2019)

 * Improvement: Works with IntelliJ 2019.3 EAP
 * Improvement: Use unified API for search and version info
 * Improvement: Fetching of version info and metadata performance improvement
 * Improvement: Can exclude results with only non-stable versions by adding `/onlyStable:true`
   to the search query
 * Improvement: The plugin looks better on dark themes, such as Darcula
 * Bug: Updating Maven dependencies with implicit scope failed

## Version 0.11.542 (12 Aug 2019)

 * Bug: Update All link sometimes displayed incorrectly

## Version 0.11.539 (12 Aug 2019)

 * Updates to new UI
   * Improvement: LaF consistent with IntelliJ plugin manager UI
   * Improvement: Terminology consistent with IntelliJ plugin manager UI
 * Bug: Custom Gradle configuration when installing package is supported
 * Improvement: Plugin startup performance

## Version 0.11.465 (17 Jul 2019)

 * Updates to new UI
   * Feature: Tool window can now be scoped to All Modules or a specific module
   * Feature: Upgrade All action in the tool window toolbar
   * Bug: Packages that have been installed in multiple configurations are now rendered correctly
   * Bug: Long module names now display a tooltip on hover
   * Improvement: Improved stability when network connectivity fails

## Version 0.11.452 (15 Jul 2019)

 * Feature: Brand new UI!
   * Now a panel rather than a dialog
   * See installed dependencies, add new ones and update outdated ones
   * At a glance view of all project modules
 * Feature: New option to disable update checks
 * Improvement: Under-the-hood changes to future-proof the code
 * Compatible with IDEA 2019.1–2019.2

## Version 0.11.305 (11 Jun 2019)

 * Improvement: Improve dialog UI behaviour when resizing
 * Improvement: Improve focus handling in dialog and add keyboard shortcuts
 * Feature: Add link to StackOverflow tags

## Version 0.11.302 (5 Jun 2019)

 * Bug: Fix crash when adding dependencies that are already declared

## Version 0.11.295 (4 Jun 2019)

 * Improvement: Minor under-the-hood improvements

## Version 0.11.292 (4 Jun 2019)

 * Feature: Initial snapshot release for private EAP
 * Compatible with IDEA 2018.3.1–2019.2 (EAP)
