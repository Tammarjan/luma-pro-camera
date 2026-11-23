package com.lumapro.camera

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.io.IOException

@UnstableApi
class LiveDetailEffect(
    private val denoise: Float,
    private val sharpening: Float,
    private val clarity: Float,
    private val texture: Float
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return LiveDetailShaderProgram(context, useHdr, denoise, sharpening, clarity, texture)
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
        return denoise <= 0f && sharpening <= 0f && clarity <= 0f && texture <= 0f
    }
}

@UnstableApi
private class LiveDetailShaderProgram(
    context: Context,
    useHdr: Boolean,
    denoise: Float,
    sharpening: Float,
    clarity: Float,
    texture: Float
) : BaseGlShaderProgram(useHdr, 1) {
    private val glProgram: GlProgram

    init {
        try {
            val vertexShader = context.resources.openRawResource(R.raw.live_detail_vertex).bufferedReader().use { it.readText() }
            val fragmentShader = context.resources.openRawResource(R.raw.live_detail_fragment).bufferedReader().use { it.readText() }
            glProgram = GlProgram(vertexShader, fragmentShader)
        } catch (e: IOException) {
            throw VideoFrameProcessingException(e)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
        glProgram.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        val identity = GlUtil.create4x4IdentityMatrix()
        glProgram.setFloatsUniform("uTransformationMatrix", identity)
        glProgram.setFloatsUniform("uTexTransformationMatrix", identity)
        glProgram.setFloatUniform("uDenoise", denoise.coerceIn(0f, 1f))
        glProgram.setFloatUniform("uSharpen", sharpening.coerceIn(0f, 1f))
        glProgram.setFloatUniform("uClarity", clarity.coerceIn(0f, 1f))
        glProgram.setFloatUniform("uTexture", texture.coerceIn(0f, 1f))
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        glProgram.setFloatsUniform("uTexelSize", floatArrayOf(1f / inputWidth.coerceAtLeast(1), 1f / inputHeight.coerceAtLeast(1)))
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }
}
