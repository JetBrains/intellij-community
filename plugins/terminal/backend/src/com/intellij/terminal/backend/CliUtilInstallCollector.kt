package com.intellij.terminal.backend

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.util.EnvironmentScanner
import com.intellij.util.SystemProperties
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal class CliUtilInstallCollector : ApplicationUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("cli.tools", 1)

  @Suppress("SpellCheckingInspection")
  private val CLI_EXECUTABLES: List<String> = listOf(
    "aliyun",
    "amplify",
    "ansible",
    "argocd",
    "aws",
    "az",
    "bpftrace",
    "buildah",
    "bun",
    "cargo",
    "cdk",
    "cfn",
    "chaos-mesh",
    "chef",
    "cilium",
    "circleci",
    "code",
    "codium",
    "colima",
    "conftest",
    "consul",
    "crane",
    "crictl",
    "ctr",
    "datadog-agent",
    "devcontainer-cli",
    "devspace",
    "docker",
    "doctl",
    "doppler",
    "drone",
    "eb",
    "eksctl",
    "envoy",
    "etcdctl",
    "fleet",
    "flux",
    "fly",
    "gcloud",
    "gem",
    "git",
    "gitpod",
    "glab",
    "gradle",
    "grafana",
    "hadolint",
    "harbor",
    "hcloud",
    "helm",
    "helmfile",
    "hubble",
    "ibmcloud",
    "istioctl",
    "jenkins-cli",
    "jf",
    "jq",
    "k3d",
    "k3s",
    "k9s",
    "kibana",
    "kind",
    "kubeconform",
    "kubectl",
    "kubectx",
    "kubefwd",
    "kubens",
    "kubeseal",
    "kubetail",
    "kustomize",
    "lima",
    "localstack",
    "lxc",
    "lxd",
    "maven",
    "meshery",
    "microk8s",
    "minikube",
    "mirrord",
    "ncp",
    "nerdctl",
    "nerdctl", //Docker-compatible CLI for containerd.
    "newrelic",
    "ngrok",
    "nix",
    "nomad",
    "npm",
    "openstack",
    "otel",
    "pack", //CLI tool maintained by the CNB project to support the use of buildpacks.
    "packer",
    "pip",
    "pnpm",
    "podman",
    "poetry",
    "pre-commit",
    "promtool",
    "pulumi",
    "puppet",
    "quarkus",
    "rancher",
    "rke",
    "sam",
    "sdkman",
    "semgrep",
    "serverless",
    "sf",
    "shellcheck",
    "skaffold",
    "sops",
    "splunk",
    "st2",
    "stern",
    "tanzu",
    "telepresence",
    "terraform",
    "terraform-docs",
    "terragrunt",
    "terramate",
    "tflint",
    "tfsec",
    "tfswitch",
    "tilt",
    "tofu",
    "travis",
    "trivy",
    "uv",
    "vagrant",
    "vault",
    "velero",
    "werf",
    "yamllint",
    "yarn",
    "yc",
    "yq",

    "claude",
    "codex",
    "cursor",
    "junie",
    "opencode",
    "copilot",
  )

  private val TOOL_DISCOVERED: EventId1<String> = GROUP.registerEvent(
    "tool.discovered",
    EventFields.String("tool", CLI_EXECUTABLES)
  )

  private val KUBECONFIG_ID: String = "kubeconfig"

  private val CLAUDE_ID: String = ".claude"
  private val CODEX_ID: String = ".codex"
  private val COPILOT_ID: String = ".copilot"
  private val CURSOR_ID: String = ".cursor"

  private val CONFIG_EXISTS: EventId1<String> = GROUP.registerEvent(
    "config.exists",
    EventFields.String("config", listOf(KUBECONFIG_ID, CLAUDE_ID))
  )

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val pathNames = EnvironmentScanner.getPathNames()
    val metrics = CLI_EXECUTABLES
      .filter { EnvironmentScanner.hasToolInLocalPath(pathNames, it) }
      .map { TOOL_DISCOVERED.metric(it) }
      .toMutableList()

    if (userPathExists(Path.of(".kube", "config"))) {
      metrics.add(CONFIG_EXISTS.metric(KUBECONFIG_ID))
    }

    if (userPathExists(Path.of(".claude"))) {
      metrics.add(CONFIG_EXISTS.metric(CLAUDE_ID))
    }
    if (userPathExists(Path.of(".codex"))) {
      metrics.add(CONFIG_EXISTS.metric(CODEX_ID))
    }
    if (userPathExists(Path.of(".copilot"))) {
      metrics.add(CONFIG_EXISTS.metric(COPILOT_ID))
    }
    if (userPathExists(Path.of(".cursor"))) {
      metrics.add(CONFIG_EXISTS.metric(CURSOR_ID))
    }

    return metrics.toSet()
  }

  private fun userPathExists(subPath: Path): Boolean {
    val fs = FileSystems.getDefault()
    val configPath = try {
      fs.getPath(SystemProperties.getUserHome()).resolve(subPath)
    }
    catch (_: InvalidPathException) {
      null
    }

    return configPath != null && try {
      Files.exists(configPath)
    }
    catch (_: Exception) {
      false
    }
  }
}