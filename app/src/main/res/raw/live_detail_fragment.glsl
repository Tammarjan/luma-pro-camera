#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uTexelSize;
uniform float uDenoise;
uniform float uSharpen;
uniform float uClarity;
uniform float uTexture;
varying vec2 vTexSamplingCoord;
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
void main() {
  vec2 uv = vTexSamplingCoord;
  vec3 center = texture2D(uTexSampler, uv).rgb;
  vec3 n1 = texture2D(uTexSampler, uv + vec2(uTexelSize.x, 0.0)).rgb;
  vec3 n2 = texture2D(uTexSampler, uv - vec2(uTexelSize.x, 0.0)).rgb;
  vec3 n3 = texture2D(uTexSampler, uv + vec2(0.0, uTexelSize.y)).rgb;
  vec3 n4 = texture2D(uTexSampler, uv - vec2(0.0, uTexelSize.y)).rgb;
  vec3 n5 = texture2D(uTexSampler, uv + uTexelSize).rgb;
  vec3 n6 = texture2D(uTexSampler, uv - uTexelSize).rgb;
  vec3 n7 = texture2D(uTexSampler, uv + vec2(uTexelSize.x, -uTexelSize.y)).rgb;
  vec3 n8 = texture2D(uTexSampler, uv + vec2(-uTexelSize.x, uTexelSize.y)).rgb;
  float lc = luma(center);
  vec3 sum = center;
  float total = 1.0;
  vec3 neighbors[8];
  neighbors[0]=n1; neighbors[1]=n2; neighbors[2]=n3; neighbors[3]=n4;
  neighbors[4]=n5; neighbors[5]=n6; neighbors[6]=n7; neighbors[7]=n8;
  for (int i=0; i<8; i++) {
    float diff = abs(luma(neighbors[i]) - lc);
    float w = exp(-diff * 18.0);
    sum += neighbors[i] * w;
    total += w;
  }
  vec3 smoothColor = sum / total;
  vec3 denoised = mix(center, smoothColor, uDenoise * 0.72);
  vec3 average = (n1+n2+n3+n4) * 0.25;
  vec3 detail = center - average;
  float midtone = 1.0 - smoothstep(0.15, 0.85, lc);
  vec3 result = denoised;
  result += detail * uSharpen * 1.35;
  result += detail * uClarity * midtone * 1.15;
  result += detail * uTexture * 0.75;
  gl_FragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
}