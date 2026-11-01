#version 330

#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec4 lightMapColor;
out vec4 overlayColor;

out vec2 texCoord0;

out float capeBridgeOwn;
out vec3 capeBridgeViewPosition;
void main() {
    capeBridgeOwn = all(lessThan(abs(Color.rgb - vec3(13.0,23.0,227.0)/255.0), vec3(0.5/255.0))) ? 1.0 : 0.0;
    vec4 capeBridgeColor = capeBridgeOwn > 0.5 ? vec4(1.0,1.0,1.0,Color.a) : Color;
    capeBridgeViewPosition = (ModelViewMat * vec4(Position, 1.0)).xyz;

    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, capeBridgeColor);
    lightMapColor = sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);

    texCoord0 = UV0;
}
