# Plan: Spot_Quantifier_3D_ Plugin

## 背景・目的

既存の `Maxima_Based_Segmenter_3D_` は 3D Extended Maxima の挙動が不安定で実用に至らなかった。
今回は「Watershed不要・スポットを塊として計測する」用途に特化した、より単純で堅牢な
3D スポット定量プラグインを同一プロジェクト内に追加する。

アルゴリズムは汎用的な「3D蛍光スポット定量」で、PCNT等の点状構造の輝度比較に使用する。

---

## 既存コードとの関係

### 流用（変更なし）
| クラス | 用途 |
|--------|------|
| `ui/HistogramPanel` | 3Dスタック全体ヒストグラム＋閾値UI（3D時は自動で全スライス集計） |
| `preview/PreviewRenderer.renderRoiBoundaries3D()` | 現在スライスへの ROI 境界オーバーレイ |
| `roi/RoiExporter3D` | ROI ZIP 出力 |
| `alg/SegmentationResult3D` | ラベル画像の wrapper（再利用） |
| ビルド・インストール構成 (`scripts/`) | そのまま使用 |

### 使わない（新プラグインには不要）
- `MarkerBuilder3D` — Extended Maxima → 不使用
- `Watershed3DRunner` — Watershed → 不使用
- tolerance パラメータ

### 新規追加クラス
| クラス | 責務 |
|--------|------|
| `Spot_Quantifier_3D_.java` | プラグインエントリーポイント（GUI / マクロモード切替） |
| `SpotQuantifier3DFrame.java` | UI フレーム（HistogramPanel ＋ スライダー ＋ プレビュー） |
| `SpotQuantifier3D.java` | コアアルゴリズム（閾値→CC→ラベル画像） |
| `SpotMeasurer.java` | ラベル画像＋元画像から各スポットを計測 |
| `SpotMeasurement.java` | 計測値データクラス |
| `QuantifierParams.java` | パラメータデータクラス |
| `CsvExporter.java` | CSV 出力 ＋ params.json 出力 |

---

## アルゴリズム

```
入力: 3D スタック（16-bit）

1. [オプション] Gaussian Blur 3D（sigma XY, Z）  ← デフォルト OFF
   ↓
2. 固定閾値 → バイナリマスク（intensity >= threshold）
   ↓
3. FloodFillComponentsLabeling3D（MorphoLibJ、既存依存）
   ↓
4. サイズフィルタ（min/max voxel 数で小ゴミ・大デブリを除外）
   ↓
5. SpotMeasurer: ラベルスタックを1回スキャンして全スポットを計測
   - 積分輝度 IntDen（元画像のピクセル値の総和）
   - 体積 voxel 数 → µm³（calibration から変換）
   - 平均輝度 Mean = IntDen / voxel 数
   - 重心 XYZ（voxel 座標の重心 → µm）
   ↓
6. 出力:
   - CSV（1行 = スポット1個）
   - ROI ZIP（RoiExporter3D 流用）
   - params.json（使用パラメータを自動保存）
```

Watershed なし。隣接スポットは分離しない（塊そのままを計測単位とする）。

---

## クラス設計

### SpotMeasurement（データクラス）
```java
class SpotMeasurement {
    int    id;
    long   volumeVox;
    double volumeUm3;
    double integratedIntensity;
    double meanIntensity;
    double centroidXUm, centroidYUm, centroidZUm;
}
```

### QuantifierParams（パラメータクラス）
```java
class QuantifierParams {
    int     threshold;
    Double  minVolUm3;      // null = フィルタ無効
    Double  maxVolUm3;      // null = フィルタ無効
    boolean gaussianBlur;   // default: false
    double  gaussXY;        // gaussianBlur=true の場合のみ使用
    double  gaussZ;
}
```

### SpotQuantifier3D（コアアルゴリズム）
```java
class SpotQuantifier3D {
    // ラベル画像（SegmentationResult3D）を返す
    // サイズフィルタ後の有効ラベルのみ残す
    static SegmentationResult3D segment(ImagePlus imp, QuantifierParams params);
}
```

### SpotMeasurer（計測）
```java
class SpotMeasurer {
    // ラベルスタックを1回スキャン → 全スポットの統計を O(W×H×D) で取得
    static List<SpotMeasurement> measure(
        SegmentationResult3D seg, ImagePlus origImp,
        double vw, double vh, double vd
    );
}
```

### CsvExporter（出力）
```java
class CsvExporter {
    static void writeCsv(List<SpotMeasurement> spots, String imageName, File out);
    static void writeParams(QuantifierParams params, String imageName,
                            String timestamp, File out);  // → params.json
}
```

---

## UI 設計

`SimpleSegmenterFrame` をベースに、不要なものを削ぎ落とした構成。

```
┌──────────────────────────────────┐
│  [Histogram（全スタック）         │  ← HistogramPanel 流用
│   閾値ラインをドラッグで操作]     │    fg ライン無効（setFgEnabled(false)）
│                                  │
│  Threshold:    [====|====] [____]│
│  [☑] Min vol: [==|=====]  [____]│ µm³  ← チェックで有効/無効
│  [☑] Max vol: [=======|=] [____]│ µm³  ← チェックで有効/無効
│  [□] Gaussian blur  XY[_] Z[_]  │  ← デフォルト OFF
│                                  │
│  Preview:  ○ Off  ● Overlay     │
│                                  │
│  [Apply]  [Add ROI]  [Save ROI] │
│  [Save CSV & Params]             │  ← CSV + params.json を同時保存
└──────────────────────────────────┘
```

### プレビューの仕組み：色分けオーバーレイ

閾値を超えた全 CC を現スライスに塗りつぶし表示し、サイズフィルタ通過状況で色分け：

| 状態 | 色 |
|------|----|
| フィルタ通過（有効スポット） | 黄（不透明度 50%） |
| 小さすぎ（min vol 未満） | 赤 |
| 大きすぎ（max vol 超過） | 青 |
| サイズフィルタ無効時 | 全て黄 |

実装：
1. CC ラベリング → ラベルごとの voxel 数を集計（O(W×H×D) 1スキャン）
2. 各ラベルのステータスを判定（有効 / 小 / 大）
3. 現スライスの各ピクセルをラベルステータスで塗る → `ColorProcessor` → `ImageRoi`
4. Overlay として imp に設定（既存 `renderMarkerFill` と同じ方式）

**キャッシュ戦略：**
- キャッシュキー: `threshold + gaussEnabled + gaussXY + gaussZ`
  （サイズフィルタはキャッシュキーに含めない → CC は再計算不要、色だけ変える）
- 閾値・gaussian 変更 → debounce (200ms) → CC 再計算 → プレビュー更新
- サイズフィルタ変更 → キャッシュ済みラベル画像で色のみ再描画 → 即時更新
- Z スライス移動 → キャッシュ済みラベル画像で色のみ再描画 → 即時更新

### サイズフィルタのオン/オフ
- min / max それぞれ独立したチェックボックス（デフォルト両方 ON）
- チェックを外すと上限 or 下限なしで通過
- 小さいノイズが少ない高S/N データでは max のみ有効など柔軟に対応可能

### スライダーの範囲
- min/max vol スライダーの範囲は CC 結果から動的に設定
  （検出された最小体積〜最大体積をスライダーレンジとする）

### Save CSV & Params ボタン
- ファイル保存先ダイアログ
- `[basename]_spots.csv` と `[basename]_params.json` を同じディレクトリに出力

---

## 拡張候補（現在スコープ外）

- **球形度フィルタ（Sphericity）**: 非球形のデブリを除外するフィルタ。
  `sphericity = π^(1/3) × (6V)^(2/3) / A`（V=体積, A=表面積）。
  現時点では不要だが、将来的に追加しやすい構造にしておく。

---

## 出力ファイル

| ファイル | 内容 |
|---------|------|
| `[image]_spots.csv` | id, volume_um3, volume_vox, integrated_intensity, mean_intensity, centroid_x/y/z_um |
| `[image]_RoiSet.zip` | ROI Manager 用（RoiExporter3D 流用） |
| `[image]_params.txt` | 使用パラメータ（.env スタイル、再現性記録） |

### params フォーマット（.env スタイル）

```properties
# Spot Quantifier 3D — params
# TIMESTAMP=2026-03-11T10:00:00
IMAGE=Series001_ch2.tif
THRESHOLD=1000
MIN_VOL_UM3=0.1
MAX_VOL_UM3=
GAUSSIAN_BLUR=false
GAUSS_XY=
GAUSS_Z=
VOXEL_X_UM=0.0707
VOXEL_Y_UM=0.0707
VOXEL_Z_UM=0.2999
```

- 空値（`KEY=`）= 無効 / null
- `#` 行はコメント
- ファイル名: `[basename]_params.txt`

---

## マクロとの関係（バッチ処理）

プラグインはシングルファイル操作に専念。
バッチ処理は `PCNT_Quantify.ijm` がプラグインのラッパーとして担う。

### プラグイン実装後のマクロの役割

```
PCNT_Quantify.ijm
  ├── params.txt を読み込む
  ├── SingleChannel フォルダ内の *_ch2.tif をループ
  └── 各ファイルに対して run("Spot Quantifier 3D", ...) を呼ぶだけ
```

マクロは薄いバッチラッパーになり、処理ロジックは全てプラグイン内に集約される。

### プラグイン実装後のマクロイメージ

```ijm
inputDir  = getDirectory("Select SingleChannel folder");
paramsPath = inputDir + "../../Macros/" + sessionName + "_params.txt";

threshold = readParam(paramsPath, "THRESHOLD");
minVol    = readParam(paramsPath, "MIN_VOL_UM3");
maxVol    = readParam(paramsPath, "MAX_VOL_UM3");

list = getFileList(inputDir);
for (i = 0; i < list.length; i++) {
    if (!endsWith(list[i], "_ch2.tif")) continue;
    open(inputDir + list[i]);
    run("Spot Quantifier 3D",
        "threshold=" + threshold +
        " min_vol=" + minVol +
        " max_vol=" + maxVol +
        " output=[" + inputDir + "]");
    close();
}
```

### マクロモード用プラグイン引数

```
threshold=1000 min_vol=0.1 max_vol= gaussian_blur=false output=[/path/]
```

- 空値（`max_vol=`）= 無効（params.txt の空値と対応）

---

## 実装順序

1. `SpotMeasurement.java` + `QuantifierParams.java` — データクラス（依存なし）
2. `SpotQuantifier3D.java` — コアアルゴリズム（単体テスト可能）
3. `SpotMeasurer.java` — 計測ロジック（単体テスト可能）
4. `CsvExporter.java` — 出力（単体テスト可能）
5. `Spot_Quantifier_3D_.java` — エントリーポイント（マクロモード）
6. `SpotQuantifier3DFrame.java` — UI フレーム
7. テスト追加
8. ビルド・インストール確認・動作確認（PCNT データで検証）

---

## 未決事項（解決済み）

- [x] HistogramPanel のヒストグラム範囲
  → 3Dスタック時は `ensureHistogram()` が自動で全スライス集計 → そのまま流用可
- [x] プレビュー時の Gaussian blur
  → デフォルト OFF + S/N 比が高いため、プレビューでも blur なしで問題なし
- [x] バッチ処理の所在
  → プラグイン外（マクロに任せる）。プラグインはシングルファイル操作に専念
