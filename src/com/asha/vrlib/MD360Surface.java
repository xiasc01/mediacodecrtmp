package com.asha.vrlib;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;
import android.view.Surface;
import com.example.mediacodecrtmp.DataManager;

import javax.microedition.khronos.opengles.GL10;

import java.nio.ByteBuffer;

import static com.asha.vrlib.common.GLUtil.glCheck;


/**
 * copied from surfaceTexture
 * Created by nitro888 on 15. 4. 5..
 * https://github.com/Nitro888/NitroAction360
 */
public class MD360Surface {
    public static final int SURFACE_TEXTURE_EMPTY = 0;

    private Surface mSurface;
    private SurfaceTexture mSurfaceTexture;
    private int mGlSurfaceTexture;
    private int mWidth;
    private int mHeight;
    private IOnSurfaceReadyListener mOnSurfaceReadyListener;

    public MD360Surface(IOnSurfaceReadyListener onSurfaceReadyListener) {
        this.mOnSurfaceReadyListener = onSurfaceReadyListener;
    }

    public Surface getSurface() {
        return mSurface;
    }

    public void resize(int width, int height) {
        boolean changed = false;
        if (mWidth == width && mHeight == height) changed = true;
        mWidth = width;
        mHeight = height;

        // resize the texture
        if (changed && mSurfaceTexture != null)
            mSurfaceTexture.setDefaultBufferSize(mWidth, mHeight);

    }

    public void createSurface() {
        if (mSurface != null) return;

        releaseSurface();
        mGlSurfaceTexture = createTexture();
        if (mGlSurfaceTexture != SURFACE_TEXTURE_EMPTY) {
            //attach the texture to a surface.
            //It's a clue class for rendering an android view to gl level
            mSurfaceTexture = new SurfaceTexture(mGlSurfaceTexture);
            mSurfaceTexture.setDefaultBufferSize(mWidth, mHeight);
            mSurface = new Surface(mSurfaceTexture);
            if (mOnSurfaceReadyListener != null)
                mOnSurfaceReadyListener.onSurfaceReady(mSurface);
        }
    }

    private void releaseSurface() {
        if (mSurface != null) {
            mSurface.release();
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
        }
        mSurface = null;
        mSurfaceTexture = null;
    }

    private int createTexture() {
        mGlSurfaceTexture = SURFACE_TEXTURE_EMPTY;
        int[] textures = new int[1];

        // Generate the texture to where android view will be rendered
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glGenTextures(1, textures, 0);
        glCheck("Texture generate");

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
        glCheck("Texture bind");

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        return textures[0];
    }

    public void onDrawFrame(MediaCodec decoder) {
        if (mGlSurfaceTexture == SURFACE_TEXTURE_EMPTY)
            return;

        if (decoder == null) {
            Log.e("MD360Surface", "onDrawFrame--->decoder == null");
            return;
        }
        ByteBuffer[] inputBuffers = decoder.getInputBuffers();
        ByteBuffer[] outputBuffers = decoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long startMs = System.currentTimeMillis();

        Log.i("MyActivity", "while start...");
        if (DataManager.getInstance().inputBytesQueue.size() > 0) {
            Log.i("MyActivity", "before decoder.dequeueInputBuffer(0)");
            int inIndex = decoder.dequeueInputBuffer(0);
            Log.i("MyActivity", "inIndex = " + inIndex);
            if (inIndex >= 0) {
                byte[] buf = DataManager.getInstance().inputBytesQueue.poll();
                Log.i("MyActivity", "inIndex >= 0, inputBytesQueue.poll()");
                int startIndex = 0;
                byte[] temp = null;
                if (buf != null) {
                    if (buf[11] == 23 && buf[12] == 1) {
                        // I帧数据
                        startIndex = 100;

                    } else if (buf[11] == 39 && buf[12] == 1) {
                        // p帧数据
                        if (buf[25] == 12) {
                            startIndex = 38;
                        } else if (buf[25] == 11) {
                            startIndex = 37;
                        }
                    }
                    int len = (buf[startIndex] & 0x000000FF) << 24 | (buf[startIndex + 1] & 0x000000FF) << 16 |
                            (buf[startIndex + 2] & 0x000000FF) << 8 | buf[startIndex + 3] & 0x000000FF;
                    temp = new byte[len + 8];
                    temp[0] = 0;
                    temp[1] = 0;
                    temp[2] = 0;
                    temp[3] = 1;
                    temp[len + 4] = 0;
                    temp[len + 5] = 0;
                    temp[len + 6] = 0;
                    temp[len + 7] = 1;

                    System.arraycopy(buf, startIndex + 4, temp, 4, len);

                    int sampleSize = temp.length;
                    ByteBuffer buffer = inputBuffers[inIndex];
                    buffer.clear();
                    buffer.put(temp, 0, sampleSize);
                    if (sampleSize < 0) {
                        Log.i("MyActivity", "sampleSize < 0");
                        // We shouldn't stop the playback at this point, just pass the EOS
                        // flag to decoder, we will get it again from the
                        // dequeueOutputBuffer
                        Log.d("MyActivity", "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, 0, 0);
                    }
                }
            } else {
                Log.i("MyActivity", "inIndex < 0");
            }
        }


        int outIndex = decoder.dequeueOutputBuffer(info, 10000);
        switch (outIndex) {
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                Log.i("MyActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
                outputBuffers = decoder.getOutputBuffers();
                break;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                Log.i("MyActivity", "New format " + decoder.getOutputFormat());
                break;
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                Log.i("MyActivity", "dequeueOutputBuffer timed out!");
                break;
            default:
                ByteBuffer buffer = outputBuffers[outIndex];

                Log.v("MyActivity", "We can't use this buffer but render it due to the API limit, " + buffer);

                // We use a very simple clock to keep the video FPS, or the video
                // playback will be too fast
                while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }
                Log.i("MyActivity", "releaseOutputBuffer");
                decoder.releaseOutputBuffer(outIndex, true);
                break;

        }

        // All decoded frames have been rendered, we can stop playing now
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.i("MyActivity", "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
            decoder.stop();
            decoder.release();
        }


        synchronized (this) {
            mSurfaceTexture.updateTexImage();
        }
    }

    public interface IOnSurfaceReadyListener {
        void onSurfaceReady(Surface surface);
    }
}
