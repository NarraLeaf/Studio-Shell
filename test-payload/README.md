# test-payload

The self-check payload. Load it in a shell — or serve it over plain HTTP — and
every part of the serving contract a game silently depends on is exercised and
reported on screen.

It is not a game: it is what proves a shell build is fit to run one.

## What it checks, and why each one matters

| Check | Why the game breaks without it |
| --- | --- |
| Secure context | IndexedDB — where saves live — is unavailable outside one |
| Content-Type inference | A module script served as the wrong type never executes |
| Range → 206, exact slice | Audio and video cannot seek; scrubbing is dead |
| Suffix (`bytes=-N`) and open-ended (`bytes=N-`) ranges | Media elements ask for both shapes |
| `Accept-Ranges` | Some players will not attempt seeking without it |
| 416 on an unsatisfiable range | A misbehaving player hangs instead of recovering |
| 404 on a missing file | The runtime's own asset-failure handling never fires |
| Path traversal refused | The payload root must be the whole world |
| IndexedDB read/write | No saves |
| ES module import | The renderer bundle is modules |
| Audio load + seek | The end-to-end proof that Range works through the media stack |
| Autoplay without a gesture | The opening track never sounds |

## Running it

**In a shell (the real test).** Repack or side-load a template with this
directory as the payload, launch it, and read the summary. The page also sets
`document.title` to `SELFCHECK PASS` / `SELFCHECK FAIL` and exposes
`window.__SHELL_SELFCHECK__ = { failed, done }`, so a smoke test can assert
without a human looking.

**Over HTTP (a sanity check of the page itself).**

```sh
npx http-server test-payload/www -p 8899 -c-1
open http://127.0.0.1:8899/
```

> `http-server` does not implement suffix ranges (`bytes=-N`) and answers 416,
> so that one check fails there. This is a gap in that server, not in the page
> or the shells: RFC 7233 §2.1 defines the form and both shells implement it.
> Everything else passing over HTTP means the page's own logic is sound.

Python's `http.server` is not usable as a reference at all — it has no Range
support, which is the thing most worth testing.
