# coding=utf-8
from abc import abstractmethod, ABCMeta


class A:
    __metaclass__ = ABCMeta

    @abstractmethod
    def m(self, x):
        u"""Юникод"""
        pass


class B(A):
    def m(self, x):
        u"""Юникод"""
        return x
