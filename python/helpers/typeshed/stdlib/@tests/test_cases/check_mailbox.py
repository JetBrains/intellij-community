import mailbox


def mbox1() -> mailbox.Mailbox:
    return mailbox.mbox("")


def mbox2() -> mailbox.Mailbox[mailbox.mboxMessage]:
    return mailbox.mbox("")


def mbox3() -> mailbox.Mailbox[mailbox.Message]:
    return mailbox.mbox("")
