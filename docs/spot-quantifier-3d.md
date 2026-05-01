# Seeded Spot Quantifier 3D

現行の `Seeded Spot Quantifier 3D` / `Seeded Spot Quantifier 3D Multi` の仕様メモです。

## 対象

- Fiji / ImageJ1 の 3D stack 画像
- single image GUI: `Plugins > Segmentation > Spot Quantifier > Seeded Spot Quantifier 3D`
- multi image GUI: `Plugins > Segmentation > Spot Quantifier > Seeded Spot Quantifier 3D Multi`
- macro mode: `Seeded Spot Quantifier 3D`

## Segmentation

処理は seeded segmentation です。

1. 指定 channel を抽出する
2. 必要なら 3D Gaussian blur を適用する
3. `seed threshold` で seed を作る
4. `area threshold` が有効なら area mask を作る
5. seeded watershed / connected region 処理で label image を作る
6. `Min vol` / `Max vol` で object をフィルタする
7. filtered label image から測定値、preview、ROI を作る

`Area threshold` を無効にした場合は、area mask による制限を使わずに seed 起点の処理を行います。

### Seed / Area threshold の逆転時

通常は `Seed threshold >= Area threshold` を推奨します。
ただし `Seed threshold < Area threshold` の場合も処理は継続できます。

この場合、area mask は seed mask の部分集合になります。
そのため、主な conflict は「1つの seed label が複数の area component と重なる」ケースです。

`Area conflict`:

- `max`: デフォルト。seed と重なる area component のうち、overlap voxel 数が最大の component だけを採用します。
- `split`: seed-area の組ごとに別 object label を割り当てます。

seed と重ならない area component、および area と重ならない seed は final result には入りません。

## Measurement

CSV は object ごとに 1 行です。現在の列は以下です。

- `spot_id`
- `volume_um3`
- `volume_vox`
- `surface_area_um2`
- `sphericity`
- `integrated_intensity`
- `mean_intensity`
- `max_intensity`
- `centroid_x_um`
- `centroid_y_um`
- `centroid_z_um`
- `max_feret3d_um`
- `max_feret_p1_x_um`
- `max_feret_p1_y_um`
- `max_feret_p1_z_um`
- `max_feret_p2_x_um`
- `max_feret_p2_y_um`
- `max_feret_p2_z_um`

`surface_area_um2` は voxel の露出面から計算する格子状の表面積です。
`sphericity` は同体積球の表面積を object 表面積で割った値です。
`max_feret3d_um` は object 内 voxel の最遠 2 点間距離で、対応する 2 点の物理座標も出力します。

## Save Outputs

保存オプション:

- `Seed ROI`
- `Size ROI`
- `Area ROI`
- `Result ROI`
- `Result ROI by object`
- `CSV`
- `Param`

デフォルト:

- `Result ROI`: off
- `Result ROI by object`: on
- `CSV`: on
- `Param`: on

通常の `Save` は元画像と同じフォルダを基準に保存します。
`Save to...` は選択したフォルダを基準に、通常の `Save` と同じ保存処理を実行します。

デフォルトの出力フォルダ名は `{name} result` です。
`Custom folder name` を有効にすると `folder_pattern` により `{name}`, `{date}`, `{seed}`, `{area}` を展開できます。

主な出力:

- `{basename}_spots.csv`
- `{basename}_params.txt`
- `{basename}_seed_roi.zip`
- `{basename}_size_roi.zip`
- `{basename}_area_roi.zip`
- `{basename}_result_roi.zip`
- `{basename}_result_roi_objects/obj-001.zip`, `obj-002.zip`, ...

ROI zip は visible ROI Manager ではなく hidden ROI Manager または ROI array 経由で保存します。
single-channel 画像では ROI position は `z`、multi-channel 画像では `(channel, z, t=1)` になります。

## Single GUI

single image GUI の主な UI:

- `Target`
- `Ch`
- `Z-proj`
- `Area threshold`
- `Seed threshold`
- `Min vol` / `Max vol`
- `Connectivity`
- `Fill holes`
- `Area conflict`
- `Gaussian blur`
- `Preview mode`
- `Save Options`
- `Apply`
- `Save`
- `Save to...`
- `Batch...`
- `Cancel`

`Target` 変更時は、開いている 2D image から title match による `Z-proj` auto-fill を行います。

## Multi GUI

multi image GUI は、複数の 3D 画像に同一パラメータを適用するための GUI です。

- `Windows...` で selector window を開閉する
- selector で対象 3D image を複数選択する
- 各 3D image に per-image `Z-proj` を指定する
- `Select all` / `Deselect all` で対象を一括変更する
- `Ch`, threshold, filter, preview, save options は共通
- `Batch...` はなく、`Save` が選択画像すべてを保存する

## Macro Mode

例:

```javascript
run("Seeded Spot Quantifier 3D",
    "channel=1 " +
    "area_threshold=200 seed_threshold=500 area_enabled=true " +
    "min_vol=0.1 max_vol=50.0 " +
    "gaussian_blur=false gauss_xy=1.0 gauss_z=0.5 " +
    "connectivity=6 fill_holes=false area_conflict=max " +
    "save_result_roi_by_object=true save_csv=true save_param=true " +
    "output=[C:/path/to/output]");
```

主な option:

- `channel`
- `area_threshold`
- `seed_threshold`
- `area_enabled`
- `min_vol`
- `max_vol`
- `gaussian_blur`
- `gauss_xy`
- `gauss_z`
- `connectivity`
- `fill_holes`
- `area_conflict` (`max` or `split`)
- `save_seed_roi`
- `save_size_roi`
- `save_area_roi`
- `save_result_roi`
- `save_result_roi_by_object`
- `save_csv`
- `save_param`
- `custom_folder`
- `folder_pattern`
- `output`

macro の保存デフォルト:

- `save_seed_roi=false`
- `save_size_roi=false`
- `save_area_roi=false`
- `save_result_roi=false`
- `save_result_roi_by_object=true`
- `save_csv=true`
- `save_param=true`
- `custom_folder=false`
- `folder_pattern={name} result`
- `area_conflict=max`

## Tests

現在のテストは headless で安定して確認できるものを中心にしています。

- core label / merge / slice segmentation logic
- seeded quantifier の measurement columns
- CSV column consistency
- 3D ROI の hyperstack position

GUI 非同期挙動や visible ROI Manager 依存のテストは、CI / headless で不安定になりやすいため除外しています。
