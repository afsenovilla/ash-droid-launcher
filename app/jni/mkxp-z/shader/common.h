#ifdef GLSLES

#ifdef FRAGMENT_SHADER
/* Only the fragment shader has no default float precision.
 * Use highp when available: with mediump (fp16 on Adreno/Mali) texture
 * coordinates into big tileset atlases lose precision, which shows up as
 * shifted tile rows and seams on the map. (ash-droid-launcher) */
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

#else

/* Desktop GLSL doesn't know about these */
#define highp
#define mediump
#define lowp

#endif
