# 模型与声音资源

## 目标检测模型（TFLite int8）

### 推荐：YOLOv5n-int8 (COCO 80 类)
- 大小：约 4 MB
- 来源：yolov5/export.py --weights yolov5n.pt --include tflite --int8
- 已在本项目 G:\StudyS\yolov5\yolov5n.pt 可直接导出
- 类别映射（仅关注人/动物）：
  - 0: person
  - 14: bird
  - 15: cat
  - 16: dog
  - 17: horse
  - 18: sheep
  - 19: cow
- 放置位置：android/app/src/main/assets/yolov5n_int8.tflite

### 备选：EfficientDet-Lite0 int8 (TF Hub)
- 大小：约 5 MB
- 类别：COCO 90 类，人/动物覆盖相同
- 下载：https://tfhub.dev/tensorflow/lite-model/efficientdet/lite0/detection/metadata/1

## 声音提醒资源

### 默认警报音
- 文件：alert.wav (16kHz, 单声道, 1-2 秒)
- 放置：android/app/src/main/res/raw/alert.wav (Android)
- 放置：python/assets/alert.wav (PC)

### 可选语音提示（中文 TTS 生成）
- person_detected.wav - 检测到有人
- animal_detected.wav - 检测到动物
- 生成工具：edge-tts / 微软 Azure TTS / 百度语音合成

## 获取方式

`ash
# 1. 导出 YOLOv5n int8 TFLite (需安装 yolov5 环境)
cd G:\StudyS\yolov5
python export.py --weights yolov5n.pt --include tflite --int8 --data data/coco.yaml
# 产出 yolov5n_saved_model/yolov5n_int8.tflite

# 2. 或直接下载预转换模型
# https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov5n_int8.tflite

# 3. 声音文件
# 使用在线 TTS 生成或录制
`

## Android 资源目录结构
`
android/app/src/main/
├── assets/
│   └── yolov5n_int8.tflite       # 必须
├── res/raw/
│   └── alert.wav                 # 必须
└── res/values/strings.xml        # 类别名称映射
`

## Python 资源目录结构
`
python/
├── assets/
│   ├── yolov5n_int8.tflite       # 可选（ultralytics 自动下载）
│   └── alert.wav                 # 必须
`
