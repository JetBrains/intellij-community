// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { IconSet } from "@jetbrains/intellij-webview"
import agentIcon from "../../icons/acpChatAgent.svg"
import agentDarkIcon from "../../icons/acpChatAgent_dark.svg"
import brainIcon from "../../icons/acpChatBrain.svg"
import brainDarkIcon from "../../icons/acpChatBrain_dark.svg"
import debugIcon from "../../icons/acpChatDebug.svg"
import debugDarkIcon from "../../icons/acpChatDebug_dark.svg"
import effortIcon from "../../icons/acpChatEffort.svg"
import effortDarkIcon from "../../icons/acpChatEffort_dark.svg"
import junieIcon from "../../icons/acpChatJunie.svg"
import modeIcon from "../../icons/acpChatMode.svg"
import modeDarkIcon from "../../icons/acpChatMode_dark.svg"
import processorIcon from "../../icons/acpChatProcessor.svg"
import processorDarkIcon from "../../icons/acpChatProcessor_dark.svg"
import sendIcon from "../../icons/acpChatSend.svg"
import sendDarkIcon from "../../icons/acpChatSend_dark.svg"
import shieldIcon from "../../icons/acpChatShield.svg"
import shieldDarkIcon from "../../icons/acpChatShield_dark.svg"
import toggleIcon from "../../icons/acpChatToggle.svg"
import toggleDarkIcon from "../../icons/acpChatToggle_dark.svg"

export type AcpControlIconKind = "mode" | "model" | "effort" | "shield" | "debug" | "brain" | "toggle"

export const ACP_CHAT_ICONS = IconSet.define("AcpChatIcons")
const ACP_CHAT_ICON_RESOURCE_ROOT = "webview/views/acp-chat/assets"

export const AGENT_ICON_PATH = iconResourcePath(agentIcon, "acpChatAgent.svg")
export const JUNIE_ICON_PATH = iconResourcePath(junieIcon, "acpChatJunie.svg")
export const SEND_ICON_PATH = iconResourcePath(sendIcon, "acpChatSend.svg")

const CONTROL_ICON_PATHS: Record<AcpControlIconKind, string> = {
  mode: iconResourcePath(modeIcon, "acpChatMode.svg"),
  model: iconResourcePath(processorIcon, "acpChatProcessor.svg"),
  effort: iconResourcePath(effortIcon, "acpChatEffort.svg"),
  shield: iconResourcePath(shieldIcon, "acpChatShield.svg"),
  debug: iconResourcePath(debugIcon, "acpChatDebug.svg"),
  brain: iconResourcePath(brainIcon, "acpChatBrain.svg"),
  toggle: iconResourcePath(toggleIcon, "acpChatToggle.svg"),
}

keepBundledIconAssets([
  iconResourcePath(agentDarkIcon, "acpChatAgent_dark.svg"),
  iconResourcePath(brainDarkIcon, "acpChatBrain_dark.svg"),
  iconResourcePath(debugDarkIcon, "acpChatDebug_dark.svg"),
  iconResourcePath(effortDarkIcon, "acpChatEffort_dark.svg"),
  iconResourcePath(modeDarkIcon, "acpChatMode_dark.svg"),
  iconResourcePath(processorDarkIcon, "acpChatProcessor_dark.svg"),
  iconResourcePath(sendDarkIcon, "acpChatSend_dark.svg"),
  iconResourcePath(shieldDarkIcon, "acpChatShield_dark.svg"),
  iconResourcePath(toggleDarkIcon, "acpChatToggle_dark.svg"),
])

export function acpControlIconPath(kind: AcpControlIconKind): string {
  return CONTROL_ICON_PATHS[kind]
}

export function acpIconSrc(path: string): string {
  return ACP_CHAT_ICONS.src(path)
}

function iconResourcePath(assetUrl: string, fileName: string): string {
  const cleanAssetUrl = assetUrl.split("?", 1)[0]
  const assetsPathStart = cleanAssetUrl.lastIndexOf("/assets/")
  if (assetsPathStart >= 0) return `${ACP_CHAT_ICON_RESOURCE_ROOT}/${cleanAssetUrl.substring(assetsPathStart + "/assets/".length)}`
  return `${ACP_CHAT_ICON_RESOURCE_ROOT}/${fileName}`
}

function keepBundledIconAssets(paths: readonly string[]): void {
  if (paths.length === 0) throw new Error("ACP chat icon assets are missing")
}
