" Vimball Archiver by Charles E. Campbell, Jr., Ph.D.
UseVimball
finish
plugin/textobj/rubyblock.vim	[[[1
58
if exists('g:loaded_textobj_rubyblock')  "{{{1
  finish
endif

" Interface  "{{{1
call textobj#user#plugin('rubyblock', {
\      '-': {
\        '*sfile*': expand('<sfile>:p'),
\        'select-a': 'ar',  '*select-a-function*': 's:select_a',
\        'select-i': 'ir',  '*select-i-function*': 's:select_i'
\      }
\    })

" Misc.  "{{{1
let s:comment_escape = '\v^[^#]*'
let s:block_openers = '\zs(<def>|<if>|<do>|<module>|<class>)'
let s:start_pattern = s:comment_escape . s:block_openers
let s:end_pattern = s:comment_escape . '\zs<end>'
let s:skip_pattern = 'getline(".") =~ "\\w\\s\\+if"'

function! s:select_a()
  let s:flags = 'W'

  call searchpair(s:start_pattern,'',s:end_pattern, s:flags, s:skip_pattern)
  let end_pos = getpos('.')

  " Jump to match
  normal %
  let start_pos = getpos('.')

  return ['V', start_pos, end_pos]
endfunction

function! s:select_i()
  let s:flags = 'W'
  if expand('<cword>') == 'end'
    let s:flags = 'cW'
  endif

  call searchpair(s:start_pattern,'',s:end_pattern, s:flags, s:skip_pattern)

  " Move up one line, and save position
  normal k^
  let end_pos = getpos('.')

  " Move down again, jump to match, then down one line and save position
  normal j^%j
  let start_pos = getpos('.')

  return ['V', start_pos, end_pos]
endfunction

" Fin.  "{{{1

let g:loaded_textobj_rubyblock = 1

" __END__
" vim: foldmethod=marker
doc/textobj-rubyblock.txt	[[[1
151
*textobj-rubyblock.txt*	Text objects for ruby blocks

Version 0.0.1

CONTENTS					*textobj-rubyblock-contents*

Introduction		|textobj-rubyblock-introduction|
Interface		|textobj-rubyblock-interface|
Mappings		|textobj-rubyblock-mappings|
Examples		|textobj-rubyblock-examples|
Bugs			|textobj-rubyblock-bugs|
Changelog		|textobj-rubyblock-changelog|


==============================================================================
INTRODUCTION					*textobj-rubyblock-introduction*

The *textobj-rubyblock* plugin provides two new |text-objects| which are
triggered by `ar` and `ir` respectively. These follow Vim convention, so that
`ar` selects _all_ of a ruby block, and `ir` selects the _inner_ portion of a
rubyblock.

In ruby, a block is always closed with the `end` keyword. Ruby blocks may be
opened using one of several keywords, including `module`, `class`, `def` `if`
and `do`. This example demonstrates a few of these:
>
	module Foo
	  class Bar
	    def Baz
	      [1,2,3].each do |i|
	        i + 1
	      end
	    end
	  end
	end
<
Suppose your cursor was positioned on the word `def` in this snippet. Typing
`var` would enable visual mode selecting _all_ of the method definition. Your
selection would comprise the following lines:
>
	def Baz
	  [1,2,3].each do |i|
	    i + 1
	  end
	end
<
Whereas if you typed `vir`, you would select everything _inside_ of the method
definition, which looks like this:
>
	[1,2,3].each do |i|
	  i + 1
	end
<
Note that the `ar` and `ir` text objects always enable _visual line_ mode,
even if you were in visual character or block mode before you triggered the
rubyblock text object.

Note too that the `ar` and `ir` text objects always position your cursor on
the `end` keyword. If you want to move to the top of the selection, you can do
so with the `o` key.

# Limitations #

Some text objects in Vim respond to a count. For example, the `a{` text object
will select _all_ of the current `{}` delimited block, but if you prefix it
with the number 2 (e.g. `v2i{`) then it will select all of the block that
contains the current block. The rubyblock text object does not respond in this
way if you prefix a count. This is due to a limitation in vimscript #2100.

However, you can achieve a similar effect by repeating the rubyblock
text-object manually. So if you press `var` to select the current ruby block,
you can expand your selection outwards by repeating `ar`, or contract your
selection inwards by repeating `ir`.


# Requirements: #

- Vim 7.2 or later
- |textobj-user| 0.3.7 or later (vimscript#2100)
- |matchit.vim|

Matchit.vim is distributed with Vim, but is not enabled by default. If you add
the following line to your vimrc file, then it will enable matchit.vim each
time Vim starts up:
>
    runtime macros/matchit.vim
<
Latest version:
http://github.com/nelstrom/vim-textobj-rubyblock


==============================================================================
INTERFACE					*textobj-rubyblock-interface*

------------------------------------------------------------------------------
MAPPINGS					*textobj-rubyblock-mappings*

These key mappings are defined in Visual mode and Operator-pending mode.

<Plug>(textobj-rubyblock-a)			*<Plug>(textobj-rubyblock-a)*
	Select the ruby block including the opening and closing lines.

<Plug>(textobj-rubyblock-i)			*<Plug>(textobj-rubyblock-i)*
	Select the inner lines of a ruby block. The opening and closing
	lines are not included.

==============================================================================
CUSTOMIZING					*textobj-rubyblock-customizing*

				*g:textobj_rubyblock_no_default_key_mappings*
					*:TextobjRubyblockDefaultKeyMappings*

	This plugin will define the following key mappings in Visual mode and
	Operator-pending mode automatically.  If you don't want these key
	mappings, define |g:textobj_rubyblock_no_default_key_mappings| before
	this plugin is loaded (e.g. in your |vimrc|).  You can also use
	|:TextobjRubyblockDefaultKeyMappings| to redefine these key mappings.
	This command doesn't override existing {lhs}s unless [!] is given.

	{lhs}	{rhs}			~
	-----	----------------------	~
	ar	<Plug>(textobj-rubyblock-a)
	ir	<Plug>(textobj-rubyblock-i)

	Suppose that you didn't like using `ar` and `ir` to trigger the
	rubyblock text objects, and instead wanted to map them to `ae` and
	`ie`. You could achieve this by placing the following in your vimrc
	file:

	let g:textobj_rubyblock_no_default_key_mappings = 1
	xmap ae  <Plug>(textobj-rubyblock-a)
	omap ae  <Plug>(textobj-rubyblock-a)
	xmap ie  <Plug>(textobj-rubyblock-i)
	omap ie  <Plug>(textobj-rubyblock-i)

==============================================================================
BUGS						*textobj-rubyblock-bugs*

- [count] is just ignored.

- See |textobj-user-bugs| for further information.

==============================================================================
CHANGELOG					*textobj-rubyblock-changelog*

0.0.1	2010-12-27
	- First release.

==============================================================================
vim:tw=78:ts=8:ft=help:norl:fen:fdl=0:fdm=marker:
