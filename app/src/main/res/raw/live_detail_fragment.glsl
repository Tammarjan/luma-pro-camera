#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uDenoise;
uniform float uSharpen;
uniform float uClarity;
uniform float uTexture;
uniform float uHighlights;
uniform float uShadows;
uniform float uHdrStrength;
varying vec2 vTexSamplingCoord;

float luma(vec3 c) {
  return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 sampleAt(vec2 uv, vec2 offset) {
  return texture2D(uTexSampler, clamp(uv + offset * uTexelSize, vec2(0.001), vec2(0.999))).rgb;
}

void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 center = texture2D(uTexSampler, uv).rgb;
  float y0 = luma(center);

  float denoiseAmount = clamp(uDenoise, 0.0, 1.0);
  vec3 result = center;

  // Skip the bilateral neighborhood entirely when denoise is not requested.
  if (denoiseAmount > 0.001) {
    vec3 neighbours = vec3(0.0);
    float weightSum = 0.0;
    for (int y = -1; y <= 1; y++) {
      for (int x = -1; x <= 1; x++) {
        if (x == 0 && y == 0) continue;
        vec2 offset = vec2(float(x), float(y));
        vec3 sampleColor = sampleAt(uv, offset);
        float spatial = exp(-dot(offset, offset) * 0.42);
        float rangeWeight = exp(-abs(luma(sampleColor) - y0) * 10.0);
        float weight = spatial * rangeWeight;
        neighbours += sampleColor * weight;
        weightSum += weight;
      }
    }
    vec3 denoised = (center * 0.30 + neighbours) / max(0.30 + weightSum, 0.0001);
    result = mix(center, denoised, denoiseAmount * 0.98);
  }

  // Each detail scale samples only when its matching control is active.
  float sharpenAmount = clamp(uSharpen, 0.0, 1.0);
  float clarityAmount = clamp(uClarity, 0.0, 1.0);
  float textureAmount = clamp(uTexture, 0.0, 1.0);

  if (sharpenAmount > 0.001) {
    vec3 nearBlur = (
      sampleAt(uv, vec2(-1.5, 0.0)) +
      sampleAt(uv, vec2( 1.5, 0.0)) +
      sampleAt(uv, vec2(0.0, -1.5)) +
      sampleAt(uv, vec2(0.0,  1.5))
    ) * 0.25;
    float fineY = luma(result - nearBlur);
    float edgeMask = smoothstep(0.008, 0.075, abs(fineY));
    result += vec3(fineY) * sharpenAmount * 3.8 * edgeMask;
  }

  if (textureAmount > 0.001) {
    vec3 midBlur = (
      sampleAt(uv, vec2(-3.5, 0.0)) +
      sampleAt(uv, vec2( 3.5, 0.0)) +
      sampleAt(uv, vec2(0.0, -3.5)) +
      sampleAt(uv, vec2(0.0,  3.5))
    ) * 0.25;
    float mediumY = luma(result - midBlur);
    float textureMask = smoothstep(0.004, 0.045, abs(mediumY));
    result += vec3(mediumY) * textureAmount * 2.8 * textureMask;
  }

  if (clarityAmount > 0.001) {
    vec3 broadBlur = (
      sampleAt(uv, vec2(-7.0, 0.0)) +
      sampleAt(uv, vec2( 7.0, 0.0)) +
      sampleAt(uv, vec2(0.0, -7.0)) +
      sampleAt(uv, vec2(0.0,  7.0))
    ) * 0.25;
    float broadY = luma(result - broadBlur);
    float clarityMask = smoothstep(0.012, 0.12, abs(broadY));
    result += vec3(broadY) * clarityAmount * 2.5 * clarityMask;
  }

  // Stronger but controlled shadow/highlight shaping. These are single-frame
  // tone controls; they cannot restore sensor detail that was clipped away.
  float currentY = clamp(luma(max(result, vec3(0.0))), 0.0, 1.0);
  float shadowMask = 1.0 - smoothstep(0.10, 0.72, currentY);
  float highlightMask = smoothstep(0.28, 0.92, currentY);
  float shadowStrength = clamp(uShadows, 0.0, 1.0) * 0.34 + clamp(uHdrStrength, 0.0, 1.0) * 0.12;
  float highlightStrength = clamp(uHighlights, 0.0, 1.0) * 0.38 + clamp(uHdrStrength, 0.0, 1.0) * 0.20;
  float mappedY = currentY + shadowMask * shadowStrength * (1.0 - currentY);
  mappedY = mappedY - highlightMask * highlightStrength * currentY;
  mappedY = clamp(mappedY, 0.0, 1.0);
  float ratio = mappedY / max(currentY, 0.035);
  result *= clamp(ratio, 0.52, 1.65);

  gl_FragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
