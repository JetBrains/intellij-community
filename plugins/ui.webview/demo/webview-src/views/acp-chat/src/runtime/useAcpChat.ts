// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import {
  useExternalStoreRuntime,
  type AppendMessage,
  type AttachmentAdapter,
  type CompleteAttachment,
  type ExternalStoreThreadData,
  type ExternalStoreThreadListAdapter,
  type PendingAttachment,
  type ThreadMessageLike,
  type ThreadUserMessagePart,
} from "@assistant-ui/react"
import type { ContentBlock } from "@agentclientprotocol/sdk"
import { AcpAuthRequiredError, AcpSession, type AcpEventSink, type StartOutcome } from "../acp/client"
import { acpBridgeHost } from "../bridge/webviewApi"
import type {
  AcpSessionInfoUpdateView,
  AcpSessionInfoView,
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
const textAttachmentAccept = "text/*,.csv,.json,.jsonl,.md,.markdown,.txt,.xml,.yaml,.yml"
let attachmentIdSeq = 0

type TurnSegment =
  | { type: "reasoning"; text: string }
  | { type: "text"; text: string }
  | { type: "tool"; tool: ToolCallView }

interface Turn {
  segments: TurnSegment[]
}

interface QuoteInfoView {
  text: string
  messageId: string
}

type AuthRequestResult = { kind: "choice"; choice: AuthChoice } | { kind: "retry" } | null

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
  chatListSupported: boolean
  chatListLoading: boolean
  chatListHasMore: boolean
  chatListCanDelete: boolean
  activeSessionId: string | null
  loadMoreChats: () => void
  selectAgent: (agentId: string) => void
  openAcpConfig: () => void
  selectMode: (modeId: string) => void
  selectConfigOption: (option: ConfigOptionView, value: string | boolean) => void
  notifyAttachmentCapabilitiesUnavailable: () => void
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
  const [sessions, setSessions] = useState<AcpSessionInfoView[]>([])
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null)
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [chatListLoading, setChatListLoading] = useState(false)
  const [chatListSupported, setChatListSupported] = useState(false)
  const [chatListCanDelete, setChatListCanDelete] = useState(false)

  const sessionRef = useRef<AcpSession | null>(null)
  const turnRef = useRef<Turn | null>(null)
  const lastChunkRoleRef = useRef<"user" | "assistant" | null>(null)
  const activeSessionIdRef = useRef<string | null>(null)
  const plansByIdRef = useRef(new Map<string, PlanEntryView[]>())
  const assistantSeqRef = useRef(0)
  const authRequestSeqRef = useRef(0)
  const newThreadSwitchRef = useRef<Promise<void> | null>(null)
  const activeAuthMessageIdRef = useRef<string | null>(null)
  const activeAuthRef = useRef<PendingAuth | null>(null)
  // Resolver for the select-phase auth card, so a dying agent (or unmount) can unblock the prompt instead of hanging.
  const authResolveRef = useRef<((result: AuthRequestResult) => void) | null>(null)

  useEffect(() => {
    let cancelled = false
    acpBridgeHost.listAgents()
      .then(result => { if (!cancelled) setAgents(result.agents) })
      .catch(error => { if (!cancelled) setStatus(errorText(error)) })
    return () => { cancelled = true }
  }, [])

  useEffect(() => {
    activeSessionIdRef.current = activeSessionId
  }, [activeSessionId])

  // On unmount, unblock any pending auth prompt and stop the agent so the process is not orphaned.
  useEffect(() => () => {
    authResolveRef.current?.(null)
    void sessionRef.current?.stop()
  }, [])

  const flushTurn = useCallback(() => {
    const turn = turnRef.current
    if (!turn) return
    const parts = turn.segments.map((segment): unknown => {
      if (segment.type === "reasoning") return { type: "reasoning", text: segment.text }
      if (segment.type === "text") return { type: "text", text: segment.text }
      const tool = segment.tool
      return {
        type: "tool-call",
        toolCallId: tool.toolCallId,
        toolName: tool.kind,
        args: {},
        argsText: tool.title,
        result: { status: tool.status, title: tool.title, kind: tool.kind, text: tool.text, diff: tool.diff },
      }
    })
    if (parts.length === 0) parts.push({ type: "text", text: "" })
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

  const resetActiveThreadUi = useCallback(() => {
    setMessages([])
    turnRef.current = null
    lastChunkRoleRef.current = null
    activeAuthMessageIdRef.current = null
    activeAuthRef.current = null
    clearPlans()
    setIsRunning(false)
  }, [clearPlans])

  const ensureAssistantTurn = useCallback((): Turn => {
    let turn = turnRef.current
    if (!turn) {
      turn = { segments: [] }
      turnRef.current = turn
      setMessages(previous => [
        ...previous,
        { id: `assistant-${++assistantSeqRef.current}`, role: "assistant", content: [] },
      ])
    }
    lastChunkRoleRef.current = "assistant"
    return turn
  }, [])

  const putAuthMessage = useCallback((auth: PendingAuth, messageId?: string, replaceMessageId?: string | null): string => {
    const id = messageId ?? activeAuthMessageIdRef.current ?? `assistant-${++assistantSeqRef.current}`
    const previousId = replaceMessageId && replaceMessageId !== id ? replaceMessageId : null
    activeAuthMessageIdRef.current = id
    activeAuthRef.current = auth
    turnRef.current = null
    lastChunkRoleRef.current = "assistant"
    setMessages(previous => {
      const content = authMessageContent(auth)
      const next = previous.slice()
      const index = next.findIndex(message => message.id === id)
      if (index >= 0) {
        next[index] = { ...next[index], role: "assistant", content }
        return next
      }
      if (previousId) {
        const replaceIndex = next.findIndex(message => message.id === previousId)
        if (replaceIndex >= 0) {
          next[replaceIndex] = { id, role: "assistant", content }
          return next
        }
      }
      next.push({ id, role: "assistant", content })
      return next
    })
    return id
  }, [])

  const updateActiveAuthMessage = useCallback((patch: Partial<PendingAuth>) => {
    const current = activeAuthRef.current
    const id = activeAuthMessageIdRef.current
    if (!current || !id) return
    putAuthMessage({ ...current, ...patch }, id)
  }, [putAuthMessage])

  const appendUserChunk = useCallback((text: string) => {
    if (!text) return
    turnRef.current = null
    clearPlans()
    setMessages(previous => {
      const next = previous.slice()
      const last = next[next.length - 1]
      if (lastChunkRoleRef.current === "user" && last?.role === "user") {
        next[next.length - 1] = appendTextToMessage(last, text)
        return next
      }
      next.push({ id: `user-${++assistantSeqRef.current}`, role: "user", content: textMessageContent(text) })
      return next
    })
    lastChunkRoleRef.current = "user"
  }, [clearPlans])

  const upsertSession = useCallback((sessionInfo: AcpSessionInfoView) => {
    setSessions(previous => mergeSessions(previous, [sessionInfo]))
  }, [])

  const updateActiveSessionInfo = useCallback((update: AcpSessionInfoUpdateView) => {
    const sessionId = activeSessionIdRef.current
    if (!sessionId) return
    const activeSession = sessionRef.current
    setSessions(previous => {
      let found = false
      const next = previous.map(session => {
        if (session.sessionId !== sessionId) return session
        found = true
        return applySessionInfoUpdate(session, update)
      })
      if (!found && activeSession?.activeSessionId === sessionId) {
        next.unshift(applySessionInfoUpdate({ sessionId, cwd: activeSession.workingDirectory }, update))
      }
      return next
    })
  }, [])

  const loadSessionsPage = useCallback(async (session: AcpSession, cursor: string | null, append: boolean) => {
    setChatListLoading(true)
    try {
      const response = await session.listSessions(cursor)
      if (sessionRef.current !== session) return
      const activeId = session.activeSessionId
      let nextSessions = response.sessions
      if (activeId && !nextSessions.some(item => item.sessionId === activeId)) {
        nextSessions = [{ sessionId: activeId, cwd: session.workingDirectory, title: "Current chat", updatedAt: null }, ...nextSessions]
      }
      setSessions(previous => append ? mergeSessions(previous, nextSessions) : nextSessions)
      setNextCursor(response.nextCursor)
      activeSessionIdRef.current = activeId
      setActiveSessionId(activeId)
    }
    catch (error) {
      if (sessionRef.current === session) setStatus(errorText(error))
    }
    finally {
      if (sessionRef.current === session) setChatListLoading(false)
    }
  }, [])

  const sink = useMemo<AcpEventSink>(() => ({
    onUserMessage(text) {
      appendUserChunk(text)
    },
    onMessageChunk(text) {
      const turn = ensureAssistantTurn()
      appendTurnText(turn, "text", text)
      flushTurn()
    },
    onThoughtChunk(text) {
      const turn = ensureAssistantTurn()
      appendTurnText(turn, "reasoning", text)
      flushTurn()
    },
    onToolCall(view) {
      const turn = ensureAssistantTurn()
      const index = turn.segments.findIndex(segment => segment.type === "tool" && segment.tool.toolCallId === view.toolCallId)
      if (index >= 0) {
        const segment = turn.segments[index]
        if (segment.type !== "tool") return
        const existing = segment.tool
        turn.segments[index] = {
          type: "tool",
          tool: {
            ...existing,
            ...view,
            title: view.title || existing.title,
            text: view.text ?? existing.text,
            diff: view.diff ?? existing.diff,
          },
        }
      }
      else {
        turn.segments.push({ type: "tool", tool: view })
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
    onSessionInfoUpdate(update) {
      updateActiveSessionInfo(update)
    },
    requestPermission(view) {
      return new Promise<string | null>(resolve => {
        setPermission({ view, resolve: optionId => { setPermission(null); resolve(optionId) } })
      })
    },
    onAuthUpdate(authUri) {
      updateActiveAuthMessage({ authUri })
    },
    onAgentExit(code) {
      setStatus(`Agent exited (code ${code ?? "unknown"})`)
      setIsRunning(false)
      // Unblock a pending auth prompt so the selection loop does not wait on a dead agent forever.
      authResolveRef.current?.(null)
    },
  }), [appendUserChunk, ensureAssistantTurn, flushTurn, publishPlans, updateActiveAuthMessage, updateActiveSessionInfo])

  const attachmentAdapter = useMemo(() => createAttachmentAdapter(promptCapabilities), [promptCapabilities])

  const switchToNewThread = useCallback((): Promise<void> => {
    const inFlight = newThreadSwitchRef.current
    if (inFlight) return inFlight
    const promise = (async () => {
      const session = sessionRef.current
      if (!session) {
        setStatus("Select an agent to start a session first.")
        return
      }
      const agentId = selectedAgentId
      resetActiveThreadUi()
      setStatus("")
      let outcome: StartOutcome
      if (session.canCloseActiveSession) {
        try {
          await session.closeActiveSession()
        }
        catch (error) {
          setStatus(errorText(error))
        }
        outcome = await session.openSession()
      }
      else if (agentId) {
        outcome = await session.restart(agentId, sink)
      }
      else {
        outcome = await session.openSession()
      }
      if (outcome.kind !== "ready") {
        setStatus(outcome.message)
        return
      }
      setStatus("")
      const sessionId = session.activeSessionId
      activeSessionIdRef.current = sessionId
      setActiveSessionId(sessionId)
      if (sessionId) upsertSession({ sessionId, cwd: session.workingDirectory, title: "New chat", updatedAt: null })
      if (chatListSupported) void loadSessionsPage(session, null, false)
    })()
    newThreadSwitchRef.current = promise
    void promise.finally(() => {
      if (newThreadSwitchRef.current === promise) newThreadSwitchRef.current = null
    })
    return promise
  }, [chatListSupported, loadSessionsPage, resetActiveThreadUi, selectedAgentId, sink, upsertSession])

  const switchToSession = useCallback(async (threadId: string) => {
    const session = sessionRef.current
    const sessionInfo = sessions.find(item => item.sessionId === threadId)
    if (!session || !sessionInfo) {
      setStatus("The selected chat is not available.")
      return
    }
    const previousActiveSessionId = activeSessionIdRef.current
    resetActiveThreadUi()
    activeSessionIdRef.current = threadId
    setActiveSessionId(threadId)
    setStatus("")
    setIsRunning(true)
    try {
      await session.loadSession(sessionInfo)
    }
    catch (error) {
      activeSessionIdRef.current = previousActiveSessionId
      setActiveSessionId(previousActiveSessionId)
      setStatus(errorText(error))
    }
    finally {
      setIsRunning(false)
    }
  }, [resetActiveThreadUi, sessions])

  const deleteChat = useCallback(async (threadId: string) => {
    const session = sessionRef.current
    if (!session) {
      setStatus("Select an agent to start a session first.")
      return
    }
    try {
      await session.deleteSession(threadId)
      setSessions(previous => previous.filter(item => item.sessionId !== threadId))
      if (activeSessionIdRef.current === threadId) await switchToNewThread()
    }
    catch (error) {
      setStatus(errorText(error))
    }
  }, [switchToNewThread])

  const loadMoreChats = useCallback(() => {
    const session = sessionRef.current
    if (!session || !nextCursor || chatListLoading) return
    void loadSessionsPage(session, nextCursor, true)
  }, [chatListLoading, loadSessionsPage, nextCursor])

  const openAcpConfig = useCallback(() => {
    void (async () => {
      try {
        setStatus("")
        const result = await acpBridgeHost.openAcpConfig()
        if (!result.ok) setStatus(result.error ?? "Failed to open ACP configuration.")
      }
      catch (error) {
        setStatus(errorText(error))
      }
    })()
  }, [])

  const requestAuth = useCallback((methods: PendingAuth["methods"], message: string, error?: string): Promise<AuthRequestResult> => {
    return new Promise<AuthRequestResult>(resolve => {
      let settled = false
      const settle = (result: AuthRequestResult) => {
        if (settled) return
        settled = true
        if (authResolveRef.current === settle) authResolveRef.current = null
        if (result == null) {
          activeAuthMessageIdRef.current = null
          activeAuthRef.current = null
        }
        resolve(result)
      }
      authResolveRef.current = settle
      const messageId = `assistant-${++assistantSeqRef.current}`
      const replaceMessageId = activeAuthMessageIdRef.current
      putAuthMessage({
        requestId: `auth-request-${++authRequestSeqRef.current}`,
        methods,
        message,
        phase: "select",
        error,
        onChoose: choice => settle(choice ? { kind: "choice", choice } : null),
        onRetry: methods.length === 0 ? () => settle({ kind: "retry" }) : undefined,
        onOpenConfig: methods.length === 0 ? openAcpConfig : undefined,
      }, messageId, replaceMessageId)
    })
  }, [openAcpConfig, putAuthMessage])

  const showAuthInProgress = useCallback((methods: PendingAuth["methods"], message: string, onCancel: () => void) => {
    const requestId = activeAuthRef.current?.requestId ?? `auth-request-${++authRequestSeqRef.current}`
    putAuthMessage({ requestId, methods, message, phase: "authenticating", onChoose: () => onCancel(), onOpenConfig: methods.length === 0 ? openAcpConfig : undefined })
  }, [openAcpConfig, putAuthMessage])

  const showAuthComplete = useCallback((message = "The agent is ready to continue.") => {
    const current = activeAuthRef.current
    const id = activeAuthMessageIdRef.current
    if (!current || !id) return
    putAuthMessage({ ...current, phase: "complete", message, error: undefined, authUri: undefined, onChoose: () => {} }, id)
    activeAuthRef.current = null
    activeAuthMessageIdRef.current = null
  }, [putAuthMessage])

  const threadListAdapter = useMemo<ExternalStoreThreadListAdapter | undefined>(() => {
    if (!chatListSupported) return undefined
    return {
      threadId: activeSessionId ?? undefined,
      isLoading: chatListLoading,
      threads: sessions.map(toThreadListData),
      onSwitchToNewThread: switchToNewThread,
      onSwitchToThread: switchToSession,
      onDelete: chatListCanDelete ? deleteChat : undefined,
    }
  }, [activeSessionId, chatListCanDelete, chatListLoading, chatListSupported, deleteChat, sessions, switchToNewThread, switchToSession])

  const onNew = useCallback(async (message: AppendMessage) => {
    const session = sessionRef.current
    if (!session || !session.isActive) {
      setStatus("Select an agent to start a session first.")
      return
    }
    let blocks: ContentBlock[]
    try {
      blocks = buildPromptBlocks(message, promptCapabilities)
    }
    catch (error) {
      setStatus(errorText(error))
      return
    }
    const text = textFromAppendMessage(message)
    const userId = `user-${++assistantSeqRef.current}`
    setMessages(previous => [
      ...previous,
      { id: userId, role: "user", content: text ? textMessageContent(text) : [], attachments: message.attachments, metadata: message.metadata },
    ])
    turnRef.current = null
    lastChunkRoleRef.current = null
    clearPlans()
    setStatus("")
    setIsRunning(true)
    try {
      let authError: string | undefined
      let promptAuthenticated = false
      for (;;) {
        try {
          await session.prompt(blocks)
          if (promptAuthenticated) showAuthComplete("Authentication complete. Prompt retried.")
          break
        }
        catch (error) {
          if (!(error instanceof AcpAuthRequiredError)) throw error
          const authResult = await requestAuth(error.methods, error.message, authError)
          if (authResult?.kind === "retry") continue
          if (!authResult) {
            setStatus("Authentication cancelled.")
            break
          }
          let cancelledDuringAuth = false
          showAuthInProgress(error.methods, error.message, () => { cancelledDuringAuth = true; void session.stop() })
          try {
            if (authResult.choice.env) {
              if (!selectedAgentId) throw new Error("Cannot reconnect the ACP agent for environment-based authentication.")
              await session.reconnectWithEnv(selectedAgentId, authResult.choice.env, sink)
            }
            await session.authenticate(authResult.choice.methodId)
            if (authResult.choice.env) {
              const outcome = await session.openSession()
              if (outcome.kind === "auth-required") throw new Error(outcome.message)
              if (outcome.kind === "error") throw new Error(outcome.message)
            }
            showAuthInProgress(error.methods, "Authentication complete. Retrying the prompt.", () => { cancelledDuringAuth = true; void session.stop() })
            promptAuthenticated = true
            authError = undefined
          }
          catch (authFailure) {
            if (cancelledDuringAuth) {
              setStatus("Authentication cancelled.")
              break
            }
            authError = errorText(authFailure)
          }
          if (cancelledDuringAuth) {
            setStatus("Authentication cancelled.")
            break
          }
        }
      }
    }
    catch (error) {
      setStatus(errorText(error))
    }
    finally {
      setIsRunning(false)
    }
  }, [clearPlans, promptCapabilities, requestAuth, selectedAgentId, showAuthComplete, showAuthInProgress, sink])

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
    adapters: { attachments: attachmentAdapter, threadList: threadListAdapter },
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
        resetActiveThreadUi()
        resetSessionMetadata()
        setPermission(null)
        setSessions([])
        activeSessionIdRef.current = null
        setActiveSessionId(null)
        setNextCursor(null)
        setChatListLoading(false)
        setChatListSupported(false)
        setChatListCanDelete(false)
        // The previous session was just stopped; drop the stale selection until this one is ready.
        setSelectedAgentId(null)
        let outcome = await session.start(agentId, sink)
        let authError: string | undefined
        // Interactive authorization: keep prompting until the agent lets us open a session, the user cancels, or it fails.
        while (outcome.kind === "auth-required") {
          const { methods, message } = outcome
          const authResult = await requestAuth(methods, message, authError)
          if (authResult?.kind === "retry") {
            outcome = await session.openSession()
            continue
          }
          if (!authResult) {
            await session.stop()
            setStatus("Authentication cancelled.")
            return
          }
          const choice = authResult.choice
          let cancelledDuringAuth = false
          showAuthInProgress(methods, message, () => { cancelledDuringAuth = true; void session.stop() })
          try {
            // env_var methods need the credential present at spawn, so re-spawn with it before authenticating.
            if (choice.env) await session.reconnectWithEnv(agentId, choice.env, sink)
            await session.authenticate(choice.methodId)
            outcome = await session.openSession()
            authError = undefined
          }
          catch (error) {
            if (cancelledDuringAuth) {
              setStatus("Authentication cancelled.")
              return
            }
            authError = errorText(error)
            outcome = { kind: "auth-required", methods, message }
          }
          if (cancelledDuringAuth) {
            setStatus("Authentication cancelled.")
            return
          }
        }
        if (outcome.kind === "error") {
          setStatus(outcome.message)
          return
        }
        showAuthComplete()
        setSelectedAgentId(agentId)
        const capabilities = session.sessionCapabilities
        const supportsChatList = capabilities.list && capabilities.load
        setChatListSupported(supportsChatList)
        setChatListCanDelete(capabilities.delete)
        activeSessionIdRef.current = session.activeSessionId
        setActiveSessionId(session.activeSessionId)
        if (supportsChatList) {
          await loadSessionsPage(session, null, false)
        }
        else {
          setStatus("The selected ACP agent does not support chat history.")
        }
      }
      catch (error) {
        setStatus(errorText(error))
      }
      finally {
        setStarting(false)
      }
    })()
  }, [loadSessionsPage, requestAuth, resetActiveThreadUi, resetSessionMetadata, showAuthComplete, showAuthInProgress, sink])

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

  const notifyAttachmentCapabilitiesUnavailable = useCallback(() => {
    if (selectedAgentId == null) {
      setStatus("Image attachment support can be detected only after an ACP agent is activated.")
    }
    else if (!promptCapabilities.image && promptCapabilities.embeddedContext) {
      setStatus("The active ACP agent does not advertise image prompt attachments.")
    }
    else {
      setStatus("The active ACP agent does not advertise image or embedded-context prompt attachments.")
    }
  }, [selectedAgentId, promptCapabilities])

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
    chatListSupported,
    chatListLoading,
    chatListHasMore: nextCursor != null,
    chatListCanDelete,
    activeSessionId,
    loadMoreChats,
    selectAgent,
    openAcpConfig,
    selectMode,
    selectConfigOption,
    notifyAttachmentCapabilitiesUnavailable,
  }
}

function textMessageContent(text: string): ThreadMessageLike["content"] {
  return [{ type: "text", text }] as ThreadMessageLike["content"]
}

function authMessageContent(auth: PendingAuth): ThreadMessageLike["content"] {
  const status = auth.phase === "complete" ? "completed" : auth.phase === "authenticating" ? "in_progress" : "pending"
  const requestId = auth.requestId ?? "auth"
  return [{
    type: "tool-call",
    toolCallId: requestId,
    toolName: "auth",
    args: {},
    argsText: auth.message ?? "Authentication required",
    result: { status, title: "Authentication", kind: "auth", auth },
  }] as ThreadMessageLike["content"]
}

function appendTurnText(turn: Turn, type: "reasoning" | "text", text: string): void {
  const last = turn.segments[turn.segments.length - 1]
  if (last?.type === type) {
    last.text += text
    return
  }
  turn.segments.push({ type, text })
}

function appendTextToMessage(message: ThreadMessageLike, text: string): ThreadMessageLike {
  const content = Array.isArray(message.content) ? [...message.content] as any[] : []
  const last = content[content.length - 1]
  if (last?.type === "text" && typeof last.text === "string") {
    content[content.length - 1] = { ...last, text: last.text + text }
  }
  else {
    content.push({ type: "text", text })
  }
  return { ...message, content: content as ThreadMessageLike["content"] }
}

function mergeSessions(previous: AcpSessionInfoView[], incoming: AcpSessionInfoView[]): AcpSessionInfoView[] {
  const byId = new Map(previous.map(session => [session.sessionId, session]))
  for (const session of incoming) {
    byId.set(session.sessionId, { ...byId.get(session.sessionId), ...session })
  }
  return Array.from(byId.values())
}

function applySessionInfoUpdate(session: AcpSessionInfoView, update: AcpSessionInfoUpdateView): AcpSessionInfoView {
  return {
    ...session,
    ...(update.title !== undefined ? { title: update.title } : {}),
    ...(update.updatedAt !== undefined ? { updatedAt: update.updatedAt } : {}),
  }
}

function toThreadListData(session: AcpSessionInfoView): ExternalStoreThreadData<"regular"> {
  return {
    id: session.sessionId,
    status: "regular",
    title: session.title?.trim() || "Untitled chat",
    custom: {
      cwd: session.cwd,
      updatedAt: session.updatedAt ?? null,
    },
  }
}

function createAttachmentAdapter(capabilities: PromptCapabilitiesView): AttachmentAdapter | undefined {
  const accept = attachmentAccept(capabilities)
  if (!accept) return undefined
  return {
    accept,
    async add({ file }) {
      if (!canAttachFile(file, capabilities)) {
        throw new Error(`The selected agent does not support '${file.name}' as a prompt attachment.`)
      }
      return {
        id: `attachment-${++attachmentIdSeq}`,
        type: file.type.startsWith("image/") ? "image" : "document",
        name: file.name,
        contentType: file.type || contentTypeForFileName(file.name),
        file,
        status: { type: "requires-action", reason: "composer-send" },
      }
    },
    async send(attachment) {
      return completeAttachment(attachment)
    },
    async remove() {
    },
  }
}

function attachmentAccept(capabilities: PromptCapabilitiesView): string | undefined {
  const accepted: string[] = []
  if (capabilities.image) accepted.push("image/*")
  if (capabilities.embeddedContext) accepted.push(textAttachmentAccept)
  return accepted.length > 0 ? accepted.join(",") : undefined
}

function canAttachFile(file: File, capabilities: PromptCapabilitiesView): boolean {
  if (capabilities.image && file.type.startsWith("image/")) return true
  return capabilities.embeddedContext && isTextLikeFile(file.name, file.type)
}

async function completeAttachment(attachment: PendingAttachment): Promise<CompleteAttachment> {
  const contentType = attachment.contentType || attachment.file.type || contentTypeForFileName(attachment.name)
  if (attachment.type === "image") {
    return {
      ...attachment,
      contentType,
      status: { type: "complete" },
      content: [{ type: "image", image: await readFileAsDataUrl(attachment.file), filename: attachment.name }],
    }
  }
  return {
    ...attachment,
    contentType,
    status: { type: "complete" },
    content: [{ type: "file", filename: attachment.name, mimeType: contentType, data: await attachment.file.text() }],
  }
}

function buildPromptBlocks(message: AppendMessage, capabilities: PromptCapabilitiesView): ContentBlock[] {
  const blocks: ContentBlock[] = []
  const quote = quoteFromAppendMessage(message)
  if (quote) blocks.push({ type: "text", text: quoteContextText(quote) })
  const text = textFromAppendMessage(message)
  if (text) blocks.push({ type: "text", text })
  for (const attachment of message.attachments ?? []) {
    for (const part of attachment.content ?? []) {
      const block = contentBlockFromAttachmentPart(attachment, part, capabilities)
      if (block) blocks.push(block)
    }
  }
  return blocks
}

function quoteFromAppendMessage(message: AppendMessage): QuoteInfoView | null {
  const quote = message.metadata?.custom?.quote
  if (!quote || typeof quote !== "object") return null
  const text = (quote as { text?: unknown }).text
  const messageId = (quote as { messageId?: unknown }).messageId
  if (typeof text !== "string" || typeof messageId !== "string" || text.length === 0 || messageId.length === 0) return null
  return { text, messageId }
}

function quoteContextText(quote: QuoteInfoView): string {
  const quotedText = quote.text.split(/\r?\n/u).map(line => `> ${line}`).join("\n")
  return `Quoted context from message ${quote.messageId}:\n${quotedText}`
}

function contentBlockFromAttachmentPart(attachment: CompleteAttachment, part: ThreadUserMessagePart, capabilities: PromptCapabilitiesView): ContentBlock | null {
  if (part.type === "image") {
    if (!capabilities.image) throw new Error("The selected agent does not support image prompt attachments.")
    const image = acpImageData(part.image, attachment.contentType)
    return { type: "image", data: image.data, mimeType: image.mimeType, uri: attachmentUri(attachment) }
  }
  if (part.type === "file") {
    if (!capabilities.embeddedContext) throw new Error("The selected agent does not support embedded prompt attachments.")
    return {
      type: "resource",
      resource: {
        uri: attachmentUri(attachment),
        mimeType: part.mimeType,
        text: part.data,
      },
    }
  }
  return null
}

function textFromAppendMessage(message: AppendMessage): string {
  return message.content
    .filter((part): part is { type: "text"; text: string } => part.type === "text")
    .map(part => part.text)
    .join("")
}

function acpImageData(image: string, fallbackMimeType?: string): { data: string; mimeType: string } {
  const match = /^data:([^;,]+);base64,(.*)$/s.exec(image)
  if (!match) return { data: image, mimeType: fallbackMimeType || "application/octet-stream" }
  return { mimeType: match[1], data: match[2] }
}

function attachmentUri(attachment: CompleteAttachment): string {
  return `attachment://${encodeURIComponent(attachment.id)}/${attachment.name.split("/").map(encodeURIComponent).join("/")}`
}

function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ""))
    reader.onerror = () => reject(reader.error ?? new Error(`Failed to read '${file.name}'.`))
    reader.readAsDataURL(file)
  })
}

function isTextLikeFile(name: string, contentType: string): boolean {
  if (contentType.startsWith("text/")) return true
  switch (extensionOf(name)) {
    case "csv":
    case "json":
    case "jsonl":
    case "md":
    case "markdown":
    case "txt":
    case "xml":
    case "yaml":
    case "yml":
      return true
    default:
      return false
  }
}

function contentTypeForFileName(name: string): string {
  switch (extensionOf(name)) {
    case "csv":
      return "text/csv"
    case "json":
    case "jsonl":
      return "application/json"
    case "md":
    case "markdown":
      return "text/markdown"
    case "xml":
      return "application/xml"
    case "yaml":
    case "yml":
      return "application/yaml"
    default:
      return "text/plain"
  }
}

function extensionOf(name: string): string {
  const index = name.lastIndexOf(".")
  return index >= 0 ? name.slice(index + 1).toLocaleLowerCase() : ""
}

function errorText(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}
