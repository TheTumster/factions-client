#version 330

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

layout(std140) uniform MotionBlurConfig {
    float Strength;
};

in vec2 texCoord;

out vec4 fragColor;

// Blend this frame with the last blended frame: an exponential trail, like OptiFine-era motion blur.
void main() {
    vec3 now = texture(InSampler, texCoord).rgb;
    vec3 prev = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(now, prev, Strength), 1.0);
}
