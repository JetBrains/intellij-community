from django.core.mail import EmailMessage
# https://docs.djangoproject.com/en/1.10/topics/testing/tools/#django.core.mail.django.core.mail.outbox
outbox = [EmailMessage()]
