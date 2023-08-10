# Terraform Extension for Visual Studio Code

The HashiCorp [Terraform Extension for Visual Studio Code (VS Code)](https://marketplace.visualstudio.com/items?itemName=HashiCorp.terraform) with the [Terraform Language Server](https://github.com/hashicorp/terraform-ls) adds editing features for [Terraform](https://www.terraform.io) files such as syntax highlighting, IntelliSense, code navigation, code formatting, module explorer and much more!

## Quick Start

Get started writing Terraform configurations with VS Code in three steps:

- **Step 1:** If you haven't done so already, install [Terraform](https://www.terraform.io/downloads)

- **Step 2:** Install the [Terraform Extension for VS Code](https://marketplace.visualstudio.com/items?itemName=HashiCorp.terraform).

- **Step 3:** To activate the extension, open any folder or VS Code workspace containing Terraform files. Once activated, the Terraform language indicator will appear in the bottom right corner of the window.

New to Terraform? Read the [Terraform Learning guides](https://learn.hashicorp.com/terraform)

See [Usage](#usage) for more detailed getting started information.

Read the [Troubleshooting Guide](#troubleshooting) for answers to common questions.

## Features

- [IntelliSense](#intellisense-and-autocomplete) Edit your code with auto-completion of providers, resource names, data sources, attributes and more
- [Syntax validation](#syntax-validation) Diagnostics using `terraform validate` provide inline error checking
- [Syntax highlighting](#syntax-highlighting) Highlighting syntax from Terraform 0.12 to 1.X
- [Code Navigation](#code-navigation) Navigate through your codebase with Go to Definition and Symbol support
- [Code Formatting](#code-formatting) Format your code with `terraform fmt` automatically
- [Code Snippets](#code-snippets) Shortcuts for commmon snippets like `for_each` and `variable`
- [Terraform Module Explorer](#terraform-module-and-provider-explorer) View all modules and providers referenced in the currently open document.
- [Terraform commands](#terraform-commands) Directly execute commands like `terraform init` or `terraform plan` from the VS Code Command Palette.

### IntelliSense and Autocomplete

IntelliSense is a general term for a variety of code editing features including: code completion, parameter info, quick info, and member lists. IntelliSense features are sometimes called by other names such as autcomplete, code completion, and code hinting.

For Terraform constructs like resource and data, labels, blocks and attributes are auto completed both at the root of the document and inside other blocks. This also works for Terraform modules that are installed in the workspace, attributes and other constructs are autocompleted.

> **Note:** If there are syntax errors present in the document upon opening, intellisense may not provide all completions. Please fix the errors and reload the document and intellisense will return. See [hcl-lang#57](https://github.com/hashicorp/hcl-lang/issues/57) for more information.

Invoking intellisense is performed through the [keyboard combination](https://code.visualstudio.com/docs/getstarted/keybindings) for your platform and the results depend on where the cursor is placed.

If the cursor is at the beginning of a line and no other characters are present, then a list of constructs like `data`, `provider`, `resource`, etc are shown.

![](docs/intellisense1.png)

If inside a set of quotes or inside a block, the extension provides context specific completions appropriate for the location. For example, inside a `resource` block attributes for a given provider are listed.

![](docs/intellisense2.png)

Combining `editor.suggest.preview` with the [pre-fill required fields](#code-completion) setting will provide inline snippet suggestions for blocks of code:

![](docs/intellisense3.png)

Completing the snippet allows you to tab complete through each attribute and block.

### Syntax Validation

The extension provides validation through [`terraform validate`](https://www.terraform.io/cli/commands/validate). This verifies whether a configuration is syntactically valid and internally consistent, regardless of any provided variables or existing state. It is thus primarily useful for general verification of reusable modules, including correctness of attribute names and value types.

### Syntax Highlighting

Terraform syntax highlighting recognizes language constructs from Terraform version 0.12 to 1.X. Terraform providers, modules, variables and other high-level constructs are recognized, as well as more complex code statements like `for` loops, conditional expressions, and other complex expressions.

![](docs/syntax.png)

Some language constructs will highlight differently for older versions of Terraform that are incompatible with newer ways of expressing Terraform code. In these cases we lean toward ensuring the latest version of Terraform displays correctly and do our best with older versions.

### Code Navigation

While editing, you can right-click different identifiers to take advantage of several convenient commands

- `Go to Definition` (`F12`) navigates to the code that defines the construct where your cursor is. This command is helpful when you're working with Terraform modules and variables defined in other files than the currently opened document.
- `Peek Definition` (`Alt+F12`) displays the relevant code snippet for the construct where your cursor is directly in the current editor instead of navigating to another file.
- `Go to Declaration` navigates to the place where the variable or other construct is declared.
- `Peek Declaration` displays the declaration directly inside the current editor.

### Code Formatting

This extension utilizes [`terraform fmt`](https://www.terraform.io/cli/commands/fmt) to rewrite an open document to a canonical format and style. This command applies a subset of the [Terraform language style conventions](https://www.terraform.io/language/syntax/style), along with other minor adjustments for readability.


See the [Formatting](#formatting) Configuration section for information on how to configure this feature.

### Code Snippets

The extension provides several snippets to accelerate adding Terraform code to your configuration files:

- `fore` - For Each
- `module` - Module
- `output` - Output
- `provisioner` - Provisioner
- `vare` - Empty variable
- `varm` - Map Variable

### Terraform Module and Provider Explorer

List Terraform modules used in the current open document in the Explorer Pane, or drag to the Side Bar pane for an expanded view.

Each item shows an icon indicating where the module comes from (local filesystem, git repository, or Terraform Registry).

![](docs/module_calls.png)

If the module comes from the Terraform Registry, a link to open the documentation in a browser is provided.

![](docs/module_calls_doc_link.png)

List Terraform providers used in the current open document in the Explorer Pane, or drag to the Side Bar pane for an expanded view.

![](docs/module_providers.png)

### Terraform Commands

The extension provides access to several Terraform commands through the Command Palette:

- Terraform: init
- Terraform: init current folder
- Terraform: validate
- Terraform: plan

## Requirements

The Terraform VS Code extension bundles the [Terraform Language Server](https://github.com/hashicorp/terraform-ls) and is a self-contained install.

The extension does require the following to be installed before use:

- VS Code v1.75 or greater
- Terraform v0.12 or greater

## Platform Support

The extension should work anywhere VS Code itself and Terraform 0.12 or higher is supported. Our test matrix includes the following:

- Windows Server 2019 with Terraform v1.1
- macOS 10.15 with Terraform v1.1
- Ubuntu 20.04 with Terraform v1.1

Intellisense, error checking and other language features are supported for Terraform v0.12 and greater.

Syntax highlighting targets Terraform v1.0 and greater. Highlighting 0.12-0.15 configuration is done on a best effort basis.

## Usage

### VS Code Workspace support

It is a common pattern to have separate folders containing related Terraform configuration that are not contained under one root folder. For example, you have a main Terraform folder containing the configuration for a single application and several module folders containing encapsulated code for configuring different parts of component pieces. You could open each folder in a separate VS Code window, and bounce between each window to author your changes.

A better approach is to use [VS Code Workspaces](https://code.visualstudio.com/docs/editor/workspaces). Using our example above, open the main Terraform folder first, then use Add Folder to workspace to add the dependent module folders. A single VS Code window is used and all Terraform files are available to author your changes. This uses a single terraform-ls process that has an understanding of your entire project, allowing you to use features like `Go to Symbol` and `Reference counts` across your project.

### Single file support

Opening a single Terraform configuration file inside VS Code is currently not supported. We see this approach most commonly attempted by users of terminal editors like vim, where it is common to edit a single file at a time.

The Terraform VS Code extension works best when it has the full context of a Terraform project where it can parse the referenced files and provide the expected advanced language features.

The recommended workflow is to instead open the containing folder for the desired Terraform file inside a single VS Code editor window, then navigate to the desired file. This seems counter-intuitive when you only want to edit a single file, but this allows the extension to understand the Terraform setup you are using and provide accurate and helpful intellisense, error checking, and other language features.

### Refresh Intellisense

The extension will pick up new schema for Terraform providers you reference in your configuration files automatically whenever anything changes inside `.terraform`.

To provide the extension with an up-to-date schema for the Terraform providers used in your configuration:

1. Open any folder or VS Code workspace containing Terraform files. 
1. Open the Command Palette and run `Terraform: init current folder` or perform a `terraform init` from the terminal.

### Remote Extension support

The Visual Studio Code [Remote - WSL extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-wsl) lets you use the Windows Subsystem for Linux (WSL) as your full-time development environment right from VS Code. You can author Terraform configuration files in a Linux-based environment, use Linux-specific toolchains and utilities from the comfort of Windows.

The Remote WSL extension runs the [HashiCorp Extension](https://marketplace.visualstudio.com/items?itemName=HashiCorp.terraform) and other extensions directly in WSL so you can edit files located in WSL or the mounted Windows filesystem (for example /mnt/c) without worrying about pathing issues, binary compatibility, or other cross-OS challenges.

For a detailed walkthrough for how to get started using WSL and VS Code, see https://code.visualstudio.com/docs/remote/wsl-tutorial.

## Configuration

The extension does not require any initial configuration and should work out of the box. To take advantage of additional VS Code features or experimental extension features you can configure settings to customize behavior.

This extension offers several configuration options. To modify these open the [VS Code Settings Editor](https://code.visualstudio.com/docs/getstarted/settings#_settings-editor) in the UI or JSON view for [user and workspace level](https://code.visualstudio.com/docs/getstarted/settings#_creating-user-and-workspace-settings) settings, [scope your settings by language](https://code.visualstudio.com/docs/getstarted/settings#_languagespecific-editor-settings), or alternatively modify the `.vscode/settings.json` file in the root of your working directory.

### Code Completion

An experimental option can be enabled to prefill required fields when completing Terraform blocks with the following setting:

```json
"terraform.experimentalFeatures.prefillRequiredFields": true
```

For example, choosing `aws_alb_listener` in the following block inserts a snippet in the current line with the `resource` block entirely filled out, containing tab stops to fill in the required values.

![](docs/pre-fill.png)

Combine this with `editor.suggest.preview` and the editor will provide inline snippet suggestions for blocks of code:

![](docs/intellisense3.png)

Completing the snippet allows you to tab complete through each attribute and block.

### Code Lens

Display reference counts above top level blocks and attributes

```json
"terraform.codelens.referenceCount": true
```

![](docs/code_lens.png)

> **Note:** This feature impacts extension performance when opening folders with many modules present. If you experience slowness or high CPU utilization, open a smaller set of folders or disable this setting.

### Formatting

To enable automatic formatting, it is recommended that the following be added to the extension settings for the Terraform extension:

```json
"[terraform]": {
  "editor.defaultFormatter": "hashicorp.terraform",
  "editor.formatOnSave": true,
  "editor.formatOnSaveMode": "file"
}
"[terraform-vars]": {
  "editor.defaultFormatter": "hashicorp.terraform",
  "editor.formatOnSave": true,
  "editor.formatOnSaveMode": "file"
}
```

> It is recommended to set `editor.defaultFormatter` to ensure that VS Code knows which extension to use to format your files. It is possible to have more than one extension installed which claim a capability to format Terraform files.

When using the `editor.formatOnSaveMode` setting, only `file` is currently supported. The `modifications` or `modificationsIfAvailable` settings [use the currently configured SCM](https://code.visualstudio.com/updates/v1_49#_only-format-modified-text) to detect file line ranges that have changed and send those ranges to the formatter. The `file` setting works because `terraform fmt` was originally designed for formatting an entire file, not ranges. If you don't have an SCM enabled for the files you are editing, `modifications` won't work at all. The `modificationsIfAvailable` setting will fall back to `file` if there is no SCM and will appear to work sometimes.

If you want to use `editor.codeActionsOnSave` with `editor.formatOnSave` to automatically format Terraform files, use the following configuration:

```json
"editor.formatOnSave": true,
"[terraform]": {
  "editor.defaultFormatter": "hashicorp.terraform",
  "editor.formatOnSave": false,
  "editor.codeActionsOnSave": {
    "source.formatAll.terraform": true
  },
},
"[terraform-vars]": {
  "editor.defaultFormatter": "hashicorp.terraform",
  "editor.formatOnSave": false,
  "editor.codeActionsOnSave": {
    "source.formatAll.terraform": true
  },
}
```

This will keep the global `editor.formatOnSave` for other languages you use, and configure the Terraform extension to only format during a `codeAction` sweep.

> **Note**: Ensure that the terraform binary is present in the environment `PATH` variable. If the terraform binary cannot be found, formatting will silently fail.

### Validation

An experimental validate-on-save option can be enabled with the following setting:

```json
"terraform.experimentalFeatures.validateOnSave": true
```

This will create diagnostics for any elements that fail validation. You can also run `terraform validate` by issuing the `Terraform: validate` in the command palette.

### Multiple Workspaces

If you have multiple root modules in your workspace, you can configure the language server settings to identify them. Edit this through the VSCode Settings UI or add a `.vscode/settings.json` file using the following template:

```json
"terraform.languageServer.rootModules": [
  "/module1",
  "/module2"
]
```

If you want to automatically search root modules in your workspace and exclude some folders, you can configure the language server settings to identify them.

```json
"terraform.languageServer.excludeRootModules": [
  "/module3",
  "/module4"
]
```

If you want to automatically ignore certain directories when terraform-ls indexes files, add the folder names to this setting:

```json
 "terraform.languageServer.ignoreDirectoryNames": [
   "folder1",
   "folder2"
 ]
```

### Terraform command options

You can configure the path to the Terraform binary used by terraform-ls to perform operations inside the editor by configuring this setting:

```json
"terraform.languageServer.terraform.path": "C:/some/folder/path"
```

You can override the Terraform execution timeout by configuring this setting:

```json
"terraform.languageServer.terraform.timeout": "30"
```

You can set the path Terraform logs (`TF_LOG_PATH`) by configuring this setting:

```json
"terraform.languageServer.terraform.logFilePath": "C:/some/folder/path/log-{{varname}}.log"
```

Supports variables (e.g. timestamp, pid, ppid) via Go template syntax `{{varname}}`

### Telemetry

We use telemetry to send error reports to our team, so we can respond more effectively. You can configure VS Code to send all telemetry, just crash telemetry, just errors or turn it off entirely by [configuring](https://code.visualstudio.com/docs/getstarted/telemetry#_disable-telemetry-reporting) `"telemetry.telemetryLevel"` to your desired value. You can also [monitor what's being sent](https://code.visualstudio.com/docs/getstarted/telemetry#_output-channel-for-telemetry-events) in your logs.

## Known Issues

- If there are syntax errors present in the document upon opening, intellisense may not provide all completions. Run `Terraform: validate` and fix validation errors, then reload the document and intellisense will work again. Potential solutions for this are being investigated in See [hcl-lang#57](https://github.com/hashicorp/hcl-lang/issues/57) for more information.
- Completion inside incomplete blocks, such as `resource "here` (without the closing quote and braces) is not supported. You can complete the 1st level blocks though and that will automatically trigger subsequent completion for e.g. resource types. See [terraform-ls#57](https://github.com/hashicorp/terraform-ls/issues/57) for more information.
- A number of different folder configurations (specifically when your root module is not a parent to any submodules) are not yet supported. More information available in ([terraform-ls#32](https://github.com/hashicorp/terraform-ls/issues/32#issuecomment-649707345))

### Terraform 0.11 compatibility

If you are using a Terraform version prior to 0.12.0, you can install the pre-transfer 1.4.0 version of this extension by following the instructions in the [pin version section](#pin-to-a-specific-version-of-the-extension).

The configuration has changed from 1.4.0 to v2.X. If you are having issues with the Language Server starting, you can reset the configuration to the following:

```json
"terraform.languageServer.enable": true,
"terraform.languageServer.args": ["serve"]
```

## Troubleshooting

- If you have a question about how to accomplish something with the extension, please ask on the [Terraform Editor Discuss site](https://discuss.hashicorp.com/c/terraform-core/terraform-editor-integrations/46)
- If you come across a problem with the extension, please file an [issue](https://github.com/hashicorp/vscode-terraform/issues/new/choose).
- If someone has already filed an issue that encompasses your feedback, please leave a 👍/👎 reaction on the issue
- Contributions are always welcome! Please see our [contributing guide](https://github.com/hashicorp/vscode-terraform/issues/new?assignees=&labels=enhancement&template=feature_request.md) for more details
- If you're interested in the development of the extension, you can read about our [development process](DEVELOPMENT.md)

### Settings Migration

Read more about [changes in settings options introduced in v2.24.0](./docs/settings-migration.md).

### Generate a bug report

Experience a problem? You can have VS Code open a Github issue in our repo with all the information filled out for you. Open the Command Palette and invoke `Terraform: Generate Bug Report`. This will inspect the VS Code version, the Terraform extension version, the terraform-ls version and the list of installed extensions and open a browser window with GitHub loaded. You can then inspect the information provided, edit if desired, and submit the issue.

### Reload the extension

If you haven't seen the Problems Pane update in awhile, or hover and intellisense doesn't seem to showing up, you might not know what to do. Sometimes the Terraform extension can experience problems which cause the editor to not respond. The extension has a way of [reporting the problem](#generate-a-bug-report), but there is something you can do to get right back to working after reporting the problem: reload the Terraform extension.

You can reload the Terraform extension by opening the command palette and starting to type `Reload`. A list of commands will appear, select `Reload Window`. This will reload the Visual Studio Code window without closing down the entire editor, and without losing any work currently open in the editor.

### Pin to a specific version of the extension

If you wish to install a specific version of the extension, you can choose 'Install Another version' option in the Extensions pane. This will bring up a list of prior versions for the selected extension. Choose the version you want to install from the list.

![](docs/pin_version.png)

## Code of Conduct

HashiCorp Community Guidelines apply to you when interacting with the community here on GitHub and contributing code to this repository.

Please read the full text at https://www.hashicorp.com/community-guidelines

## Contributing

We are an open source project on GitHub and would enjoy your contributions! Consult our [development guide](DEVELOPMENT.md) for steps on how to get started. Please [open a new issue](https://github.com/hashicorp/terraform-vscode/issues) before working on a PR that requires significant effort. This will allow us to make sure the work is in line with the project's goals.

## Release History

**v2.0.0** is the first official release from HashiCorp, prior releases were by [Mikael Olenfalk](https://github.com/mauve).

The 2.0.0 release integrates a new [Language Server package from HashiCorp](https://github.com/hashicorp/terraform-ls). The extension will install and upgrade terraform-ls to continue to add new functionality around code completion and formatting. See the [terraform-ls CHANGELOG](https://github.com/hashicorp/terraform-ls/blob/main/CHANGELOG.md) for details.

In addition, this new version brings the syntax highlighting up to date with all HCL2 features, as needed for Terraform 0.12 and above.

> **Configuration Changes** Please note that in 2.x, the configuration differs from 1.4.0, see [Known Issues](#known-issues) for more information.

See the [CHANGELOG](https://github.com/hashicorp/vscode-terraform/blob/main/CHANGELOG.md) for more detailed release notes.

## Credits

- [Mikael Olenfalk](https://github.com/mauve) - creating and supporting the [vscode-terraform](https://github.com/mauve/vscode-terraform) extension, which was used as a starting point and inspiration for this extension.
