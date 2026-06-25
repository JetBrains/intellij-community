// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useExternalStoreRuntime, type AppendMessage, type ThreadMessageLike } from "@assistant-ui/react"
import type { ContentBlock } from "@agentclientprotocol/sdk"
import { AcpSession, type AcpEventSink } from "../acp/client"
import { acpBridgeHost } from "../bridge/webviewApi"
import type {
  AuthChoice,
  AgentInfo,
  CommandView,
  ConfigOptionView,
  PendingAuth,
  PendingPermission,
  PlanEntryView,
  PromptCapabilitiesView,
  SessionModeView,
  ToolCallView,
} from "../model/types"

const emptyPromptCapabilities: PromptCapabilitiesView = { image: false, audio: false, embeddedContext: false }
const legacyPlanId = "legacy"

interface Turn {
  reasoning: string
  text: string
  tools: ToolCallView[]
}

export interface AcpChat {
  runtime: ReturnType<typeof useExternalStoreRuntime>
  agents: AgentInfo[]
  selectedAgentId: string | null
  starting: boolean
  status: string
  plan: PlanEntryView[]
  promptCapabilities: PromptCapabilitiesView
  modes: SessionModeView[]
  configOptions: ConfigOptionView[]
  currentModeId: string | null
  commands: CommandView[]
  permission: PendingPermission | null
  auth: PendingAuth | null
  selectAgent: (agentId: string) => void
  selectMode: (modeId: string) => void
  selectConfigOption: (option: ConfigOptionView, value: string | boolean) => void
}

export function useAcpChat(): AcpChat {
  const [messages, setMessages] = useState<ThreadMessageLike[]>([])
  const [isRunning, setIsRunning] = useState(false)
  const [agents, setAgents] = useState<AgentInfo[]>([])
  const [selectedAgentId, setSelectedAgentId] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const [status, setStatus] = useState("")
  const [plan, setPlan] = useState<PlanEntryView[]>([])
  const [promptCapabilities, setPromptCapabilities] = useState<PromptCapabilitiesView>(emptyPromptCapabilities)
  const [modes, setModes] = useState<SessionModeView[]>([])
  const [configOptions, setConfigOptions] = useState<ConfigOptionView[]>([])
  const [currentModeId, setCurrentModeId] = useState<string | null>(null)
  const [commands, setCommands] = useState<CommandView[]>([])
  const [permission, setPermission] = useState<PendingPermission | null>(null)
  const [auth, setAuth] = useState<PendingAuth | null>(null)

  const sessionRef = useRef<AcpSession | null>(null)
  const turnRef = useRef<Turn | null>(null)
  const plansByIdRef = useRef(new Map<string, PlanEntryView[]>())
  const assistantSeqRef = useRef(0)
  // Resolver for the select-phase auth prompt, so a dying agent (or unmount) can unblock the prompt instead of hanging.
  const authResolveRef = useRef<((choice: AuthChoice | null) => void) | null>(null)

  useEffect(() => {
    let cancelled = false
    acpBridgeHost.listAgents()
      .then(result => { if (!cancelled) setAgents(result.agents) })
      .catch(error => { if (!cancelled) setStatus(errorText(error)) })
    return () => { cancelled = true }
  }, [])

  // On unmount, unblock any pending auth prompt and stop the agent so the process is not orphaned.
  useEffect(() => () => {
    authResolveRef.current?.(null)
    void sessionRef.current?.stop()
  }, [])

  const flushTurn = useCallback(() => {
    const turn = turnRef.current
    if (!turn) return
    const parts: unknown[] = []
    if (turn.reasoning) {
      parts.push({ type: "reasoning", text: turn.reasoning })
    }
    for (const tool of turn.tools) {
      parts.push({
        type: "tool-call",
        toolCallId: tool.toolCallId,
        toolName: tool.kind,
        args: {},
        argsText: tool.title,
        result: { status: tool.status, title: tool.title, kind: tool.kind, text: tool.text, diff: tool.diff },
      })
    }
    if (turn.text || parts.length === 0) {
      parts.push({ type: "text", text: turn.text })
    }
    setMessages(previous => {
      const next = previous.slice()
      for (let i = next.length - 1; i >= 0; i--) {
        if (next[i].role === "assistant") {
          next[i] = { ...next[i], content: parts as ThreadMessageLike["content"] }
          return next
        }
      }
      return next
    })
  }, [])

  const publishPlans = useCallback(() => {
    setPlan(Array.from(plansByIdRef.current.values()).flat())
  }, [])

  const clearPlans = useCallback(() => {
    plansByIdRef.current.clear()
    setPlan([])
  }, [])

  const resetSessionMetadata = useCallback(() => {
    setPromptCapabilities(emptyPromptCapabilities)
    setModes([])
    setConfigOptions([])
    setCurrentModeId(null)
    setCommands([])
  }, [])

  const sink = useMemo<AcpEventSink>(() => ({
    onMessageChunk(text) {
      const turn = turnRef.current
      if (turn) { turn.text += text; flushTurn() }
    },
    onThoughtChunk(text) {
      const turn = turnRef.current
      if (turn) { turn.reasoning += text; flushTurn() }
    },
    onToolCall(view) {
      const turn = turnRef.current
      if (!turn) return
      const index = turn.tools.findIndex(t => t.toolCallId === view.toolCallId)
      if (index >= 0) {
        const existing = turn.tools[index]
        turn.tools[index] = {
          ...existing,
          ...view,
          title: view.title || existing.title,
          text: view.text ?? existing.text,
          diff: view.diff ?? existing.diff,
        }
      }
      else {
        turn.tools.push(view)
      }
      flushTurn()
    },
    onPlan(entries) {
      plansByIdRef.current.clear()
      if (entries.length > 0) plansByIdRef.current.set(legacyPlanId, entries)
      publishPlans()
    },
    onPlanUpdate(planId, entries) {
      plansByIdRef.current.set(planId, entries)
      publishPlans()
    },
    onPlanRemoved(planId) {
      plansByIdRef.current.delete(planId)
      publishPlans()
    },
    onPromptCapabilities(capabilities) {
      setPromptCapabilities(capabilities)
    },
    onSessionModes(nextModes, nextCurrentModeId) {
      setModes(nextModes)
      setCurrentModeId(nextCurrentModeId)
    },
    onCurrentMode(nextCurrentModeId) {
      setCurrentModeId(nextCurrentModeId)
    },
    onConfigOptions(nextConfigOptions) {
      setConfigOptions(nextConfigOptions)
    },
    onCommands(nextCommands) {
      setCommands(nextCommands)
    },
    requestPermission(view) {
      return new Promise<string | null>(resolve => {
        setPermission({ view, resolve: optionId => { setPermission(null); resolve(optionId) } })
      })
    },
    onAuthUpdate(authUri) {
      setAuth(previous => (previous ? { ...previous, authUri } : previous))
    },
    onAgentExit(code) {
      setStatus(`Agent exited (code ${code ?? "unknown"})`)
      setIsRunning(false)
      // Unblock a pending auth prompt so the selection loop does not wait on a dead agent forever.
      authResolveRef.current?.(null)
    },
  }), [flushTurn, publishPlans])

  const onNew = useCallback(async (message: AppendMessage) => {
    const session = sessionRef.current
    if (!session || !session.isActive) {
      setStatus("Select an agent to start a session first.")
      return
    }
    const blocks = buildPromptBlocks(message)
    const text = textFromBlocks(blocks)
    const assistantId = `assistant-${++assistantSeqRef.current}`
    setMessages(previous => [
      ...previous,
      { id: `user-${assistantSeqRef.current}`, role: "user", content: [{ type: "text", text }] },
      { id: assistantId, role: "assistant", content: [] },
    ])
    turnRef.current = { reasoning: "", text: "", tools: [] }
    clearPlans()
    setStatus("")
    setIsRunning(true)
    try {
      await session.prompt(blocks)
    }
    catch (error) {
      setStatus(errorText(error))
    }
    finally {
      setIsRunning(false)
    }
  }, [clearPlans])

  const onCancel = useCallback(async () => {
    try {
      await sessionRef.current?.cancel()
    }
    catch (error) {
      setStatus(errorText(error))
    }
    setIsRunning(false)
  }, [])

  const runtime = useExternalStoreRuntime({
    isRunning,
    messages,
    setMessages: next => setMessages([...next]),
    unstable_capabilities: { copy: true },
    convertMessage: (message: ThreadMessageLike) => message,
    onNew,
    onCancel,
  })

  const selectAgent = useCallback((agentId: string) => {
    setStarting(true)
    setStatus("")
    const previous = sessionRef.current
    const session = new AcpSession()
    sessionRef.current = session
    void (async () => {
      try {
        await previous?.stop()
        setMessages([])
        clearPlans()
        resetSessionMetadata()
        setPermission(null)
        setAuth(null)
        // The previous session was just stopped; drop the stale selection until this one is ready.
        setSelectedAgentId(null)
        turnRef.current = null
        let outcome = await session.start(agentId, sink)
        let authError: string | undefined
        // Interactive authorization: keep prompting until the agent lets us open a session, the user cancels, or it fails.
        while (outcome.kind === "auth-required") {
          const { methods, message } = outcome
          const choice = await new Promise<AuthChoice | null>(resolve => {
            authResolveRef.current = resolve
            setAuth({ methods, message, phase: "select", error: authError, onChoose: resolve })
          })
          authResolveRef.current = null
          if (!choice) {
            await session.stop()
            setAuth(null)
            setStatus("Authentication cancelled.")
            return
          }
          let cancelledDuringAuth = false
          setAuth({ methods, message, phase: "authenticating", onChoose: () => { cancelledDuringAuth = true; void session.stop() } })
          try {
            // env_var methods need the credential present at spawn, so re-spawn with it before authenticating.
            if (choice.env) await session.reconnectWithEnv(agentId, choice.env, sink)
            await session.authenticate(choice.methodId)
            outcome = await session.openSession()
            authError = undefined
          }
          catch (error) {
            if (cancelledDuringAuth) {
              setAuth(null)
              setStatus("Authentication cancelled.")
              return
            }
            authError = errorText(error)
            outcome = { kind: "auth-required", methods, message }
          }
          if (cancelledDuringAuth) {
            setAuth(null)
            setStatus("Authentication cancelled.")
            return
          }
        }
        setAuth(null)
        if (outcome.kind === "error") {
          setStatus(outcome.message)
          return
        }
        setSelectedAgentId(agentId)
      }
      catch (error) {
        setAuth(null)
        setStatus(errorText(error))
      }
      finally {
        setStarting(false)
      }
    })()
  }, [clearPlans, resetSessionMetadata, sink])

  const selectMode = useCallback((modeId: string) => {
    const session = sessionRef.current
    if (!session || !session.isActive) {
      setStatus("Select an agent to start a session first.")
      return
    }
    void (async () => {
      try {
        setStatus("")
        await session.setMode(modeId)
      }
      catch (error) {
        setStatus(errorText(error))
      }
    })()
  }, [])

  const selectConfigOption = useCallback((option: ConfigOptionView, value: string | boolean) => {
    const session = sessionRef.current
    if (!session || !session.isActive) {
      setStatus("Select an agent to start a session first.")
      return
    }
    void (async () => {
      try {
        setStatus("")
        await session.setConfigOption(option.id, option.type, value)
      }
      catch (error) {
        setStatus(errorText(error))
      }
    })()
  }, [])

  return {
    runtime,
    agents,
    selectedAgentId,
    starting,
    status,
    plan,
    promptCapabilities,
    modes,
    configOptions,
    currentModeId,
    commands,
    permission,
    auth,
    selectAgent,
    selectMode,
    selectConfigOption,
  }
}

function buildPromptBlocks(message: AppendMessage): ContentBlock[] {
  return [{ type: "text", text: textFromAppendMessage(message) }]
}

function textFromAppendMessage(message: AppendMessage): string {
  return message.content
    .filter((part): part is { type: "text"; text: string } => part.type === "text")
    .map(part => part.text)
    .join("")
}

function textFromBlocks(blocks: ContentBlock[]): string {
  return blocks
    .filter((block): block is ContentBlock & { type: "text"; text: string } => block.type === "text")
    .map(block => block.text)
    .join("")
}

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}
