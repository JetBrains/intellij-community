// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import { useEffect, useState } from "react"
import { ThreadListItemPrimitive, ThreadListPrimitive } from "@assistant-ui/react"
import type { AcpChat } from "../runtime/useAcpChat"

type ChatListProps = {
  chat: Pick<AcpChat, "chatListSupported" | "chatListLoading" | "chatListHasMore" | "chatListCanDelete" | "loadMoreChats">
}

export function ChatList({ chat }: ChatListProps) {
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(true)
  const chatListAvailable = chat.chatListSupported
  const sidebarExpanded = chatListAvailable && sidebarOpen

  useEffect(() => {
    if (!drawerOpen) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setDrawerOpen(false)
    }
    document.addEventListener("keydown", onKeyDown)
    return () => document.removeEventListener("keydown", onKeyDown)
  }, [drawerOpen])

  useEffect(() => {
    if (!chatListAvailable) setDrawerOpen(false)
  }, [chatListAvailable])

  const closeDrawer = () => setDrawerOpen(false)
  const openDrawer = () => {
    if (chatListAvailable) setDrawerOpen(true)
  }
  const toggleSidebar = () => {
    if (chatListAvailable) setSidebarOpen(open => !open)
  }
  return (
    <>
      {chatListAvailable ? (
        <aside className="acpChatListSidebar" data-open={sidebarOpen ? "true" : "false"} aria-label="Chats">
          <ChatListPanel chat={chat} />
        </aside>
      ) : null}
      <button
        type="button"
        className="acpChatListToggle acpChatListSidebarTrigger"
        aria-label={sidebarExpanded ? "Close chats" : "Open chats"}
        title={sidebarExpanded ? "Close chats" : "Open chats"}
        aria-expanded={sidebarExpanded}
        disabled={!chatListAvailable}
        onClick={toggleSidebar}
      >
        {sidebarExpanded ? <ChevronLeftIcon /> : <ChevronRightIcon />}
      </button>
      <button
        type="button"
        className="acpChatListToggle acpChatListDrawerTrigger"
        aria-label="Open chats"
        title="Open chats"
        aria-expanded={drawerOpen}
        disabled={!chatListAvailable}
        onClick={openDrawer}
      >
        <ChevronRightIcon />
      </button>
      <div className="acpChatListOverlay" data-open={drawerOpen ? "true" : "false"} aria-hidden={!drawerOpen}>
        <button type="button" className="acpChatListBackdrop" aria-label="Close chats" onClick={closeDrawer} />
        <div className="acpChatListDrawerShell">
          <aside className="acpChatListDrawer" aria-label="Chats">
            {chatListAvailable ? <ChatListPanel chat={chat} onNavigate={closeDrawer} /> : null}
          </aside>
          <button
            type="button"
            className="acpChatListToggle acpChatListDrawerCloseTrigger"
            aria-label="Close chats"
            title="Close chats"
            aria-expanded={drawerOpen}
            onClick={closeDrawer}
          >
            <ChevronLeftIcon />
          </button>
        </div>
      </div>
    </>
  )
}

function ChatListPanel(props: ChatListProps & { onNavigate?: () => void }) {
  const { chat, onNavigate } = props
  return (
    <ThreadListPrimitive.Root className="acpChatListRoot">
      <div className="acpChatListHeader">
        <span className="acpChatListTitle">Chats</span>
        <ThreadListPrimitive.New className="acpChatListNew" aria-label="New chat" title="New chat" onClick={() => onNavigate?.()}>
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

function ChevronRightIcon() {
  return (
    <svg width="8" height="16" viewBox="0 0 8 16" aria-hidden="true" focusable="false">
      <path d="M2.25 4.5 5.75 8 2.25 11.5" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

function ChevronLeftIcon() {
  return (
    <svg width="8" height="16" viewBox="0 0 8 16" aria-hidden="true" focusable="false">
      <path d="M5.75 4.5 2.25 8 5.75 11.5" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
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
