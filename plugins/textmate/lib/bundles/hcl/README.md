# HCL Extension for Visual Studio Code

The [HashiCorp HCL Extension](https://marketplace.visualstudio.com/items?itemName=hashicorp.hcl) for Visual Studio Code (VS Code) adds syntax highlighting for [HCL](https://www.hashicorp.com) files.

## Quick Start

1. Install the extension from the [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=hashicorp.hcl)
1. Open your desired workspace and/or the root folder containing your [HCL](https://github.com/hashicorp/hcl#hcl) files.

## Features

This extension provides [HCL](https://github.com/hashicorp/hcl#hcl) syntax highlighting for files that use `.hcl` as the file extension, for example:

- [Nomad job specification](https://www.nomadproject.io/docs/job-specification/hcl2)
- [Packer template](https://www.packer.io/docs/templates/hcl_templates)
- [Waypoint project](https://www.waypointproject.io/docs/waypoint-hcl)

## Usage

The [HashiCorp HCL Extension](https://marketplace.visualstudio.com/items?itemName=hashicorp.HCL) is a grammar only extension targeted to provide HCL syntax highlighting for files not already accounted for by a more specific product-focused extension. For example, Terraform syntax highlighting is already provided by the official [HashiCorp Terraform Extension](https://marketplace.visualstudio.com/items?itemName=hashicorp.terraform) for VS Code.

Current Terraform users can install the HCL extension alongside the Terraform Extension and find that they can use HCL files without conflict. Users can also install the HCL Extension separately from the Terraform Extension if they only want HCL support.

Read more about HCL at https://github.com/hashicorp/hcl.

## Telemetry

We use telemetry to collect data about opened file _types_ which are implied from file names (such as `terraform` for `*.tf` or `packer` for `*.pkr.hcl`). This helps us better understand usage and make better informed product decisions. You can configure VS Code to send all telemetry, or turn it off entirely by [configuring](https://code.visualstudio.com/docs/getstarted/telemetry#_disable-telemetry-reporting) `"telemetry.telemetryLevel"` to your desired value (e.g. `"off"` or `"all"`). You can also [monitor what's being sent](https://code.visualstudio.com/docs/getstarted/telemetry#_output-channel-for-telemetry-events) in your logs.

## Credits

 - We thank [William Holroyd](https://github.com/wholroyd) for creation and past maintenance of [`wholroyd.HCL`](https://marketplace.visualstudio.com/items?itemName=wholroyd.HCL) VS Code extension and for agreeing to the extension namespace transfer to make the transition conflict-less and easy for existing users
