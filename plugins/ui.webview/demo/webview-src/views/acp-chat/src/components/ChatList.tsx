// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useEffect, useState, type MouseEvent } from "react"
import { ThreadListItemPrimitive, ThreadListPrimitive } from "@assistant-ui/react"
import type { AcpChat } from "../runtime/useAcpChat"

type ChatListProps = {
  chat: Pick<AcpChat, "chatListSupported" | "chatListLoading" | "chatListHasMore" | "chatListCanDelete" | "startNewChat" | "loadMoreChats">
}

export function ChatList({ chat }: ChatListProps) {
  const [drawerOpen, setDrawerOpen] = useState(false)

  useEffect(() => {
    if (!drawerOpen) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setDrawerOpen(false)
    }
    document.addEventListener("keydown", onKeyDown)
    return () => document.removeEventListener("keydown", onKeyDown)
  }, [drawerOpen])

  if (!chat.chatListSupported) return null

  const closeDrawer = () => setDrawerOpen(false)
  return (
    <>
      <aside className="acpChatListSidebar" aria-label="Chats">
        <ChatListPanel chat={chat} />
      </aside>
      <button
        type="button"
        className="acpChatListDrawerTrigger"
        aria-label="Open chats"
        title="Open chats"
        aria-expanded={drawerOpen}
        onClick={() => setDrawerOpen(true)}
      >
        <SidebarIcon />
      </button>
      <div className="acpChatListOverlay" data-open={drawerOpen ? "true" : "false"} aria-hidden={!drawerOpen}>
        <button type="button" className="acpChatListBackdrop" aria-label="Close chats" onClick={closeDrawer} />
        <aside className="acpChatListDrawer" aria-label="Chats">
          <ChatListPanel chat={chat} onNavigate={closeDrawer} />
        </aside>
      </div>
    </>
  )
}

function ChatListPanel(props: ChatListProps & { onNavigate?: () => void }) {
  const { chat, onNavigate } = props
  const startNewChat = (event: MouseEvent<HTMLButtonElement>) => {
    event.preventDefault()
    onNavigate?.()
    chat.startNewChat()
  }
  return (
    <ThreadListPrimitive.Root className="acpChatListRoot">
      <div className="acpChatListHeader">
        <span className="acpChatListTitle">Chats</span>
        <ThreadListPrimitive.New className="acpChatListNew" aria-label="New chat" title="New chat" onClick={startNewChat}>
          <PlusIcon />
        </ThreadListPrimitive.New>
      </div>
      <div className="acpChatListItems">
        <ThreadListPrimitive.Items>
          {() => <ChatListItem canDelete={chat.chatListCanDelete} onNavigate={onNavigate} />}
        </ThreadListPrimitive.Items>
        {chat.chatListLoading ? <div className="acpChatListLoading">Loading chats...</div> : null}
        {chat.chatListHasMore ? (
          <button type="button" className="acpChatListLoadMore" disabled={chat.chatListLoading} onClick={chat.loadMoreChats}>
            Load more
          </button>
        ) : null}
      </div>
    </ThreadListPrimitive.Root>
  )
}

function ChatListItem(props: { canDelete: boolean; onNavigate?: () => void }) {
  const { canDelete, onNavigate } = props
  return (
    <ThreadListItemPrimitive.Root className="acpChatListItem">
      <ThreadListItemPrimitive.Trigger className="acpChatListItemTrigger" onClick={onNavigate}>
        <span className="acpChatListItemTitle"><ThreadListItemPrimitive.Title fallback="Untitled chat" /></span>
      </ThreadListItemPrimitive.Trigger>
      {canDelete ? (
        <ThreadListItemPrimitive.Delete className="acpChatListDelete" aria-label="Delete chat" title="Delete chat" onClick={event => event.stopPropagation()}>
          <TrashIcon />
        </ThreadListItemPrimitive.Delete>
      ) : null}
    </ThreadListItemPrimitive.Root>
  )
}

function SidebarIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      <path d="M3 2.5h10A1.5 1.5 0 0 1 14.5 4v8a1.5 1.5 0 0 1-1.5 1.5H3A1.5 1.5 0 0 1 1.5 12V4A1.5 1.5 0 0 1 3 2.5Zm3.5 0v11" fill="none" stroke="currentColor" strokeWidth="1.2" />
      <path d="M4.3 5.2 2.9 8l1.4 2.8" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function PlusIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true" focusable="false">
      <path d="M7 2.5v9M2.5 7h9" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  )
}

function TrashIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 14 14" aria-hidden="true" focusable="false">
      <path d="M2.5 4h9M5.5 4V2.8h3V4m-4.8 0 .5 7.2h5.6l.5-7.2" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
