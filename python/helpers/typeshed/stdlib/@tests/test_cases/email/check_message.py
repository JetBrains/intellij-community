from email.headerregistry import Address
from email.message import EmailMessage
from typing_extensions import assert_type

msg = EmailMessage()
msg["To"] = "receiver@example.com"
msg["From"] = Address("Sender Name", "sender", "example.com")

for a in msg.iter_attachments():
    assert_type(a, EmailMessage)
