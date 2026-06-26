// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { marked } from "marked"
import type { ClipboardEvent } from "react"
import {
  AssistantRuntimeProvider,
  AttachmentPrimitive,
  ComposerPrimitive,
  MessagePrimitive,
  ThreadPrimitive,
  useMessagePartText,
  useSmooth,
} from "@assistant-ui/react"
import { useAcpChat } from "../runtime/useAcpChat"
import { AgentSelector } from "./AgentSelector"
import { ApprovalPrompt } from "./ApprovalPrompt"
import { AuthPrompt } from "./AuthPrompt"
import { ModelPicker } from "./ModelPicker"
import { PlanView } from "./PlanView"
import { ThinkingBlock } from "./ThinkingBlock"
import { ToolCallCard } from "./ToolCallCard"

const SMOOTH_TEXT_OPTIONS = { drainMs: 250, maxCharIntervalMs: 5, minCommitMs: 33 }

export function ChatView() {
  const chat = useAcpChat()
  const attachmentsEnabled = chat.promptCapabilities.image || chat.promptCapabilities.embeddedContext
  const notifyOnUnsupportedImagePaste = (event: ClipboardEvent<HTMLTextAreaElement>) => {
    if (chat.promptCapabilities.image) return
    const files = Array.from(event.clipboardData.files)
    if (!files.some(file => file.type.startsWith("image/"))) return
    event.preventDefault()
    chat.notifyAttachmentCapabilitiesUnavailable()
  }
  return (
    <AssistantRuntimeProvider runtime={chat.runtime}>
      <div className="acpChatLayout">
        {chat.status ? <header className="acpChatHeader"><span className="acpChatStatus">{chat.status}</span></header> : null}
        <PlanView plan={chat.plan} />
        <ThreadPrimitive.Root className="acpThread">
          <ThreadPrimitive.Viewport className="acpThreadViewport">
            <ThreadPrimitive.Empty>
              <div className="acpEmpty">Select an agent and send a message to start.</div>
            </ThreadPrimitive.Empty>
            <ThreadPrimitive.Messages components={{ UserMessage, AssistantMessage }} />
          </ThreadPrimitive.Viewport>
          <div className="acpComposerShell">
            <ComposerPrimitive.Root className="acpComposer">
              <div className="acpComposerMain">
                <div className="acpAttachmentList acpComposerAttachments">
                  <ComposerPrimitive.Attachments>
                    {() => <AttachmentChip removable />}
                  </ComposerPrimitive.Attachments>
                </div>
                <ComposerPrimitive.Input className="acpComposerInput" placeholder="Message the agent…" onPaste={notifyOnUnsupportedImagePaste} />
              </div>
              {attachmentsEnabled ? (
                <ComposerPrimitive.AddAttachment className="acpComposerAttach" aria-label="Attach file" title="Attach file">
                  <AttachmentIcon />
                </ComposerPrimitive.AddAttachment>
              ) : (
                <button className="acpComposerAttach" type="button" aria-label="Attach file" title="Attach file" onClick={chat.notifyAttachmentCapabilitiesUnavailable}>
                  <AttachmentIcon />
                </button>
              )}
              <ComposerPrimitive.Send className="acpComposerSend">Send</ComposerPrimitive.Send>
            </ComposerPrimitive.Root>
            <div className="acpComposerToolbar">
              <AgentSelector
                agents={chat.agents}
                selectedAgentId={chat.selectedAgentId}
                starting={chat.starting}
                onSelect={chat.selectAgent}
              />
              <ModelPicker
                modes={chat.modes}
                configOptions={chat.configOptions}
                currentModeId={chat.currentModeId}
                disabled={chat.starting || chat.selectedAgentId == null}
                onSelectMode={chat.selectMode}
                onSelectConfigOption={chat.selectConfigOption}
              />
            </div>
          </div>
        </ThreadPrimitive.Root>
        {chat.permission ? <ApprovalPrompt permission={chat.permission} /> : null}
        {chat.auth ? <AuthPrompt auth={chat.auth} /> : null}
      </div>
    </AssistantRuntimeProvider>
  )
}

function UserMessage() {
  return (
    <div className="acpMsg acpMsgUser">
      <MessagePrimitive.Parts components={{ Text: PlainText }} />
      <div className="acpAttachmentList acpMessageAttachments">
        <MessagePrimitive.Attachments>
          {() => <AttachmentChip />}
        </MessagePrimitive.Attachments>
      </div>
    </div>
  )
}

function AssistantMessage() {
  return (
    <div className="acpMsg acpMsgAssistant">
      <MessagePrimitive.Parts
        components={{ Text: MarkdownText, Reasoning: ThinkingBlock, tools: { Fallback: ToolCallCard } }}
      />
    </div>
  )
}

function PlainText(props: any) {
  return <span className="acpText">{props?.text ?? ""}</span>
}

function AttachmentChip(props: { removable?: boolean }) {
  return (
    <AttachmentPrimitive.Root className="acpAttachmentChip">
      <AttachmentPrimitive.unstable_Thumb className="acpAttachmentThumb" />
      <span className="acpAttachmentName"><AttachmentPrimitive.Name /></span>
      {props.removable ? (
        <AttachmentPrimitive.Remove className="acpAttachmentRemove" aria-label="Remove attachment" title="Remove attachment">
          <RemoveIcon />
        </AttachmentPrimitive.Remove>
      ) : null}
    </AttachmentPrimitive.Root>
  )
}

function AttachmentIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true" focusable="false">
      <path d="M5 10.5L10.6 4.9C11.4 4.1 11.4 2.8 10.6 2C9.8 1.2 8.5 1.2 7.7 2L2.4 7.3C1.3 8.4 1.3 10.2 2.4 11.3C3.5 12.4 5.3 12.4 6.4 11.3L11.3 6.4" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function RemoveIcon() {
  return (
    <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true" focusable="false">
      <path d="M2.5 2.5L7.5 7.5M7.5 2.5L2.5 7.5" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  )
}

function MarkdownText() {
  const { text, status } = useSmooth(useMessagePartText(), SMOOTH_TEXT_OPTIONS)
  const className = status.type === "running" ? "acpMarkdown acpMarkdown--streaming" : "acpMarkdown"
  return <div className={className} dangerouslySetInnerHTML={{ __html: marked.parse(text, { async: false }) as string }} />
}
