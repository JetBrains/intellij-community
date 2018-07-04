# coding=utf-8
from django.core.handlers.wsgi import WSGIRequest


class View(object):
    def __init__(self, *args, **kwargs):
        self.request = WSGIRequest() if False else None

        self.args = list()
        self.kwargs = dict()
