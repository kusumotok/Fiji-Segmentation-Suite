# Implementation Plan: Slice-Based 3D Segmenter

## Overview

このタスクリストは、スライスベース3Dセグメンター機能の実装を段階的に進めるためのものです。各タスクは前のタスクの上に構築され、最終的に完全に統合されたシステムを作成します。

## Tasks

- [x] 1. プロジェクト構造とデータモデルの実装
  - [x] 1.1 LabelKeyクラスの実装
    - (sliceIndex, label)の組み合わせを表すイミュータブルなキークラスを作成
    - equals()とhashCode()を適切に実装
    - _Requirements: 4.3_
  
  - [x] 1.2 SliceSegmentationResultクラスの実装
    - 各スライスのセグメンテーション結果を保持するデータクラスを作成
    - sliceIndex、labelImage、regionCountフィールドを含む
    - _Requirements: 3.4_
  
  - [x] 1.3 MergeStatisticsクラスの実装
    - 結合処理の統計情報を保持するデータクラスを作成
    - toSummaryString()メソッドで統計情報を文字列化
    - _Requirements: 13.1, 13.2, 13.3_

- [x] 2. Union-Findデータ構造の実装
  - [x] 2.1 UnionFindクラスの実装
    - 経路圧縮とランクによる結合を使用した効率的な実装
    - find()、union()、getGroups()メソッドを実装
    - _Requirements: 4.4_
  
  - [ ]* 2.2 UnionFindのプロパティテスト
    - **Property 9: Transitive Merging**
    - **Validates: Requirements 4.1, 4.4**
    - 3つの連続スライスでの推移的結合を検証
  
  - [x]* 2.3 UnionFindのユニットテスト
    - 基本的なunion/find操作のテスト
    - グループ化の正確性テスト
    - _Requirements: 4.4_

- [x] 3. スライスセグメンターの実装
  - [x] 3.1 SliceSegmenterクラスの基本構造を作成
    - segmentAllSlices()とsegmentSingleSlice()メソッドのシグネチャを定義
    - _Requirements: 3.1_
  
  - [x] 3.2 各スライスでの2Dセグメンテーション実行ロジックを実装
    - 3Dスタックから各スライスを抽出
    - 既存のMarkerBuilderを使用してシードを検出
    - WatershedRunner/RandomWalkerRunnerを実行
    - _Requirements: 3.1, 3.2_
  
  - [x] 3.3 シードが見つからない場合の処理を実装
    - 空のラベル画像（全ピクセル0）を生成
    - ログメッセージを出力
    - _Requirements: 3.3, 10.1_
  
  - [ ]* 3.4 SliceSegmenterのプロパティテスト
    - **Property 8: Slice Processing Consistency**
    - **Validates: Requirements 3.1, 3.2**
    - バッチ処理と手動スライス処理の一貫性を検証
  
  - [ ]* 3.5 SliceSegmenterのプロパティテスト
    - **Property 10: Empty Slice Handling**
    - **Validates: Requirements 3.3**
    - 空スライスが正しく背景として処理されることを検証

- [x] 4. Checkpoint - 基本的なスライス処理の検証
  - すべてのテストが通ることを確認し、質問があればユーザーに確認してください。

- [x] 5. スライスマージャーの実装
  - [x] 5.1 SliceMergerクラスの基本構造を作成
    - mergeSlices()、buildConnectedComponents()、hasOverlap()メソッドのシグネチャを定義
    - _Requirements: 4.1_
  
  - [x] 5.2 重なり検出アルゴリズムの実装
    - hasOverlap()メソッドで隣接スライス間のピクセル重なりを検出
    - calculateOverlapArea()メソッドで重なり面積を計算
    - 双方向マッチング対応
    - 最小重なり閾値（MIN_OVERLAP_RATIO = 0.1）の実装
    - _Requirements: 4.1, 4.2_
  
  - [x] 5.3 連結成分構築アルゴリズムの実装
    - buildConnectedComponents()でUnion-Findを使用して領域をグループ化
    - performBidirectionalMatching()で双方向マッチングを実装
    - findBestOverlapMatch()で重なり面積による最適マッチングを実装
    - 隣接スライスペアを走査して重なりを検出
    - _Requirements: 4.3, 4.4_
  
  - [x] 5.4 3Dラベル画像生成の実装
    - グループ化された領域に新しいラベルを割り当て
    - 3D ImagePlusを生成
    - _Requirements: 4.5_
  
  - [ ]* 5.5 SliceMergerのプロパティテスト
    - **Property 2: Overlapping Regions Merge**
    - **Validates: Requirements 4.1**
    - 重なる領域が同じラベルを持つことを検証

- [x] 6. ラベル再割り当ての実装
  - [x] 6.1 LabelReassignerクラスの実装
    - reassignLabels()メソッドで1から連続した整数にラベルを再割り当て
    - buildLabelMapping()で既存ラベルから新ラベルへのマッピングを構築
    - _Requirements: 5.1, 5.2, 5.3_
  
  - [ ]* 6.2 LabelReassignerのプロパティテスト
    - **Property 4: Label Reassignment Correctness**
    - **Validates: Requirements 5.1, 5.2, 5.3**
    - ラベルが連続し、一意で、背景が0であることを検証

- [x] 7. メインプラグインクラスの実装
  - [x] 7.1 Slice_Based_3D_Segmenter_クラスの基本構造を作成
    - PlugInインターフェースを実装
    - run()メソッドのシグネチャを定義
    - _Requirements: 1.1_
  
  - [x] 7.2 入力検証ロジックの実装
    - null画像チェック
    - スライス数チェック（2未満でエラー）
    - エラーメッセージ表示
    - _Requirements: 1.1, 1.2, 1.3, 10.2_
  
  - [x] 7.3 パラメータ設定の実装
    - ThresholdModelを使用してパラメータを管理
    - 全パラメータ（bg_threshold、fg_threshold、tolerance、method、surface、connectivity、marker_source、preprocessing、sigma値）を受け入れ
    - _Requirements: 2.1-2.10_
  
  - [x] 7.4 メインセグメンテーションフローの実装
    - SliceSegmenterを呼び出して各スライスを処理
    - SliceMergerを呼び出してスライス間を結合
    - LabelReassignerを呼び出してラベルを再割り当て
    - _Requirements: 3.1, 4.1, 5.1_
  
  - [x] 7.5 public static segment APIの実装
    - 全パラメータを引数として受け入れるAPIメソッド
    - 3D ImagePlusを返す
    - エラー時にnullを返す
    - _Requirements: 8.1, 8.2, 8.3, 8.4_
  
  - [ ]* 7.6 メインプラグインのプロパティテスト
    - **Property 3: 3D Label Image Generation and Format Compatibility**
    - **Validates: Requirements 4.5, 8.2, 14.1**
    - 出力形式と次元の正確性を検証

- [x] 8. マクロモードの実装
  - [x] 8.1 マクロオプション解析の実装
    - runMacroMode()メソッドでマクロオプション文字列を解析
    - 全パラメータをパース
    - デフォルト値の処理
    - _Requirements: 7.1, 7.2, 7.3_
  
  - [x] 8.2 マクロモードでのGUIスキップ実装
    - マクロオプションが提供された場合、GUIを表示せずに処理を実行
    - _Requirements: 7.1_
  
  - [ ]* 8.3 マクロモードのプロパティテスト
    - **Property 6: Macro Parameter Parsing Round-Trip**
    - **Validates: Requirements 7.2**
    - マクロオプション文字列のパースと適用の正確性を検証

- [x] 9. Checkpoint - コア機能の検証
  - すべてのテストが通ることを確認し、質問があればユーザーに確認してください。

- [ ] 10. ROIエクスポート機能の実装
  - [x] 10.1 RoiExporter3Dとの統合
    - 既存のRoiExporter3Dクラスを使用して3DラベルをROI Managerにエクスポート
    - _Requirements: 6.1, 6.2_
  
  - [x] 10.2 エクスポート完了通知の実装
    - ユーザーにROI Managerで結果を確認できることを通知
    - _Requirements: 6.3_
  
  - [ ]* 10.3 ROIエクスポートのプロパティテスト
    - **Property 5: ROI Export Completeness**
    - **Validates: Requirements 6.1**
    - 全非ゼロピクセルがROIでカバーされることを検証

- [ ] 11. GUIの実装
  - [x] 11.1 SliceBased3DFrameクラスの基本構造を作成
    - JFrameを継承
    - 全パラメータ入力フィールドを配置
    - _Requirements: 9.1, 9.2_
  
  - [x] 11.2 パラメータ入力フィールドの実装
    - bg_threshold、fg_threshold、toleranceなどの入力フィールド
    - method、surface、connectivityなどのドロップダウン
    - preprocessing、sigma値などの追加設定
    - _Requirements: 9.2_
  
  - [x] 11.3 プレビュー機能の実装
    - パラメータ変更時にリアルタイムでプレビューを更新
    - _Requirements: 9.3, 9.4_
  
  - [x] 11.4 実行ボタンと進行状況表示の実装
    - 実行ボタンクリック時にセグメンテーションを開始
    - 進行状況バーまたはステータスメッセージを表示
    - _Requirements: 9.5_

- [ ] 12. 設定の永続化の実装
  - [x] 12.1 設定の保存機能を実装
    - GUIでパラメータ変更時にImageJ Preferencesに保存
    - _Requirements: 12.1_
  
  - [x] 12.2 設定の読み込み機能を実装
    - GUI起動時に保存された設定を読み込み
    - 設定が存在しない場合はデフォルト値を使用
    - _Requirements: 12.2, 12.3_
  
  - [ ]* 12.3 設定永続化のプロパティテスト
    - **Property 7: Settings Persistence Round-Trip**
    - **Validates: Requirements 12.1**
    - 設定の保存と読み込みの正確性を検証
  
  - [ ]* 12.4 設定のプロパティテスト
    - **Property 1: Configuration Round-Trip**
    - **Validates: Requirements 2.1-2.10**
    - パラメータ設定と取得の正確性を検証

- [ ] 13. ログ出力と統計情報の実装
  - [x] 13.1 処理進行状況のログ出力を実装
    - 各スライスの処理状況をログに出力
    - シード検出数をログに出力
    - _Requirements: 10.4_
  
  - [x] 13.2 統計情報の計算と出力を実装
    - 検出された3Dエリアの数を計算
    - 各3Dエリアのボクセル数を計算
    - サマリーを表示
    - _Requirements: 13.1, 13.2, 13.3_
  
  - [ ] 13.3 処理時間とメモリ使用量の計測を実装
    - 開始時刻と終了時刻を記録
    - 処理時間をログに出力
    - メモリ使用量をログに出力
    - _Requirements: 11.1, 11.2, 11.3, 11.4_

- [ ] 14. エラーハンドリングの実装
  - [ ] 14.1 入力検証エラーの処理を実装
    - null画像、スライス数不足のエラーメッセージ
    - _Requirements: 1.1, 1.2, 10.2_
  
  - [ ] 14.2 処理エラーの処理を実装
    - 全スライスでシードが見つからない場合の警告
    - 一部スライスでシードが見つからない場合のログ
    - _Requirements: 3.3, 10.1_
  
  - [ ] 14.3 ランタイムエラーの処理を実装
    - OutOfMemoryErrorのキャッチと適切なメッセージ表示
    - 予期しない例外のキャッチとスタックトレース出力
    - _Requirements: 10.3_
  
  - [ ] 14.4 パラメータ検証の実装
    - 負の閾値値などの無効なパラメータをデフォルト値に置き換え
    - 警告ログを出力
    - _Requirements: 2.1-2.10_

- [ ] 15. Checkpoint - 統合テストと検証
  - すべてのテストが通ることを確認し、質問があればユーザーに確認してください。

- [ ] 16. ドキュメンテーションの作成
  - [ ] 16.1 Javadocコメントの追加
    - 全クラスとpublicメソッドにJavadocを追加
    - 各パラメータの説明を含む
    - _Requirements: 15.1, 15.2_
  
  - [ ] 16.2 使用例の追加
    - Javadocに使用例を含める
    - マクロモードとAPIモードの両方の例を提供
    - _Requirements: 15.3_
  
  - [ ] 16.3 既存3D版との違いの説明を追加
    - Javadocに既存3D版との比較を含める
    - 使用シーンの違いを説明
    - _Requirements: 15.4_

- [ ]* 17. 統合テストとエンドツーエンド検証
  - [ ]* 17.1 エンドツーエンド統合テスト
    - 既知の入力で完全な処理フローをテスト
    - 結果を手動結果と比較
    - _Requirements: 全体_
  
  - [ ]* 17.2 既存3D版との比較テスト
    - 同じ入力で両方のセグメンターを実行
    - 出力形式の互換性を検証
    - _Requirements: 14.1, 14.2, 14.3_
  
  - [ ]* 17.3 パフォーマンステスト
    - 大きな3Dスタックでの処理時間を計測
    - メモリ使用量を計測
    - _Requirements: 11.1-11.4_

- [ ] 18. 最終チェックポイント - 全機能の検証
  - すべてのテストが通ることを確認し、質問があればユーザーに確認してください。

## Notes

- `*`マークのタスクはオプションで、より速いMVPのためにスキップ可能です
- 各タスクは特定の要件を参照してトレーサビリティを確保しています
- チェックポイントは段階的な検証を保証します
- プロパティテストは普遍的な正確性プロパティを検証します
- ユニットテストは特定の例とエッジケースを検証します
