# Native libraries

`libmesh_core.so`, cross-compiled for each Android ABI, so the app can call into the Rust core
at runtime:

```
jniLibs/
├── arm64-v8a/libmesh_core.so     (most real devices, API 21+)
├── armeabi-v7a/libmesh_core.so   (older/budget 32-bit devices)
└── x86_64/libmesh_core.so        (emulator)
```

**Status: built.** Cross-compiled with `cargo-ndk` against NDK r27c. `./gradlew assembleDebug`
succeeds end to end (`docs/PROGRESS.md`) with these libraries packaged into the APK.

`i686-linux-android` (32-bit x86 emulator) target is installed via rustup but not built into
`jniLibs/` by default — add `-t x86` to the command below if a 32-bit emulator image is needed.

`core/` is a git submodule pointing at [project-mesh](https://github.com/konkomaji/project-mesh)
— run `git submodule update --init` first if it's empty. To regenerate after changing it, from
the repo root:

```sh
cd core && cargo ndk -o ../app/src/main/jniLibs \
  -t arm64-v8a -t armeabi-v7a -t x86_64 \
  build --release -p mesh-core
```

(Requires `ANDROID_NDK_HOME` set, e.g.
`C:\Users\<you>\AppData\Local\Android\Sdk\ndk\27.2.12479018`.)
