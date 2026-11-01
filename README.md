# WatchYou - Android Object Detection App

基于 TensorFlow Lite + YOLOv5n 的 Android 实时目标检测应用。

## 功能
- 实时目标检测 (YOLOv5n-int8)
- 后置摄像头预览 (CameraX)
- 目标类别: person, bird, cat, dog, horse, sheep, cow
- 自动截图保存到 Pictures/WatchYou/frames/
- 自动录像保存到 Movies/WatchYou/videos/
- 前台服务持续运行

## 构建
cd android && gradle assembleDebug --no-daemon

## 安装
adb install -r app/build/outputs/apk/debug/app-debug.apk

## 核心文件
- ObjectDetector.kt - TFLite 推理核心
- DetectionService.kt - 前台检测服务
- FrameSaver.kt / VideoRecorder.kt - MediaStore 存储
- StorageUtil.kt - 公共存储路径
- MainActivity.kt - 主界面

## 权限
CAMERA, FOREGROUND_SERVICE, FOREGROUND_SERVICE_CAMERA, WAKE_LOCK, POST_NOTIFICATIONS

## 模型
YOLOv5n-int8, 输入 [1,640,640,3], 输出 [1,25200,85]
