" characterize.vim - Unicode character metadata
" Maintainer:   Tim Pope
" Version:      1.0

if exists("g:loaded_characterize") || v:version < 700 || &cp
  finish
endif
let g:loaded_characterize = 1

function! s:info(char)
  if empty(a:char)
    return 'NUL'
  endif
  let charseq = a:char
  let outs = []
  while !empty(charseq)
    let nr = charseq ==# "\n" ? 0 : char2nr(charseq)
    let char = nr < 32 ? '^'.nr2char(64 + nr) : nr2char(nr)
    let charseq = strpart(charseq, nr ? len(nr2char(nr)) : 1)
    let out = '<' . (empty(outs) ? '' : ' ') . char . '> ' . nr
    if nr < 256
      let out .= printf(', \%03o', nr)
    endif
    let out .= printf(', U+%04X', nr)
    let out .= ' '.characterize#description(nr, '<unknown>')
    for digraph in characterize#digraphs(nr)
      let out .= ", \<C-K>".digraph
    endfor
    for emoji in characterize#emojis(nr)
      let out .= ', '.emoji
    endfor
    let entity = characterize#html_entity(nr)
    if !empty(entity)
      let out .= ', '.entity
    endif
    call add(outs, out)
  endwhile
  return join(outs, ' ')
endfunction

nnoremap <silent><script> <Plug>(characterize) :<C-U>echo <SID>info(matchstr(getline('.')[col('.')-1:-1],'.'))<CR>
if !hasmapto('<Plug>(characterize)', 'n')
  nmap ga <Plug>(characterize)
endif

" vim:set sw=2 et:
