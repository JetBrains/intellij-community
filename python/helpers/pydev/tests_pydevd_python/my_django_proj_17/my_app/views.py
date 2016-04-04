from django.shortcuts import render

# Create your views here.
from django.http import HttpResponse
import sys

class Entry(object):

    def __init__(self, key, val):
        self.key = key
        self.val = val

    def __unicode__(self):
        return u'%s:%s' % (self.key, self.val)

    def __str__(self):
        return u'%s:%s' % (self.key, self.val)

def index(request):
    context = {
        'entries': [Entry('v1', 'v1'), Entry('v2', 'v2')]
    }
    ret = render(request, 'my_app/index.html', context)
    return ret