# Requirements Document

## Introduction

スライスベース3Dセグメンター（Slice-Based 3D Segmenter）は、既存の2Dセグメンテーション手法を各スライスに適用し、スライス間で隣接する領域を3Dエリアとして結合する新しい3D画像処理機能です。既存の3D版セグメンター（Maxima_Based_Segmenter_3D_）とは異なるアプローチを提供し、ユーザーが画像の特性に応じて最適な手法を選択できるようにします。

## Glossary

- **Slice_Based_3D_Segmenter**: 2Dセグメンテーションをスライスごとに実行し、スライス間で領域を結合する3Dセグメンテーションシステム
- **2D_Segmenter**: 既存の2Dセグメンテーション機能（Maxima_Based_Segmenter_）
- **Existing_3D_Segmenter**: 既存の3Dセグメンテーション機能（Maxima_Based_Segmenter_3D_）
- **Label_Image**: セグメンテーション結果を表すラベル付き画像（各領域に一意の整数値が割り当てられる）
- **3D_Stack**: 複数の2D画像スライスから構成される3D画像データ
- **Adjacent_Regions**: 隣接するスライス間で空間的に重なる領域
- **C6_Connectivity**: 3D空間における6近傍接続性（上下左右前後の6方向）
- **Marker_Configuration**: セグメンテーションのパラメータ設定（bg_threshold, fg_threshold, tolerance, method, surface, connectivityなど）
- **ROI_Manager**: ImageJのROI（Region of Interest）管理ツール
- **Round_Trip_Property**: パース→プリント→パースで元のデータと等価になる性質

## Requirements

### Requirement 1: 3Dスタック画像の検証

**User Story:** As a ユーザー, I want システムが入力画像を検証する, so that 不適切な画像での実行を防止できる

#### Acceptance Criteria

1. WHEN 入力画像がnullの場合, THEN THE Slice_Based_3D_Segmenter SHALL エラーメッセージを表示して処理を中止する
2. WHEN 入力画像のスライス数が2未満の場合, THEN THE Slice_Based_3D_Segmenter SHALL エラーメッセージを表示して処理を中止する
3. WHEN 入力画像が有効な3Dスタックの場合, THEN THE Slice_Based_3D_Segmenter SHALL 処理を続行する

### Requirement 2: 2Dセグメンテーション設定の適用

**User Story:** As a ユーザー, I want 2D版と同じパラメータ設定を使用できる, so that 既存の2D処理との一貫性を保てる

#### Acceptance Criteria

1. THE Slice_Based_3D_Segmenter SHALL bg_threshold（背景閾値）パラメータを受け入れる
2. THE Slice_Based_3D_Segmenter SHALL fg_threshold（前景閾値）パラメータを受け入れる
3. THE Slice_Based_3D_Segmenter SHALL tolerance（Extended Maxima許容値）パラメータを受け入れる
4. THE Slice_Based_3D_Segmenter SHALL method（WatershedまたはRandom Walker）パラメータを受け入れる
5. THE Slice_Based_3D_Segmenter SHALL surface（INVERT_ORIGINAL, ORIGINAL, GRADIENT_SOBEL）パラメータを受け入れる
6. THE Slice_Based_3D_Segmenter SHALL connectivity（C4またはC8）パラメータを受け入れる
7. THE Slice_Based_3D_Segmenter SHALL marker_source（THRESHOLD_COMPONENTS, ROI_MANAGER, BINARY_IMAGE, FIND_MAXIMA, MANUAL_SELECTION）パラメータを受け入れる
8. THE Slice_Based_3D_Segmenter SHALL preprocessing_enabled（前処理有効化）パラメータを受け入れる
9. THE Slice_Based_3D_Segmenter SHALL sigma_surface（表面平滑化シグマ値）パラメータを受け入れる
10. THE Slice_Based_3D_Segmenter SHALL sigma_seed（シード平滑化シグマ値）パラメータを受け入れる

### Requirement 3: スライスごとの2Dセグメンテーション実行

**User Story:** As a ユーザー, I want 各スライスで2Dセグメンテーションが実行される, so that スライスごとの領域を検出できる

#### Acceptance Criteria

1. FOR ALL スライス in 3D_Stack, THE Slice_Based_3D_Segmenter SHALL 2D_Segmenterを同じMarker_Configurationで実行する
2. WHEN 各スライスの2Dセグメンテーションが完了した場合, THEN THE Slice_Based_3D_Segmenter SHALL Label_Imageを生成する
3. WHEN あるスライスでシードが検出されない場合, THEN THE Slice_Based_3D_Segmenter SHALL そのスライスのラベルを全て0（背景）として処理を続行する
4. THE Slice_Based_3D_Segmenter SHALL 各スライスの2Dセグメンテーション結果を保持する

### Requirement 4: スライス間の領域結合

**User Story:** As a ユーザー, I want 隣接スライス間で重なる領域が3Dエリアとして結合される, so that 3D構造を認識できる

#### Acceptance Criteria

1. WHEN 隣接する2つのスライスのラベル領域が1ピクセル以上重なる場合, THEN THE Slice_Based_3D_Segmenter SHALL それらを同じ3Dエリアとして結合する
2. THE Slice_Based_3D_Segmenter SHALL C6_Connectivityを使用してスライス間の隣接判定を行う
3. WHEN スライスz1の領域Aとスライスz2の領域Bが重なる場合, THEN THE Slice_Based_3D_Segmenter SHALL 領域Aと領域Bに同じラベル値を割り当てる
4. THE Slice_Based_3D_Segmenter SHALL 全スライスを走査して連結成分を識別する
5. WHEN 領域結合が完了した場合, THEN THE Slice_Based_3D_Segmenter SHALL 3D Label_Imageを生成する

### Requirement 5: ラベルの再割り当て

**User Story:** As a ユーザー, I want 最終的なラベルが連続した整数になる, so that 結果を正しく解釈できる

#### Acceptance Criteria

1. WHEN スライス間結合が完了した場合, THEN THE Slice_Based_3D_Segmenter SHALL ラベル値を1から連続した整数に再割り当てする
2. THE Slice_Based_3D_Segmenter SHALL 背景ピクセルのラベルを0に保つ
3. THE Slice_Based_3D_Segmenter SHALL 各3Dエリアに一意のラベル値を割り当てる

### Requirement 6: ROIマネージャーへのエクスポート

**User Story:** As a ユーザー, I want セグメンテーション結果がROIマネージャーにエクスポートされる, so that 結果を視覚的に確認・編集できる

#### Acceptance Criteria

1. WHEN セグメンテーションが完了した場合, THEN THE Slice_Based_3D_Segmenter SHALL 3D Label_ImageをROI_Managerにエクスポートする
2. THE Slice_Based_3D_Segmenter SHALL 既存のRoiExporter3Dクラスを使用してエクスポートを実行する
3. WHEN エクスポートが完了した場合, THEN THE Slice_Based_3D_Segmenter SHALL ユーザーにROI_Managerで結果を確認できることを通知する

### Requirement 7: マクロモードのサポート

**User Story:** As a ユーザー, I want マクロからこの機能を呼び出せる, so that バッチ処理を自動化できる

#### Acceptance Criteria

1. WHEN マクロオプションが提供された場合, THEN THE Slice_Based_3D_Segmenter SHALL GUIを表示せずに処理を実行する
2. THE Slice_Based_3D_Segmenter SHALL マクロオプションから全てのMarker_Configurationパラメータを解析する
3. WHEN マクロオプションにパラメータが指定されていない場合, THEN THE Slice_Based_3D_Segmenter SHALL デフォルト値を使用する
4. WHEN マクロモードで実行された場合, THEN THE Slice_Based_3D_Segmenter SHALL 結果をROI_Managerにエクスポートする

### Requirement 8: プログラマティックAPI

**User Story:** As a 開発者, I want Javaコードから直接この機能を呼び出せる, so that カスタムスクリプトやプラグインに統合できる

#### Acceptance Criteria

1. THE Slice_Based_3D_Segmenter SHALL public static メソッドとしてsegment APIを提供する
2. WHEN segment APIが呼び出された場合, THEN THE Slice_Based_3D_Segmenter SHALL 3D Label_Imageを返す
3. THE segment API SHALL 入力画像と全てのMarker_Configurationパラメータを引数として受け入れる
4. WHEN セグメンテーションが失敗した場合, THEN THE segment API SHALL nullを返す

### Requirement 9: GUIインターフェース

**User Story:** As a ユーザー, I want GUIから設定を調整できる, so that インタラクティブにパラメータを試せる

#### Acceptance Criteria

1. WHEN マクロオプションが提供されていない場合, THEN THE Slice_Based_3D_Segmenter SHALL GUIウィンドウを表示する
2. THE GUI SHALL 全てのMarker_Configurationパラメータの入力フィールドを提供する
3. THE GUI SHALL プレビュー機能を提供する
4. WHEN ユーザーがパラメータを変更した場合, THEN THE GUI SHALL リアルタイムでプレビューを更新する
5. WHEN ユーザーが実行ボタンをクリックした場合, THEN THE Slice_Based_3D_Segmenter SHALL セグメンテーションを実行する

### Requirement 10: エラーハンドリングとログ出力

**User Story:** As a ユーザー, I want エラーや警告が適切に報告される, so that 問題を診断できる

#### Acceptance Criteria

1. WHEN シードが検出されない場合, THEN THE Slice_Based_3D_Segmenter SHALL ログメッセージを出力する
2. WHEN 入力検証が失敗した場合, THEN THE Slice_Based_3D_Segmenter SHALL エラーダイアログを表示する
3. WHEN 処理中に例外が発生した場合, THEN THE Slice_Based_3D_Segmenter SHALL エラーメッセージをログに記録する
4. THE Slice_Based_3D_Segmenter SHALL 処理の進行状況をログに出力する

### Requirement 11: パフォーマンス比較のための計測

**User Story:** As a ユーザー, I want 処理時間が計測される, so that Existing_3D_Segmenterと性能を比較できる

#### Acceptance Criteria

1. THE Slice_Based_3D_Segmenter SHALL セグメンテーション処理の開始時刻を記録する
2. THE Slice_Based_3D_Segmenter SHALL セグメンテーション処理の終了時刻を記録する
3. WHEN 処理が完了した場合, THEN THE Slice_Based_3D_Segmenter SHALL 処理時間をログに出力する
4. THE Slice_Based_3D_Segmenter SHALL メモリ使用量の情報をログに出力する

### Requirement 12: 設定の永続化

**User Story:** As a ユーザー, I want 設定が保存される, so that 次回起動時に同じ設定を使用できる

#### Acceptance Criteria

1. WHEN ユーザーがGUIでパラメータを変更した場合, THEN THE Slice_Based_3D_Segmenter SHALL 設定をImageJのPreferencesに保存する
2. WHEN GUIが起動した場合, THEN THE Slice_Based_3D_Segmenter SHALL 保存された設定を読み込む
3. WHEN 保存された設定が存在しない場合, THEN THE Slice_Based_3D_Segmenter SHALL デフォルト値を使用する

### Requirement 13: 結果の検証

**User Story:** As a ユーザー, I want 結果が正しいことを確認できる, so that セグメンテーションの品質を評価できる

#### Acceptance Criteria

1. THE Slice_Based_3D_Segmenter SHALL 検出された3Dエリアの数をログに出力する
2. THE Slice_Based_3D_Segmenter SHALL 各3Dエリアのボクセル数をログに出力する
3. WHEN セグメンテーションが完了した場合, THEN THE Slice_Based_3D_Segmenter SHALL 統計情報を含むサマリーを表示する

### Requirement 14: 既存3D版との互換性

**User Story:** As a ユーザー, I want 既存3D版と同じ出力形式を使用する, so that 既存のワークフローに統合できる

#### Acceptance Criteria

1. THE Slice_Based_3D_Segmenter SHALL Existing_3D_Segmenterと同じ3D Label_Image形式を出力する
2. THE Slice_Based_3D_Segmenter SHALL Existing_3D_Segmenterと同じROIエクスポート形式を使用する
3. THE Slice_Based_3D_Segmenter SHALL SegmentationResult3Dクラスを使用して結果を返す

### Requirement 15: ドキュメンテーション

**User Story:** As a ユーザー, I want 機能の使い方が文書化されている, so that 効果的に使用できる

#### Acceptance Criteria

1. THE Slice_Based_3D_Segmenter SHALL Javadocコメントを含む
2. THE Javadoc SHALL 各パラメータの説明を含む
3. THE Javadoc SHALL 使用例を含む
4. THE Javadoc SHALL Existing_3D_Segmenterとの違いを説明する
