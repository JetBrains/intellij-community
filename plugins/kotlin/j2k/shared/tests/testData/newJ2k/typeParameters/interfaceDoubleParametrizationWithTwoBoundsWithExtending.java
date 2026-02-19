// IGNORE_K2
interface INode {}
interface Node {}
interface II {}

interface I <T extends INode & Comparable<? super T>, K extends Node & Collection<? extends K>> extends II {}