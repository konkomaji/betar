# Contributing to Betar

Betar is pre-alpha and moving fast. Before sending a pull request, please open an issue
first for anything beyond a small fix — it saves rework on both sides.

## Scope

This repo is the Android app only. Protocol, cryptography, and mesh-engine changes
belong in [Project Mesh](https://github.com/betarcomms/project-mesh) (`core/` here is a
submodule pointing at it).

## Building

See [`README.md`](README.md).

## Code style

Match the existing Kotlin/Compose style in the codebase. Keep commit messages honest
about what's verified and what isn't — this project doesn't claim something works
without saying how it was checked (a passing build, a real device run, or "compiles
only, not yet run on hardware").

## Security

Do not open a public issue for a security vulnerability. See [`SECURITY.md`](SECURITY.md).
