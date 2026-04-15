# Plan: Simplify 3D Segmenter to Simple plugin approach + histogram improvements

## Overview
3 changes: (1) simplify slice processing & UI to match Simple plugin, (2) use 3D full-stack histogram, (3) add log-axis toggle.

## 1. SliceBased3DFrame.java — rewrite (~1384 lines → ~450 lines)

Model SimpleSegmenterFrame's pattern. Key changes:

- Use `ThresholdModel.createForSimplePlugin(imp)` instead of `new ThresholdModel(imp)`
- **Keep**: histogramPanel, bgBar/bgField, toleranceBar/toleranceField, preview radio buttons, Apply/Add ROI/Save ROI buttons
- **Add**: `Checkbox logScaleCb` near histogram to toggle Y-axis log scale
- **Remove all**: fgBar/fgField, invertCb, methodGroup (Watershed/RW), surfaceGroup, connGroup, rwBetaBar/rwBetaField, resetBtn, advancedPanel and ALL advanced controls (absorb, overlap, debounce, marker source, binary, preprocessing, sigma, manual seeds, appearance, seed area)
- `histogramPanel.setFgEnabled(false)` — fg threshold line disabled
- Preview: always uses Watershed + INVERT_ORIGINAL + C4 (same as Simple)
- `runApply()`: call simplified `Slice_Based_3D_Segmenter_.segment(imp, bgThreshold, tolerance)`
- `createSliceModel()`: only copy tBg and tolerance (rest fixed by createForSimplePlugin)
- `onHistogramThreshold()`: simplified like SimpleSegmenterFrame (only bg threshold)

## 2. SliceSegmenter.java — simplify (~174 lines → ~80 lines)

- `segmentSingleSlice()`: use `ThresholdModel.createForSimplePlugin(slice)` then only set tBg + tolerance
- Remove Random Walker branch — always Watershed + INVERT_ORIGINAL + C4 + no preprocessing
- Remove manual seed copying, unnecessary parameter copying

## 3. Slice_Based_3D_Segmenter_.java — simplify API (~751 lines → ~400 lines)

- `segment()`: change from 11 params to `segment(ImagePlus imp, int bgThreshold, double tolerance)`
- `runMacroMode()`: only parse `bg_threshold` and `tolerance`
- `performSegmentation()`: create model via `ThresholdModel.createForSimplePlugin(imp)` then set tBg + tolerance
- Remove: `createConfiguredModel()`, `parseMethod()`, `parseSurface()`, `parseConnectivity()`, `parseMarkerSource()`, `validateSigma()`, `parseParametersFromMacro()` large method → simplified version

## 4. HistogramPanel.java — add log scale (~180 lines → ~200 lines)

- Add field: `private boolean logScale = false;`
- Add method: `public void setLogScale(boolean log)` → sets field + repaint
- Modify `paint()`: when logScale, use `Math.log1p(histogram[i])` for bar height normalization against `Math.log1p(histMax)`
- **3D histogram**: already working correctly (computes full stack, doesn't update on slice change). No changes needed.

## 5. Test updates

- `SliceMergerOverlapTest`, `SliceMerger3DLabelTest`, `SliceMergerConnectedComponentsTest` — already fixed (no changes)
- `SliceSegmenterTest` — update to match simplified API if it tests parameter passing
- `SliceBasedMacroModeTest` — update macro parameter tests to only use bg_threshold + tolerance
- `SliceBasedParameterConfigTest` — likely needs simplification to match new API
- Other test files referencing the old `segment()` signature will need updating

## File change summary

| File | Action |
|------|--------|
| SliceBased3DFrame.java | Rewrite (simplify) |
| SliceSegmenter.java | Simplify |
| Slice_Based_3D_Segmenter_.java | Simplify API |
| HistogramPanel.java | Add logScale feature |
| Tests referencing old APIs | Update |

## Order of implementation
1. HistogramPanel (log scale) — small, independent
2. SliceSegmenter — simplify core logic
3. Slice_Based_3D_Segmenter_ — simplify API
4. SliceBased3DFrame — rewrite UI
5. Fix tests
6. Build & install
