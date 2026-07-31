                #version 330

                #moj_import <minecraft:fog.glsl>
                #moj_import <minecraft:dynamictransforms.glsl>
                #moj_import <minecraft:globals.glsl>

                uniform sampler2D Sampler0;

                in float sphericalVertexDistance;
                in float cylindricalVertexDistance;
                in vec4 vertexColor;
                in vec2 texCoord0;
                in vec4 effectData;

                out vec4 fragColor;

                // HSV to RGB conversion for rainbow effect
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0/3.0, 1.0/3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

                void main() {
                    vec4 color = texture(Sampler0, texCoord0).rrrr * vertexColor * ColorModulator;
                    vec4 texColor = color;

                    // Apply text effects if effectData.x >= 0 (effectType, 0 is rainbow)
                    if (effectData.x >= 0.0 && effectData.y > 0.5) {
                        int effectType = int(effectData.x + 0.5);
                        float speed = effectData.y;
                        float charIndex = effectData.z;
                        float param = effectData.w;
                        float timeSeconds = (GameTime <= 1.0) ? (GameTime * 1200.0) : (GameTime / 20.0);

                            // rainbow (id=0)
                            if (effectType == 0) {
                                float hue = fract(charIndex * 0.03 + timeSeconds * 0.09);
                                texColor.rgb = hsv2rgb(vec3(hue, 0.9, 1.0));
                            }
                            // wave (id=1)
                            else if (effectType == 1) {
                                texColor.rgb = vec3(0.333, 0.804, 0.988);
                            }
                            // shake (id=2)
                            else if (effectType == 2) {
                                texColor.rgb = vec3(1.0, 0.42, 0.42);
                            }
                            // pulse (id=3)
                            else if (effectType == 3) {
                                texColor.rgb = vec3(1.0, 0.85, 0.24);
                                float pulse = (sin(timeSeconds * 1.5 + charIndex * 0.3) + 1.0) * 0.5;
                                texColor.a *= 0.3 + pulse * 0.7;
                            }

                    }

                    color = texColor;

                    if (color.a < 0.1) {
                        discard;
                    }
                    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
                }
