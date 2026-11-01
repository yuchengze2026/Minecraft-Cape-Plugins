#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 texCoord1;

out vec4 fragColor;

in float capeBridgeOwn;
in vec3 capeBridgeViewPosition;
void main() {
    // Clip only the owner's marked cape close to their camera; F5 remains visible.
    if (capeBridgeOwn > 0.5 && dot(capeBridgeViewPosition,capeBridgeViewPosition) < 2.7225) discard;

    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
