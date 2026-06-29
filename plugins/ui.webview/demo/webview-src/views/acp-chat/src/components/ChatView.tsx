// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { IconSet } from "@jetbrains/intellij-webview"
import "@jetbrains/intellij-webview-controls/define/icon"
import type { ClipboardEvent } from "react"
import {
  AssistantRuntimeProvider,
  AttachmentPrimitive,
  ComposerPrimitive,
  MessagePrimitive,
  SelectionToolbarPrimitive,
  ThreadPrimitive,
  useMessagePartText,
  useSmooth,
} from "@assistant-ui/react"
import { useAcpChat } from "../runtime/useAcpChat"
import { AgentSelector } from "./AgentSelector"
import { ApprovalPrompt } from "./ApprovalPrompt"
import { AuthPrompt } from "./AuthPrompt"
import { ChatList } from "./ChatList"
import { MarkdownRenderer } from "./MarkdownRenderer"
import { ModelPicker } from "./ModelPicker"
import { PlanView } from "./PlanView"
import { SlashCommandMenu } from "./SlashCommandMenu"
import { ThinkingBlock } from "./ThinkingBlock"
import { ToolCallCard } from "./ToolCallCard"

const SMOOTH_TEXT_OPTIONS = { drainMs: 250, maxCharIntervalMs: 5, minCommitMs: 33 }
const ACP_CHAT_ICONS = IconSet.define("AcpChatIcons")
const SEND_ICON_PATH = "icons/acpChatSend.svg"

export function ChatView() {
  const chat = useAcpChat()
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
        <ChatList chat={chat} />
        <main className="acpChatMain">
        {chat.status ? <header className="acpChatHeader"><span className="acpChatStatus">{chat.status}</span></header> : null}
        <PlanView plan={chat.plan} />
        <ThreadPrimitive.Root className="acpThread">
          <ThreadPrimitive.Viewport className="acpThreadViewport">
            <ThreadPrimitive.Empty>
              <div className="acpEmpty">Select an agent and send a message to start.</div>
            </ThreadPrimitive.Empty>
            <ThreadPrimitive.Messages components={{ UserMessage, AssistantMessage }} />
          </ThreadPrimitive.Viewport>
          <SelectionToolbarPrimitive.Root className="acpSelectionToolbar" aria-label="Selection actions">
            <SelectionToolbarPrimitive.Quote className="acpSelectionToolbarButton">Quote</SelectionToolbarPrimitive.Quote>
          </SelectionToolbarPrimitive.Root>
          <div className="acpComposerShell">
            <ComposerPrimitive.Unstable_TriggerPopoverRoot>
              <ComposerPrimitive.Root className="acpComposer">
                <div className="acpComposerMain">
                  <ComposerPrimitive.Quote className="acpComposerQuote">
                    <span className="acpComposerQuoteLabel">Quote</span>
                    <ComposerPrimitive.QuoteText className="acpComposerQuoteText" />
                    <ComposerPrimitive.QuoteDismiss className="acpComposerQuoteDismiss" aria-label="Remove quote" title="Remove quote">
                      <RemoveIcon />
                    </ComposerPrimitive.QuoteDismiss>
                  </ComposerPrimitive.Quote>
                  <div className="acpAttachmentList acpComposerAttachments">
                    <ComposerPrimitive.Attachments>
                      {() => <AttachmentChip removable />}
                    </ComposerPrimitive.Attachments>
                  </div>
                  <ComposerPrimitive.Input className="acpComposerInput" placeholder="Type your task or use / for commands…" onPaste={notifyOnUnsupportedImagePaste} />
                  <ComposerPrimitive.Send className="acpComposerSend" aria-label="Send" title="Send">
                    <jb-icon className="acpComposerSendIcon" src={ACP_CHAT_ICONS.src(SEND_ICON_PATH)} />
                  </ComposerPrimitive.Send>
                </div>
                <SlashCommandMenu commands={chat.commands} />
              </ComposerPrimitive.Root>
            </ComposerPrimitive.Unstable_TriggerPopoverRoot>
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
        </main>
        {chat.permission ? <ApprovalPrompt permission={chat.permission} /> : null}
        {chat.auth ? <AuthPrompt auth={chat.auth} /> : null}
      </div>
    </AssistantRuntimeProvider>
  )
}

function UserMessage() {
  return (
    <MessagePrimitive.Root className="acpMsg acpMsgUser">
      <MessagePrimitive.Quote>
        {quote => <blockquote className="acpMessageQuote">{quote.text}</blockquote>}
      </MessagePrimitive.Quote>
      <MessagePrimitive.Parts components={{ Text: PlainText }} />
      <div className="acpAttachmentList acpMessageAttachments">
        <MessagePrimitive.Attachments>
          {() => <AttachmentChip />}
        </MessagePrimitive.Attachments>
      </div>
    </MessagePrimitive.Root>
  )
}

function AssistantMessage() {
  return (
    <MessagePrimitive.Root className="acpMsg acpMsgAssistant">
      <MessagePrimitive.Parts
        components={{ Text: MarkdownText, Reasoning: ThinkingBlock, tools: { Fallback: ToolCallCard } }}
      />
    </MessagePrimitive.Root>
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

function RemoveIcon() {
  return (
    <svg width="10" height="10" viewBox="0 0 10 10" aria-hidden="true" focusable="false">
      <path d="M2.5 2.5L7.5 7.5M7.5 2.5L2.5 7.5" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  )
}

function MarkdownText() {
  const { text, status } = useSmooth(useMessagePartText(), SMOOTH_TEXT_OPTIONS)
  return <MarkdownRenderer text={text} streaming={status.type === "running"} />
}
