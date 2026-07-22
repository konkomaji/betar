# Native libraries — not yet present

This is where `libmesh_core.so`, cross-compiled for each Android ABI, must go before the app
can actually call into the Rust core at runtime:

```
jniLibs/
├── arm64-v8a/libmesh_core.so     (most real devices, API 21+)
├── armeabi-v7a/libmesh_core.so   (older/budget 32-bit devices)
└── x86_64/libmesh_core.so        (emulator)
```

**Status: not done.** This repo's current dev environment has no Android NDK, so `mesh-core`
has only been built for the host (`target/release/mesh_core.dll` on this machine) — that build
was used solely to generate the Kotlin bindings (`uniffi-bindgen`), not to produce an
Android-loadable library. Until the `.so` files exist here, `MainActivity`'s call to
`FfiIdentity.generate()` will throw `UnsatisfiedLinkError`. See
`docs/IMPLEMENTATION-STATUS.md`.

To fix, once the Android NDK is available:

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo install cargo-ndk
cargo ndk -o android/app/src/main/jniLibs \
  -t arm64-v8a -t armeabi-v7a -t x86_64 \
  build --release -p mesh-core
```
