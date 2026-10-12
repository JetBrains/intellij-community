packer {
  required_version = "= 1.16.0"

  required_plugins {
    tart = {
      version = "= 1.21.0"
      source  = "github.com/cirruslabs/tart"
    }
  }
}

variable "base_vm" {
  type = string
}

variable "golden_vm" {
  type = string
}

variable "macos_version" {
  type = string
}

variable "junie_version" {
  type = string
}

variable "node_major" {
  type = number
}

# The host path of the lane's macOS guest agent, whose `provision-image` and `validate-image` verbs are the
# two provisioners that turn this base into the golden image and then prove the result.
#
# **This variable is the Bazel dependency the image pipeline did not have**, and it binds `image validate` as
# much as `image build`: Packer's `file` provisioner stats its source during `packer validate`, so no
# configuration check can skip the binary. `scripts/lib.sh` owns the resolution and states what that costs the
# operator who has to satisfy it. Why the pipeline took the dependency on, and why the image still contains no
# Bazel, is ADR 0108 - `community/tools/vm/docs/decisions/0108-the-guest-half-of-the-image-pipeline-is-go.md`.
variable "guest_agent_binary" {
  type = string
}

locals {
  # Where the agent lands in the guest: named once, for the upload and both invocations.
  guest_agent = "/tmp/air-vm-guest-agent"

  # The three pins both image verbs are told, written once.
  #
  # One list and not one per verb, and that is the whole reason this is a local rather than two argv strings.
  # The pair's entire value is that `validate-image` checks `provision-image`'s work, so two lists that can be
  # edited apart are two chances to tell the validator the same wrong version the provisioner installed - it
  # then passes while proving nothing. The two `environment_vars` blocks this replaces were exactly that
  # duplicate.
  #
  # Each value is quoted so that an empty variable arrives as an empty argument, which the guest refuses by
  # name: `--macos-version was given an empty value`. Unquoted, the empty value vanishes in word splitting and
  # every later flag pairs with its neighbour, so the guest still refuses - it reads the flags in pairs - but
  # it blames the token that landed in a flag's place: `validate-image does not accept "24"`. Both fail the
  # build; only the quoted one says which pin is empty.
  image_versions = join(" ", [
    "--macos-version '${var.macos_version}'",
    "--node-major '${var.node_major}'",
    "--junie-version '${var.junie_version}'",
  ])
}

source "tart-cli" "air_macos" {
  vm_base_name = var.base_vm
  vm_name      = var.golden_vm
  cpu_count    = 8
  memory_gb    = 32
  disk_size_gb = 50
  display      = "1920x1080"
  headless     = true

  # These are the public credentials of the Cirrus base image. No private
  # credential is passed to or persisted by this template.
  ssh_username = "admin"
  ssh_password = "admin"
  ssh_timeout  = "5m"

  # base_vm is a local, digest-gated APFS snapshot prepared by fetch-base.sh.
  # It is deliberately never refreshed by Packer.
  always_pull    = false
  run_extra_args = ["--no-audio", "--no-clipboard"]
}

build {
  sources = ["source.tart-cli.air_macos"]

  provisioner "file" {
    source      = "scripts/init-worker-storage.sh"
    destination = "/tmp/air-init-worker-storage.sh"
  }

  provisioner "file" {
    source      = "scripts/audit-public-image.sh"
    destination = "/tmp/air-audit-public-image.sh"
  }

  provisioner "file" {
    source      = "scripts/ensure-ssh-host-keys.sh"
    destination = "/tmp/air-ensure-ssh-host-keys.sh"
  }

  # Uploaded once for both verbs. Nothing between them reboots the guest and nothing clears `/tmp`, so a
  # second `file` block would be a second name for one file - and another 3.8 MB (the measured darwin_arm64
  # build) over the same SSH connection for nothing. So: uploaded before its first use, removed after its
  # last, which is why the removal sits at the end of the `validate-image` block below rather than here.
  #
  # `/tmp` is on the image, so a provisioning input left there is residue in something publishable. That is
  # the same reason `provision-image` deletes the three scripts above once it has installed them, and the
  # reason this one cannot delete itself: the verb that would do it is not the last verb to run it.
  provisioner "file" {
    source      = var.guest_agent_binary
    destination = local.guest_agent
  }

  # The `chmod` is here, in the first use, and not in both blocks. What Bazel writes is mode 0555 and Packer's
  # `file` provisioner uploads the source's own permission bits, so the upload already arrives executable
  # today; this line is what keeps that from being load bearing.
  provisioner "shell" {
    inline = [
      "chmod +x ${local.guest_agent}",
      "${local.guest_agent} provision-image ${local.image_versions}",
    ]
  }

  provisioner "shell" {
    script = "scripts/sanitize-public-image.sh"
  }

  provisioner "shell" {
    inline = [
      "${local.guest_agent} validate-image ${local.image_versions}",
      "rm -f ${local.guest_agent}",
    ]
  }

  provisioner "shell" {
    script = "scripts/seal-public-image.sh"
  }
}
