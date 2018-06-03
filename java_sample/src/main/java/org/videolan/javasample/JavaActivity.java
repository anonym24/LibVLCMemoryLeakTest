package org.videolan.javasample;

import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;

public class JavaActivity extends AppCompatActivity implements IVLCVout.OnNewVideoLayoutListener {
    private static final String TAG = "JavaActivity";
    private static final String SAMPLE_URL = "http://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_640x360.m4v";

    private FrameLayout mVideoSurfaceFrame = null;
    private SurfaceView mVideoSurface = null;
    private SurfaceView mSubtitlesSurface = null;
    private View mVideoView = null;

    private Handler mHandler;
    private View.OnLayoutChangeListener mOnLayoutChangeListener = null;

    private LibVLC mLibVLC = null;
    private MediaPlayer mMediaPlayer = null;
    private int mVideoHeight = 0;
    private int mVideoWidth = 0;
    private int mVideoVisibleHeight = 0;
    private int mVideoVisibleWidth = 0;
    private int mVideoSarNum = 0;
    private int mVideoSarDen = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        setContentView(R.layout.activity_main);
        mVideoSurfaceFrame = findViewById(R.id.video_surface_frame);
        ViewStub stub = findViewById(R.id.surface_stub);
        mVideoSurface = (SurfaceView) stub.inflate();
        mVideoView = mVideoSurface;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                stop();
                start();
                mHandler.postDelayed(this, 2000);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        stop();
    }

    private void start() {
        Log.i(TAG, "start");
        final ArrayList<String> args = new ArrayList<>();
        args.add("-vvv");
        mLibVLC = new LibVLC(this, args);
        Log.i(TAG, "LibVLC version " + mLibVLC.version());
        mMediaPlayer = new MediaPlayer(mLibVLC);
        final IVLCVout vlcVout = mMediaPlayer.getVLCVout();
        if (mVideoSurface != null) {
            vlcVout.setVideoView(mVideoSurface);
            if (mSubtitlesSurface != null)
                vlcVout.setSubtitlesView(mSubtitlesSurface);
        }
        vlcVout.attachViews(this);

        //Media media = new Media(mLibVLC, SAMPLE_URL);
        Media media = new Media(mLibVLC, Uri.parse(SAMPLE_URL));
        mMediaPlayer.setMedia(media);
        media.release();
        mMediaPlayer.play();

        if (mOnLayoutChangeListener == null) {
            mOnLayoutChangeListener = new View.OnLayoutChangeListener() {
                private final Runnable mRunnable = new Runnable() {
                    @Override
                    public void run() {
                        updateVideoSurfaces();
                    }
                };
                @Override
                public void onLayoutChange(View v, int left, int top, int right,
                                           int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                        mHandler.removeCallbacks(mRunnable);
                        mHandler.post(mRunnable);
                    }
                }
            };
        }
        mVideoSurfaceFrame.addOnLayoutChangeListener(mOnLayoutChangeListener);
    }

    private void stop() {
        if (mMediaPlayer != null) {
            Log.i(TAG, "stop");
            if (mOnLayoutChangeListener != null) {
                mVideoSurfaceFrame.removeOnLayoutChangeListener(mOnLayoutChangeListener);
                mOnLayoutChangeListener = null;
            }
            mMediaPlayer.stop();
            mMediaPlayer.getVLCVout().detachViews();
            //mMediaPlayer.getMedia().release();
            mMediaPlayer.release();
            mLibVLC.release();
            mMediaPlayer = null;
            mLibVLC = null;
        }
    }

    private void updateVideoSurfaces() {
        int sw = getWindow().getDecorView().getWidth();
        int sh = getWindow().getDecorView().getHeight();
        if (sw * sh == 0) {
            return;
        }
        mMediaPlayer.getVLCVout().setWindowSize(sw, sh);
        ViewGroup.LayoutParams lp = mVideoView.getLayoutParams();
        if (mVideoWidth * mVideoHeight == 0) {
            lp.width  = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mVideoView.setLayoutParams(lp);
            lp = mVideoSurfaceFrame.getLayoutParams();
            lp.width  = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            mVideoSurfaceFrame.setLayoutParams(lp);
            mMediaPlayer.setAspectRatio(null);
            mMediaPlayer.setScale(0);
            return;
        }
        if (lp.width == lp.height && lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
            mMediaPlayer.setAspectRatio(null);
            mMediaPlayer.setScale(0);
        }
        double dw = sw, dh = sh;
        final boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (sw > sh && isPortrait || sw < sh && !isPortrait) {
            dw = sh;
            dh = sw;
        }
        double ar, vw;
        if (mVideoSarDen == mVideoSarNum) {
            ar = (double)mVideoVisibleWidth / (double)mVideoVisibleHeight;
        } else {
            vw = mVideoVisibleWidth * (double)mVideoSarNum / mVideoSarDen;
            ar = vw / mVideoVisibleHeight;
        }
        double dar = dw / dh;
        if (dar < ar)
            dh = dw / ar;
        else
            dw = dh * ar;
        lp.width  = (int) Math.ceil(dw * mVideoWidth / mVideoVisibleWidth);
        lp.height = (int) Math.ceil(dh * mVideoHeight / mVideoVisibleHeight);
        mVideoView.setLayoutParams(lp);
        if (mSubtitlesSurface != null)
            mSubtitlesSurface.setLayoutParams(lp);
        lp = mVideoSurfaceFrame.getLayoutParams();
        lp.width = (int) Math.floor(dw);
        lp.height = (int) Math.floor(dh);
        mVideoSurfaceFrame.setLayoutParams(lp);
        mVideoView.invalidate();
        if (mSubtitlesSurface != null)
            mSubtitlesSurface.invalidate();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    public void onNewVideoLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        mVideoWidth = width;
        mVideoHeight = height;
        mVideoVisibleWidth = visibleWidth;
        mVideoVisibleHeight = visibleHeight;
        mVideoSarNum = sarNum;
        mVideoSarDen = sarDen;
        updateVideoSurfaces();
    }
}
