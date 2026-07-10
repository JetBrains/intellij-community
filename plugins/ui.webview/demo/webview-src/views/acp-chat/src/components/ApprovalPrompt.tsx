// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import type { PendingPermission } from "../model/types"

export function ApprovalPrompt({ permission }: { permission: PendingPermission }) {
  return (
    <div className="acpApprovalOverlay" role="dialog" aria-modal="true" aria-label="Permission request">
      <div className="acpApproval">
        <div className="acpApprovalTitle">{permission.view.title}</div>
        <div className="acpApprovalOptions">
          {permission.view.options.map(option => (
            <button
              key={option.optionId}
              type="button"
              className={`acpApprovalButton acpApprovalButton--${option.kind ?? "default"}`}
              onClick={() => permission.resolve(option.optionId)}
            >
              {option.name}
            </button>
          ))}
          <button
            type="button"
            className="acpApprovalButton acpApprovalButton--cancel"
            onClick={() => permission.resolve(null)}
          >
            Cancel
          </button>
        </div>
      </div>
    </div>
  )
}
