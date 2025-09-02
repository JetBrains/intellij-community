# Advance Kotlin Analyzer Version Script

[advance-analyzer-version.main.kts](advance-analyzer-version.main.kts) is supposed to automate the process of advancing the Kotlin compiler version in the monorepo.
The script helps update configuration files and related resources, ensures consistency, and integrates with version control.

## Prerequisites

- **Gradle:** Make sure `gradle` is available for updating libraries.
- **Network access:** The script queries build information from internal TeamCity, so the corporate network is required.
- **Kotlin scripting:** Make sure `kotlin` is available on your PATH (part of [Kotlin command line tools](https://kotlinlang.org/docs/command-line.html)) if you want to run the script directly from the terminal.

## Usage

### Bootstrap the analyzer version:

- Run `Bootstrap Kotlin Analyzer Version` run configuration
- From a terminal, run
```bash
./advance-analyzer-version.main.kts --bootstrap
``` 
or
```bash
kotlinc -script advance-analyzer-version.main.kts -- --bootstrap
```

### Advance the analyzer version:

- From IntelliJ IDEA (`Advance Kotlin Analyzer Version`)
  - Pass a version to advance to via the run configuration arguments
  - Or via changing [advance-analyzer-version.main.kts](advance-analyzer-version.main.kts)
    1. Open [advance-analyzer-version.main.kts](advance-analyzer-version.main.kts)
    2. Set `Configuration#NEW_VERSION` to a version to advance to 
    3. Run `Advance Kotlin Analyzer Version` run configuration
    4. Reset `Configuration#NEW_VERSION` to `null`
- From a terminal
```bash
./advance-analyzer-version.main.kts <version-to-advance-to>
``` 
or
```bash
kotlinc -script advance-analyzer-version.main.kts <version-to-advance-to>
```

## What the Script Does

1. **Reads the Current Version**  
   The script checks the current `kotlincVersion` from the [model.properties](resources/model.properties) file.

2. **Fetches Revision Information**  
   Obtains build revisions (commits) for both the current and target versions from TeamCity.

3. **Compares Versions**  
   Shows a GitHub compare link with the range of changes if there are any differences.

4. **Checks for Uncommitted Changes**  
   The script ensures there are no uncommitted changes in specific project files.

5. **Updates Version and Mode**  
   Updates the `kotlincVersion` and sets artifact mode as needed in the relevant properties.

6. **Runs the Gradle Task**  
   Updates libraries via `gradle run`.

7. **Commits Changes**  
   Stages and commits the updated files with a properly formatted commit message (including the compare link).

8. **Rollback on Failure**  
   If any critical step fails, the script attempts to revert the affected files.

## Troubleshooting

- **Permission Denied:** Make the script executable:
  ```bash
  chmod +x advance-analyzer-version.main.kts
  ```
- **Uncommitted Changes Detected:** Commit or discard changes in related files before running the script.
