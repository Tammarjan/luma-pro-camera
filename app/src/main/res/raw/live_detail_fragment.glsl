#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uDenoise;
uniform float uSharpen;
uniform float uClarity;
uniform float uTexture;
varying vec2 vTexSamplingCoord;

float luma(vec3 c) {
  return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 center = texture2D(uTexSampler, uv).rgb;

  vec3 left = texture2D(uTexSampler, uv - vec2(uTexelSize.x, 0.0)).rgb;
  vec3 right = texture2D(uTexSampler, uv + vec2(uTexelSize.x, 0.0)).rgb;
  vec3 up = texture2D(uTexSampler, uv - vec2(0.0, uTexelSize.y)).rgb;
  vec3 down = texture2D(uTexSampler, uv + vec2(0.0, uTexelSize.y)).rgb;

  vec3 diagonalA = texture2D(uTexSampler, uv + uTexelSize).rgb;
  vec3 diagonalB = texture2D(uTexSampler, uv - uTexelSize).rgb;
  vec3 diagonalC = texture2D(uTexSampler, uv + vec2(uTexelSize.x, -uTexelSize.y)).rgb;
  vec3 diagonalD = texture2D(uTexSampler, uv + vec2(-uTexelSize.x, uTexelSize.y)).rgb;

  float centerLuma = luma(center);
  float wL = exp(-abs(luma(left) - centerLuma) * 28.0);
  float wR = exp(-abs(luma(right) - centerLuma) * 28.0);
  float wU = exp(-abs(luma(up) - centerLuma) * 28.0);
  float wD = exp(-abs(luma(down) - centerLuma) * 28.0);
  float wA = exp(-abs(luma(diagonalA) - centerLuma) * 28.0);
  float wB = exp(-abs(luma(diagonalB) - centerLuma) * 28.0);
  float wC = exp(-abs(luma(diagonalC) - centerLuma) * 28.0);
  float wDg = exp(-abs(luma(diagonalD) - centerLuma) * 28.0);

  vec3 weighted = center + left*wL + right*wR + up*wU + down*wD
      + diagonalA*wA + diagonalB*wB + diagonalC*wC + diagonalD*wDg;
  float weightSum = 1.0 + wL + wR + wU + wD + wA + wB + wC + wDg;
  vec3 denoised = mix(center, weighted / weightSum, clamp(uDenoise, 0.0, 1.0) * 0.92);

  vec3 nearAverage = (left + right + up + down) * 0.25;
  vec3 detail = center - nearAverage;
  vec2 farStep = uTexelSize * 3.0;
  vec3 farAverage = (
      texture2D(uTexSampler, uv + vec2(farStep.x, 0.0)).rgb +
      texture2D(uTexSampler, uv - vec2(farStep.x, 0.0)).rgb +
      texture2D(uTexSampler, uv + vec2(0.0, farStep.y)).rgb +
      texture2D(uTexSampler, uv - vec2(0.0, farStep.y)).rgb
  ) * 0.25;
  vec3 textureDetail = center - farAverage;
  float midtone = 1.0 - smoothstep(0.12, 0.62, abs(centerLuma - 0.5));

  vec3 result = denoised;
  result += detail * clamp(uSharpen, 0.0, 1.0) * 1.35;
  result += detail * clamp(uClarity, 0.0, 1.0) * midtone * 2.0;
  result += textureDetail * clamp(uTexture, 0.0, 1.0) * 1.8;
  gl_FragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
