// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useState } from "react"
import type { AuthMethodView, PendingAuth } from "../model/types"

/**
 * In-chat authorization dialog. Mirrors {@link ApprovalPrompt}: the runtime resolves `auth.onChoose` with the chosen
 * method (and, for env_var methods, the entered credentials) or `null` to cancel. While the agent runs an OAuth device
 * flow the dialog switches to the `authenticating` phase and shows the verification URL pushed via `authenticate/update`.
 */
export function AuthCard({ auth }: { auth: PendingAuth }) {
  return (
    <div className="acpApproval acpAuth acpAuthCard" role="group" aria-label="Authentication">
      {auth.phase === "select" ? <AuthMethodPicker auth={auth} /> : auth.phase === "complete" ? <AuthComplete auth={auth} /> : <AuthInProgress auth={auth} />}
    </div>
  )
}

export function AuthPrompt({ auth }: { auth: PendingAuth }) {
  return <AuthCard auth={auth} />
}

function AuthMethodPicker({ auth }: { auth: PendingAuth }) {
  return (
    <>
      <div className="acpApprovalTitle">{auth.message || "Authentication required"}</div>
      {auth.error ? <div className="acpAuthError">{auth.error}</div> : null}
      {auth.methods.length > 0
        ? (
            <div className="acpAuthMethods">
              {auth.methods.map(method => (
                <AuthMethod key={`${auth.requestId ?? "auth"}-${method.id}`} method={method} onChoose={auth.onChoose} />
              ))}
            </div>
          )
        : <UnsupportedAuth />}
      <div className="acpApprovalOptions">
        {auth.onRetry ? (
          <button type="button" className="acpApprovalButton" onClick={auth.onRetry}>
            Retry
          </button>
        ) : null}
        {auth.onOpenConfig ? (
          <button type="button" className="acpApprovalButton" onClick={auth.onOpenConfig}>
            Open acp.json
          </button>
        ) : null}
        <button type="button" className="acpApprovalButton acpApprovalButton--cancel" onClick={() => auth.onChoose(null)}>
          Cancel
        </button>
      </div>
    </>
  )
}

interface EnvRow {
  name: string
  value: string
  secret: boolean
  /** A variable the agent declared (fixed name); user-added rows let the name be edited. */
  fixed: boolean
}

/**
 * One auth method. Variables the agent declares (ACP `env_var` methods) are pre-listed; the user can add more (some
 * agents, e.g. qwen's "Use OpenAI API key", expect `OPENAI_API_KEY`/`OPENAI_BASE_URL`/`OPENAI_MODEL` in the env without
 * declaring them). On submit, entered variables are injected via re-spawn before `authenticate`; with none entered this
 * is a plain agent-driven sign-in / OAuth device flow.
 */
function AuthMethod({ method, onChoose }: { method: AuthMethodView; onChoose: PendingAuth["onChoose"] }) {
  const supportsEnv = method.vars.length > 0 || method.type === "env_var" || method.type === "environment"
  const [rows, setRows] = useState<EnvRow[]>(() =>
    method.vars.map(variable => ({ name: variable.name, value: "", secret: variable.secret, fixed: true })),
  )
  const env = collectEnv(rows)
  const missingRequired = method.vars.some(variable => !variable.optional && !env[variable.name])
  const update = (index: number, patch: Partial<EnvRow>) =>
    setRows(previous => previous.map((row, i) => (i === index ? { ...row, ...patch } : row)))
  return (
    <form
      className="acpAuthMethod"
      onSubmit={event => {
        event.preventDefault()
        if (!missingRequired) onChoose({ methodId: method.id, env: Object.keys(env).length > 0 ? env : undefined })
      }}
    >
      <div className="acpAuthMethodName">{method.name}</div>
      {method.type || method.link ? (
        <div className="acpAuthMethodMeta">
          {method.type ? <span>{method.type}</span> : null}
          {method.link ? <a href={method.link} target="_blank" rel="noreferrer">{method.link}</a> : null}
        </div>
      ) : null}
      {method.description ? <div className="acpAuthMethodDesc">{method.description}</div> : null}
      {supportsEnv ? rows.map((row, index) => (
          <div key={index} className="acpAuthVarRow">
            {row.fixed
              ? <span className="acpAuthVarLabel acpAuthVarName">{row.name}</span>
              : (
                  <input
                    className="acpAuthVarInput acpAuthVarName"
                    placeholder="ENV_VAR"
                    autoComplete="off"
                    spellCheck={false}
                    value={row.name}
                    onChange={event => update(index, { name: event.target.value })}
                  />
                )}
            <input
              className="acpAuthVarInput"
              type={row.secret ? "password" : "text"}
              placeholder="value"
              autoComplete="off"
              spellCheck={false}
              value={row.value}
              onChange={event => update(index, { value: event.target.value })}
            />
          </div>
        )) : null}
      <div className="acpAuthActions">
        {supportsEnv ? (
          <button
            type="button"
            className="acpAuthAddVar"
            onClick={() => setRows(previous => [...previous, { name: "", value: "", secret: false, fixed: false }])}
          >
            + Add variable
          </button>
        ) : <span />}
        <button type="submit" className="acpApprovalButton acpApprovalButton--allow_once" disabled={missingRequired}>
          Authenticate
        </button>
      </div>
    </form>
  )
}

function UnsupportedAuth() {
  return (
    <div className="acpAuthUnsupported">
      This ACP agent did not provide a supported local authentication method. Reconfigure the agent, sign in with its CLI, or retry after updating acp.json.
    </div>
  )
}

function AuthInProgress({ auth }: { auth: PendingAuth }) {
  return (
    <>
      <div className="acpApprovalTitle">Authenticating…</div>
      {auth.authUri
        ? (
            <div className="acpAuthUri">
              <div>Open this URL in your browser to finish signing in:</div>
              <a href={auth.authUri} target="_blank" rel="noreferrer" className="acpAuthUriLink">{auth.authUri}</a>
            </div>
          )
        : <div className="acpAuthHint">Waiting for the agent…</div>}
      {auth.authUri
        ? <div className="acpAuthHint">If it doesn&apos;t continue after you approve, the agent&apos;s OAuth may be unavailable — Cancel and use an API key instead.</div>
        : null}
      <div className="acpApprovalOptions">
        <button type="button" className="acpApprovalButton acpApprovalButton--cancel" onClick={() => auth.onChoose(null)}>
          Cancel
        </button>
      </div>
    </>
  )
}

function AuthComplete({ auth }: { auth: PendingAuth }) {
  return (
    <>
      <div className="acpApprovalTitle">Authentication complete</div>
      <div className="acpAuthHint">{auth.message || "The agent is ready to continue."}</div>
    </>
  )
}

function collectEnv(rows: { name: string; value: string }[]): Record<string, string> {
  const env: Record<string, string> = {}
  for (const row of rows) {
    const name = row.name.trim()
    const value = row.value.trim()
    if (name && value) env[name] = value
  }
  return env
}
