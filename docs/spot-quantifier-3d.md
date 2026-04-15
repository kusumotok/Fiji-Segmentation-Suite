# Spot Quantifier 3D — 処理詳細と手動再現手順

## 概要

`Spot_Quantifier_3D_` は 3D 共焦点スタック画像における蛍光スポット（中心体・点状シグナル等）を
**固定閾値 → 3D Connected Components → 体積フィルタ → 測定** の4ステップで定量するプラグインです。

---

## 処理ステップの詳細

### Step 1: 前処理（オプション）

`gaussian_blur=true` の場合のみ実行します。

- 元画像を複製
- `GaussianBlur3D.blur(imp, gaussXY, gaussXY, gaussZ)` で 3D Gaussian フィルタを適用
- 以降の閾値処理はこの複製に対して行う（元画像は測定専用として保持）

> **注意**: 閾値・バイナリ化・CC計算はぼかし後の画像で行い、輝度測定（IntDen・Mean）は**元画像**のピクセル値を使用します。

---

### Step 2: 固定閾値 → バイナリマスク

```
各ボクセル (x, y, z) について:
  ip.get(x, y) >= threshold  →  binary[x][y] = 255
  ip.get(x, y) <  threshold  →  binary[x][y] = 0
```

- `ip.get(x, y)` は ImageJ の `ImageProcessor.get()` — 16-bit 整数値をそのまま取得
- スタック全スライスにわたって同じ閾値を適用
- 結果は 8-bit バイナリスタック（0 または 255）

---

### Step 3: 3D Connected Components ラベリング

MorphoLibJ の `BinaryImages.componentsLabeling()` を使用します。

```java
ImagePlus labelImp = BinaryImages.componentsLabeling(binaryImp, 26, 32);
```

| パラメータ | 値 | 意味 |
|-----------|-----|------|
| connectivity | 26 | 26近傍（面・辺・頂点で接するボクセルを同一CCとみなす） |
| bitDepth | 32 | 32-bit float ラベル（65535超のCC数に対応） |

- 出力はラベル画像（各ボクセルの値 = CC のラベル番号、背景 = 0）
- ラベル番号は 1 から始まり連続整数

**26近傍接続性について:**
3×3×3 の近傍 26ボクセルすべてを隣接とみなします。PSF の影響で Z方向に伸びたシグナルも1つの CC として統合されます。

---

### Step 4: 体積フィルタ（分類）

ラベル画像を1回スキャンし、ラベルごとにボクセル数をカウントします。

```
voxelVol_um3 = pixelWidth × pixelHeight × pixelDepth   [µm³]
label_vol_um3 = voxelCount[label] × voxelVol_um3

分類:
  label_vol_um3 < minVolUm3  →  TOO_SMALL  (赤)
  label_vol_um3 > maxVolUm3  →  TOO_LARGE  (青)
  それ以外                    →  VALID      (黄)
```

- `minVolUm3 = null`（チェックOFF）の場合は下限なし
- `maxVolUm3 = null`（チェックOFF）の場合は上限なし
- VALID ラベルのみを保持したフィルタ済みラベル画像を生成（その他は 0 に置換）

---

### Step 5: 測定（単一スキャン O(W×H×D)）

フィルタ済みラベル画像と元画像を同時にスキャンし、ラベルごとに以下を集計します。

```
各ボクセル (x, y, z) について:
  label  = filteredLabel[x][y][z]
  intens = originalImage[x][y][z]   ← 元画像の生ピクセル値

  sumIntensity[label] += intens       → IntDen
  voxelCount[label]   += 1            → volume_vox
  sumX[label]         += x × vw      → centroid X
  sumY[label]         += y × vh      → centroid Y
  sumZ[label]         += z × vd      → centroid Z
```

出力値の計算：

| CSV カラム | 計算式 |
|-----------|--------|
| `volume_vox` | ボクセルカウント |
| `volume_um3` | `volume_vox × voxelVol_um3` |
| `integrated_intensity` | `sumIntensity[label]` |
| `mean_intensity` | `sumIntensity[label] / volume_vox` |
| `centroid_x_um` | `sumX[label] / volume_vox` |
| `centroid_y_um` | `sumY[label] / volume_vox` |
| `centroid_z_um` | `sumZ[label] / volume_vox` |

---

### Step 6: 出力

```
{outputDir}/
├── csv/{basename}_spots.csv     # 1行1スポット
├── roi/{basename}_RoiSet.zip    # ROI Manager 用 ZIP
└── params.txt                   # 解析パラメータ（上書き）
```

**ROI 生成方法（RoiExporter3D）:**

各 VALID ラベルについて、各 Z スライスごとに：
1. そのラベルのみ 255 の 8-bit マスクを作成
2. `ThresholdToSelection.run()` で輪郭 ROI を生成
3. `roi.setPosition(z)` で Z 座標を埋め込み
4. `roi.setName("obj-NNN-zZZZ")` で命名
5. ROI Manager に追加 → ZIP 保存

---

## パラメータ選定の指針

| パラメータ | 選び方 |
|-----------|--------|
| `threshold` | 代表画像で `Analyze > Histogram` を確認。シグナルピーク底部 ≈ background + 3σ 以上を目安に。`pcnt_ctrl_check.txt` 等の voxel 分布確認が有効 |
| `min_vol_um3` | PSF や hot pixel によるノイズ CC を除去。0.1 µm³ 程度（1〜数ボクセル）が目安 |
| `max_vol_um3` | PSF ハロー（Z方向伸長）で CC が肥大化する場合は大きめに設定。PCNT: 200 µm³、GTU88: 50 µm³ |
| `gaussian_blur` | 通常不要。低 S/N 画像または hot pixel が多い場合のみ `true` |

---

## 手動再現手順（Fiji 標準機能のみ）

プラグインを使わずに同等の解析を Fiji 標準機能で再現する手順です。

### 必要プラグイン

- **MorphoLibJ**（IJPB-plugins）: Update Site `IJPB-plugins` から導入

### 手順

#### 1. 画像を開いてキャリブレーションを確認

```
File > Open... → {Series}_ch2.tif を開く
Image > Properties... (Ctrl+Shift+P)
  → Voxel size: 0.0707 × 0.0707 × 0.2999 µm であることを確認
```

#### 2. 閾値確認（オプション）

```
Analyze > Histogram
  → スタック全体のヒストグラムを確認
  → シグナルピークと背景ピークの境界を読み取る
```

#### 3. バイナリマスクを作成

```
Image > Duplicate... → [✓] Duplicate stack → OK
Image > Adjust > Threshold...
  → Lower: {threshold}（例: 300）
  → Upper: 65535
  → [✓] Dark background
  → Apply → Convert to Mask
```

#### 4. 3D Connected Components ラベリング

```
Plugins > MorphoLibJ > Label > Connected Components Labeling
  → Connectivity: C26
  → Type: float
  → OK
```

→ ラベル画像が生成されます（各 CC に固有の整数値）

#### 5. サイズフィルタ

体積閾値をボクセル数に換算します：

```
voxelVol_um3 = 0.0707 × 0.0707 × 0.2999 = 0.001498 µm³
minVox = ceil(min_vol_um3 / voxelVol_um3)   例: 0.1 / 0.001498 ≈ 67 voxels
maxVox = floor(max_vol_um3 / voxelVol_um3)  例: 200 / 0.001498 ≈ 133511 voxels
```

```
Plugins > MorphoLibJ > Label > Remove Border Labels（不要なら省略）
Plugins > MorphoLibJ > Label > Label Size Filtering
  → Operation: keep
  → Min Voxel Number: {minVox}
  → Max Voxel Number: {maxVox}
  → OK
```

#### 6. 測定（元画像にリダイレクト）

```
Analyze > Set Measurements...
  → [✓] Area, Integrated density, Mean gray value, Centroid
  → Redirect to: {元画像タイトル}
  → OK

Analyze > Analyze Particles...
  → Size (pixel^2): 0-Infinity
  → [✓] Display results
  → [✓] Add to Manager
  → OK
```

> ただし Analyze Particles は 2D です。3D 測定には **3D Objects Counter** または MorphoLibJ の **Analyze Regions 3D** を使用してください。

#### 6'（推奨）. MorphoLibJ で 3D 測定

```
Plugins > MorphoLibJ > Analyze > Analyze Regions 3D
  → Label image: {ラベル画像}
  → Intensity image: {元画像}
  → [✓] Volume, Mean Intensity, Centroid
  → OK
```

→ Results テーブルに spot ごとの測定値が表示されます

#### 7. ROI 保存

```
ROI Manager が空の状態で:
Plugins > MorphoLibJ > Label > Labels to ROI Manager
  → Label image: {フィルタ済みラベル画像}
  → OK

ROI Manager > More >> Save...
  → {basename}_RoiSet.zip として保存
```

---

## セッション別パラメータ実績

| セッション | マーカー | threshold | min_vol µm³ | max_vol µm³ | 備考 |
|-----------|---------|-----------|-------------|-------------|------|
| 20260226 | PCNT | 3000 | 0.1 | 50.0 | 12-bit 飽和（max 4095）、noise max ≈ 950 |
| 20260227 | PCNT | 300 | 0.1 | 200.0 | PSF ハローで CC が Z 方向に伸長 → max_vol 大きめ |
| 20260227 | GTU88 | 700 | 0.1 | 50.0 | 輝度 max=1744、mean+3sd=423。threshold=1000 では取り逃がし多数（S011: 27 spots）→ 700 に変更（S011: 45 spots）|

**20260226 PCNT で threshold=3000 を使う理由:**
シグナルが 12-bit 上限（4095）で飽和しているため mean_intensity での条件間比較が困難。
スポット数の定量が主目的。ノイズ最大値 ≈ 950 に対して 3000 は十分なマージン。

**20260227 PCNT で max_vol=200 を使う理由:**
threshold=300 では PSF ハロー（Z方向伸長）により CC が肥大化し、
標準的な max_vol=50 では valid スポットがすべて TOO_LARGE に分類される。
輝度範囲（Ctrl: ~480、Saponin: ~560）を考慮すると threshold をこれ以上上げると Ctrl のスポットが検出不能になる。
