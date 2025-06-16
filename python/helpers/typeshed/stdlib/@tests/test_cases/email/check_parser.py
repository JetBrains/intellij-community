import email.policy
from email.message import EmailMessage, Message
from email.parser import BytesParser, Parser
from typing_extensions import assert_type

p1 = Parser()
p2 = Parser(policy=email.policy.default)

assert_type(p1, Parser[Message[str, str]])
assert_type(p2, Parser[EmailMessage])

bp1 = BytesParser()
bp2 = BytesParser(policy=email.policy.default)

assert_type(bp1, BytesParser[Message[str, str]])
assert_type(bp2, BytesParser[EmailMessage])
