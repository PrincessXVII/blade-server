                #version 330

                #moj_import <minecraft:dynamictransforms.glsl>
                #moj_import <minecraft:projection.glsl>
                #moj_import <minecraft:globals.glsl>

                in vec3 Position;
                in vec4 Color;
                in vec2 UV0;

                out vec4 vertexColor;
                out vec2 texCoord0;
                out vec4 effectData;
                const bool ORAXEN_ANIMATED_GLYPHS = true;
const bool ORAXEN_TEXT_EFFECTS = true;
const int ORAXEN_EFFECT_COUNT = 4;
const ivec3 ORAXEN_EFFECT_TRIGGERS[4] = ivec3[](
    ivec3(253, 13, 0), // rainbow (id=0)
    ivec3(253, 29, 0), // wave (id=1)
    ivec3(253, 45, 0), // shake (id=2)
    ivec3(253, 61, 0) // pulse (id=3)
);
const int ORAXEN_EFFECT_IDS[4] = int[](
    0,
    1,
    2,
    3
);

                

                    void main() {
                        vec3 pos = Position;
                        gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
                        texCoord0 = UV0;
                        vertexColor = Color;
                        effectData = vec4(-1.0, 0.0, 0.0, 0.0); // -1 means no effect

                        int rInt = int(Color.r * 255.0 + 0.5);
                        int gRaw = int(Color.g * 255.0 + 0.5);
                        int bRaw = int(Color.b * 255.0 + 0.5);

                        // Check for animation color: R=254 for primary, R≈63 for shadow
                        bool isPrimaryAnim = (rInt == 254);
                        bool isShadowAnim = (rInt >= 62 && rInt <= 64) && (gRaw >= 1) && (bRaw <= 64);

                        if (ORAXEN_ANIMATED_GLYPHS && (isPrimaryAnim || isShadowAnim)) {
                            int gInt = isPrimaryAnim ? gRaw : min(255, gRaw * 4);
                            int bInt = isPrimaryAnim ? bRaw : min(255, bRaw * 4);

                            bool loop = (gInt < 128);
                            float fps = max(1.0, float(gInt & 0x7F));
                            int frameIndex = bInt & 0x0F;
                            int totalFrames = ((bInt >> 4) & 0x0F) + 1;

                            float timeSeconds = (GameTime <= 1.0) ? (GameTime * 1200.0) : (GameTime / 20.0);
                            int rawFrame = int(floor(timeSeconds * fps));
                            int currentFrame = loop ? (rawFrame % totalFrames) : min(rawFrame, totalFrames - 1);

                            float visible = (frameIndex == currentFrame && isPrimaryAnim) ? 1.0 : 0.0;

                            if (isPrimaryAnim) {
                                vertexColor = vec4(1.0, 1.0, 1.0, visible);
                            } else {
                                vertexColor = vec4(0.0);
                            }
                        }

                        // Text effects: exact trigger color matching
                        if (ORAXEN_TEXT_EFFECTS && ORAXEN_EFFECT_COUNT > 0 && (!ORAXEN_ANIMATED_GLYPHS || (!isPrimaryAnim && !isShadowAnim))) {
                            // Check for exact trigger color match
                            ivec3 colorInt = ivec3(rInt, gRaw, bRaw);
                            int effectType = -1;
                            for (int i = 0; i < ORAXEN_EFFECT_COUNT; i++) {
                                if (colorInt == ORAXEN_EFFECT_TRIGGERS[i]) {
                                    effectType = ORAXEN_EFFECT_IDS[i];
                                    break;
                                }
                            }

                            if (effectType >= 0) {
                                float speed = 3.0; // Default speed (configured in shader snippets)
                                float param = 3.0; // Default param (configured in shader snippets)
                                float charIndex = float(gl_VertexID >> 2);

                                float timeSeconds = (GameTime <= 1.0) ? (GameTime * 1200.0) : (GameTime / 20.0);

                            // wave (id=1)
                            if (effectType == 1) {
                                float phase = charIndex * 0.6 + timeSeconds * 6.0;
                                pos.y += sin(phase) * 2.0;
                            }
                            // shake (id=2)
                            else if (effectType == 2) {
                                float seed = charIndex + floor(timeSeconds * 32.0);
                                pos.x += (fract(sin(seed * 12.9898) * 43758.5453) - 0.5) * 1.5;
                                pos.y += (fract(sin(seed * 78.233) * 43758.5453) - 0.5) * 1.5;
                            }


                                gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

                                // Pass effect data to fragment shader
                                effectData = vec4(float(effectType), speed, charIndex, param);
                            }
                        }
                    }
