// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { AgentInfo } from "../model/types"
import { acpIconSrc, AGENT_ICON_PATH } from "./icons/AcpChatIconSet"
import { Select, SelectItem, SelectSeparator } from "./Select"

const OPEN_ACP_CONFIG_VALUE = "__open_acp_config__"

export function AgentSelector(props: {
  agents: AgentInfo[]
  selectedAgentId: string | null
  starting: boolean
  onSelect: (agentId: string) => void
  onOpenConfig: () => void
}) {
  const placeholderText = props.agents.length ? "Select an agent…" : "No agents in ~/.jetbrains/acp.json"
  const selectedAgent = props.agents.find(agent => agent.id === props.selectedAgentId)
  const options = props.agents.map(agent => ({ value: agent.id, label: <AgentSelectItem agent={agent} />, textValue: agent.name }))
  return (
    <div className="acpAgentSelector">
      <Select
        className={props.starting ? "acpAgentSelect acpAgentSelectStarting" : "acpAgentSelect"}
        value={props.selectedAgentId ?? ""}
        disabled={props.starting}
        placeholder={<AgentSelectContent name={placeholderText} />}
        triggerAriaLabel={`Agent: ${selectedAgent?.name ?? placeholderText}`}
        options={options}
        onValueChange={value => {
          if (value === OPEN_ACP_CONFIG_VALUE) {
            props.onOpenConfig()
          }
          else if (value) {
            props.onSelect(value)
          }
        }}
      >
        {props.agents.map((agent, index) => (
          <SelectItem key={`${agent.id}-${index}`} value={agent.id} textValue={agent.name}>
            <AgentSelectItem agent={agent} />
          </SelectItem>
        ))}
        {props.agents.length > 0 ? <SelectSeparator /> : null}
        <SelectItem value={OPEN_ACP_CONFIG_VALUE} textValue="Open acp.json">
          <span className="acpAgentSelectConfigItem">Open acp.json</span>
        </SelectItem>
      </Select>
    </div>
  )
}

function AgentSelectItem(props: { agent: AgentInfo }) {
  return <AgentSelectContent name={props.agent.name} iconSrc={props.agent.iconSrc} />
}

function AgentSelectContent(props: { name: string; iconSrc?: string }) {
  const iconSrc = props.iconSrc ?? acpIconSrc(AGENT_ICON_PATH)
  return (
    <span className="acpAgentSelectItemContent">
      <span className="acpAgentSelectItemIcon" aria-hidden="true">
        <jb-icon src={iconSrc} />
      </span>
      <span className="acpAgentSelectItemName">{props.name}</span>
    </span>
  )
}
