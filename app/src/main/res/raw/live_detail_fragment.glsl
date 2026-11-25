#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uDenoise;
uniform float uSharpen;
uniform float uClarity;
uniform float uTexture;
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

  // Edge-aware 8-neighbour denoise. Strong enough to be visible at 100,
  // while reducing cross-edge smearing compared with a plain box blur.
  vec3 n1 = sampleAt(uv, vec2(-1.0, 0.0));
  vec3 n2 = sampleAt(uv, vec2( 1.0, 0.0));
  vec3 n3 = sampleAt(uv, vec2(0.0, -1.0));
  vec3 n4 = sampleAt(uv, vec2(0.0,  1.0));
  vec3 n5 = sampleAt(uv, vec2(-1.0, -1.0));
  vec3 n6 = sampleAt(uv, vec2( 1.0, -1.0));
  vec3 n7 = sampleAt(uv, vec2(-1.0,  1.0));
  vec3 n8 = sampleAt(uv, vec2( 1.0,  1.0));

  float y0 = luma(center);
  float w1 = exp(-abs(luma(n1)-y0)*18.0);
  float w2 = exp(-abs(luma(n2)-y0)*18.0);
  float w3 = exp(-abs(luma(n3)-y0)*18.0);
  float w4 = exp(-abs(luma(n4)-y0)*18.0);
  float w5 = exp(-abs(luma(n5)-y0)*18.0)*0.7071;
  float w6 = exp(-abs(luma(n6)-y0)*18.0)*0.7071;
  float w7 = exp(-abs(luma(n7)-y0)*18.0)*0.7071;
  float w8 = exp(-abs(luma(n8)-y0)*18.0)*0.7071;
  vec3 sum = center*1.5 + n1*w1+n2*w2+n3*w3+n4*w4+n5*w5+n6*w6+n7*w7+n8*w8;
  float total = 1.5+w1+w2+w3+w4+w5+w6+w7+w8;
  vec3 denoised = sum / max(total, 0.0001);

  // Fine detail and local contrast at different spatial scales.
  vec3 nearBlur = (n1+n2+n3+n4)*0.25;
  vec3 fineDetail = center-nearBlur;
  vec3 f1 = sampleAt(uv, vec2(-3.0, 0.0));
  vec3 f2 = sampleAt(uv, vec2( 3.0, 0.0));
  vec3 f3 = sampleAt(uv, vec2(0.0, -3.0));
  vec3 f4 = sampleAt(uv, vec2(0.0,  3.0));
  vec3 midBlur = (f1+f2+f3+f4)*0.25;
  vec3 localDetail = center-midBlur;

  float denoiseAmount = clamp(uDenoise, 0.0, 1.0);
  float sharpenAmount = clamp(uSharpen, 0.0, 1.0);
  float clarityAmount = clamp(uClarity, 0.0, 1.0);
  float textureAmount = clamp(uTexture, 0.0, 1.0);

  vec3 result = mix(center, denoised, denoiseAmount*0.95);
  result += fineDetail * sharpenAmount * 2.2;
  result += localDetail * clarityAmount * 2.8;
  result += fineDetail * textureAmount * 3.0;

  gl_FragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}
