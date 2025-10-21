from email.mime.text import MIMEText
from email.policy import SMTP

msg = MIMEText("", policy=SMTP)
