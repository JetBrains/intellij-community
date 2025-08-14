from django.core.management.base import BaseCommand

class Command(BaseCommand):
    missing_args_message: str
