// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { marked } from "marked"
import { AssistantRuntimeProvider, ComposerPrimitive, MessagePrimitive, ThreadPrimitive } from "@assistant-ui/react"
import { useAcpChat } from "../runtime/useAcpChat"
import { AgentSelector } from "./AgentSelector"
import { ApprovalPrompt } from "./ApprovalPrompt"
import { AuthPrompt } from "./AuthPrompt"
import { PlanView } from "./PlanView"
import { ThinkingBlock } from "./ThinkingBlock"
import { ToolCallCard } from "./ToolCallCard"

export function ChatView() {
  const chat = useAcpChat()
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
              <ComposerPrimitive.Input className="acpComposerInput" placeholder="Message the agent…" />
              <ComposerPrimitive.Send className="acpComposerSend">Send</ComposerPrimitive.Send>
            </ComposerPrimitive.Root>
            <div className="acpComposerToolbar">
              <AgentSelector
                agents={chat.agents}
                selectedAgentId={chat.selectedAgentId}
                starting={chat.starting}
                onSelect={chat.selectAgent}
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

function MarkdownText(props: any) {
  const text: string = props?.text ?? ""
  return <div className="acpMarkdown" dangerouslySetInnerHTML={{ __html: marked.parse(text, { async: false }) as string }} />
}
