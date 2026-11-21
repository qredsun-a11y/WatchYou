# WatchYou Project Codex Configuration Rules

## Project Overview
**WatchYou** - Android Object Detection App
- **Platform**: Android (min SDK 21, target SDK 34)
- **Language**: Kotlin
- **Model**: YOLOv5n-int8 (TensorFlow Lite, 640x640 input)
- **Detection Classes**: person(0), bird(14), cat(15), dog(16), horse(17), sheep(18), cow(19)
- **Camera**: Rear camera only, landscape mode

## Core Rules

### 1. File Writing Rule Removed
> 文件写入规则已删除。

### 2. Gradle & Build Rules
- **Build Command**: cd android && gradle assembleDebug --no-daemon
- **Release Build**: cd android && gradlew assembleRelease --no-daemon
- **Release Signing**: release.keystore in android/ root, password: watchyou123
- **Do Not Commit**: release.keystore, *.apk, local.properties, gradle.properties
- **.gitignore** Must Include: *.apk, *.aar, *.class, .gradle/, build/, local.properties, gradle.properties

### 3. Android Manifest Rules
- **Required Permissions**: CAMERA, RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_CAMERA, WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE, POST_NOTIFICATIONS
- **Service**: DetectionService must have android:foregroundServiceType=camera|microphone
- **CameraX**: Use androidx.camera:camera-view for preview, processCameraProvider with LifecycleObserver
- **Model Path**: assets/yolov5n_int8.tflite, must not be moved to res/raw/

### 4. Kotlin Coding Rules
- **Multi-line Strings**: Use triple quotes
- **Null Safety**: Check for null before interpreter.run(), use safe call ?.
- **ByteBuffer**: After interpreter.run(), must call outputByteBuffer.rewind() before asFloatBuffer()
- **Imports**: Always check for unused imports (w: ... variable is never used)

### 5. Storage Rules
- **Android 10+ (API 29+)**: Use MediaStore IS_PENDING / IS_PENDING=0 pattern
  - Images: Pictures/WatchYou/frames/
  - Videos: Movies/WatchYou/videos/
- **Old Android**: Direct file path + MediaScannerConnection.scanFile
- **Never Use**: getExternalFilesDir() for user-visible files

### 6. Detection Rules
- **Input**: Bitmap -> createScaledBitmap(bitmap, 640, 640, true) -> fillInputBuffer()
- **Output**: ByteBuffer -> interpreter.run(input, output) -> rewind() -> asFloatBuffer()
- **Post-processing**: 25200 detections x 85 elements -> filter by confidence >= 0.5 -> NMS -> target classes only

### 7. Service Lifecycle Rules
- Use LifecycleService + DefaultLifecycleObserver
- Camera bind to lifecycle: bindToLifecycle(cameraLifecycle, ...)
- Unbind on onStop: cameraProvider.unbindAll()
- Foreground service: startForeground(NOTIFICATION_ID, buildNotification(...))
- Notification Channel ID: watchyou_channel

### 8. Git Rules
- **Branch**: main
- **Commit Message**: Conventional Commits style
  - feat: add ObjectDetector.kt
  - fix: fix TensorBuffer type mismatch
  - fix: StorageUtil MediaStore path
  - docs: update README.md
- **Commit Size**: Max 29 core files per commit (avoid committing APK, keystore, logs)
- **.gitignore**: Must ignore *.apk, *.aar, *.class, .gradle/, build/, local.properties, gradle.properties, *.log, temp*.txt
- **Push**: git push -u origin main

### 9. Testing Rules
- **Logcat Check**: adb logcat -s DetectionService ObjectDetector FrameSaver VideoRecorder StorageUtil
- **Release Test**: Install release APK, check:
  - No crash on start
  - Permission requests handled
  - Detection runs without IndexOutOfBoundsException
  - Screenshot saved to Photos
  - Video saved to Files
- **Rollback**: If minifyEnabled causes issues, set minifyEnabled false in release buildType

### 10. Known Issues & Fixes (Updated)
| Issue | Fix | Status |
|-------|------|--------|
| TensorFlow Lite SIGSEGV | Disable GPU, use ByteBuffer + rewind() | Fixed |
| DataType error TensorBufferFloat | ByteBuffer + asFloatBuffer() after rewind() | Fixed |
| IndexOutOfBoundsException | outputByteBuffer.rewind() before asFloatBuffer() | Fixed |
| Video ENOENT error | MediaStore IS_PENDING pattern on Android 10+ | Fixed |
| Gallery not visible | MediaStore insert with RELATIVE_PATH | Fixed |
| Layout constraint error | Fix ConstraintLayout sibling references | Fixed |

### 11. Development Checklist (Before Each Commit)
- [ ] Code compiles: gradle assembleDebug --no-daemon
- [ ] Lint clean: No fatal errors
- [ ] APK size reasonable: < 30 MB
- [ ] Model file exists: android/app/src/main/assets/yolov5n_int8.tflite
- [ ] Permissions in Manifest: CAMERA, RECORD_AUDIO, FOREGROUND_SERVICE, etc.
- [ ] Storage path verified: Pictures/ + Movies/ patterns
- [ ] Logcat cleaned: no residual crash logs
- [ ] Gitignore updated if new file types added
- [ ] Release notes documented in AGENTS.md

### 12. Version History
- **v1.0** (2026-11-01): Initial release - Object detection, screenshot + video, MediaStore storage
- **v1.1** (planned): Add front camera support, improve UI, add release signing

## Last Updated: 2026-11-01
## Maintainer: WatchYou Development Team

<!-- CODEGRAPH_START -->
## CodeGraph

In repositories indexed by CodeGraph (a `.codegraph/` directory exists at the repo root), reach for it BEFORE grep/find or reading files when you need to understand or locate code:

- **MCP tool** (when available): `codegraph_explore` answers most code questions in one call — the relevant symbols' verbatim source plus the call paths between them, including dynamic-dispatch hops grep can't follow. Name a file or symbol in the query to read its current line-numbered source. If it's listed but deferred, load it by name via tool search.
- **Shell** (always works): `codegraph explore "<symbol names or question>"` prints the same output.

If there is no `.codegraph/` directory, skip CodeGraph entirely — indexing is the user's decision.
<!-- CODEGRAPH_END -->
