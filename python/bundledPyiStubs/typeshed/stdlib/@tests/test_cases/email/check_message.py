from email.headerregistry import Address, BaseHeader
from email.message import EmailMessage, MIMEPart
from typing_extensions import assert_type

msg = EmailMessage()
msg["To"] = "receiver@example.com"
msg["From"] = Address("Sender Name", "sender", "example.com")

for a in msg.iter_attachments():
    assert_type(a, EmailMessage)

generic_msg: EmailMessage[BaseHeader, str] = EmailMessage()
assert_type(generic_msg.get("To"), BaseHeader | None)
assert_type(generic_msg.get_body(), MIMEPart[BaseHeader, str] | None)
for a in generic_msg.iter_attachments():
    assert_type(a, EmailMessage[BaseHeader, str])
for p in generic_msg.iter_parts():
    assert_type(p, MIMEPart[BaseHeader, str])
