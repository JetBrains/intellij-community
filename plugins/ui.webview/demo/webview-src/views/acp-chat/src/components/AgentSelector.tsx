// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { AgentInfo } from "../model/types"

export function AgentSelector(props: {
  agents: AgentInfo[]
  selectedAgentId: string | null
  starting: boolean
  onSelect: (agentId: string) => void
}) {
  return (
    <label className="acpAgentSelector">
      <span className="acpAgentSelectorLabel">Agent</span>
      <select
        className="acpAgentSelect"
        value={props.selectedAgentId ?? ""}
        disabled={props.starting}
        onChange={event => {
          if (event.target.value) props.onSelect(event.target.value)
        }}
      >
        <option value="" disabled>
          {props.agents.length ? "Select an agent…" : "No agents in ~/.jetbrains/acp.json"}
        </option>
        {props.agents.map(agent => (
          <option key={agent.id} value={agent.id}>{agent.name}</option>
        ))}
      </select>
      {props.starting && <span className="acpAgentStarting">Starting…</span>}
    </label>
  )
}
