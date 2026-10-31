# Betar

Betar (বেতার, Bengali for "wireless" — literally *be-* without, *tar* wire) is an offline
mesh messaging and safety app: chat, an SOS flow, a disaster bulletin board, a community
resource board, and an offline map, all working over Bluetooth LE and Wi-Fi Direct with
no SIM, no internet, and no server.

It's built on [Project Mesh](https://github.com/konkomaji/project-mesh), the open Rust
protocol and research core underneath it — Betar is the Android app; Project Mesh is
where the protocol design, cryptography, and research live.

## Status

**Pre-alpha.** Debug-signed and release-signed builds both exist (see
[Releases](https://github.com/konkomaji/betar/releases)), but there has been no
independent security audit yet and no two-device hardware verification. Don't rely on
this for anything real yet.

## Getting the code

`core/` is a git submodule pointing at Project Mesh:

```sh
git clone --recurse-submodules https://github.com/konkomaji/betar.git
```

or, if already cloned:

```sh
git submodule update --init
```

## Building

Requires the Rust core cross-compiled first — see
[`app/src/main/jniLibs/README.md`](app/src/main/jniLibs/README.md). Then:

```sh
./gradlew assembleDebug
```

## License

AGPL-3.0-or-later, see [`LICENSE`](LICENSE).
