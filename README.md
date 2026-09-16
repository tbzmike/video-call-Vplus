# Video Call Vplus

Android video-call volume enhancement project.

## Goals

- Up to a user-selectable **200% Vplus amplification target**.
- Speech-focused equalization for clearer voices.
- Conservative gain limits and loudness processing to reduce clipping/distortion.
- Android 6.0+ baseline with current Android 17 / API 37 build target.
- Device capability detection instead of falsely claiming an effect is active.
- Future device-specific and rooted-device backends where they can be implemented safely.

## Important Android limitation

A normal Android application cannot universally intercept and rewrite the private audio stream of every other application. Audio effects are normally attached to an audio session, and Android documents global output-mix insert effects using session 0 as deprecated. Therefore Vplus uses the public audio-effect framework first and reports when an OEM/device does not expose a usable global path.

Android 17 also hardens background audio interactions, so future always-on operation will use an explicit user-started foreground-service design where required.

## Current engine

- `LoudnessEnhancer` for controlled loudness gain.
- `Equalizer` for speech clarity.
- Capability/control detection.
- Gain capped at +6 dB at the 200% UI setting in this first implementation.

The 200% value is a Vplus control target, not a guarantee of 2× acoustic SPL. Final loudness depends on the Android audio path, OEM mixer, speaker/headphone hardware, and the calling application.
