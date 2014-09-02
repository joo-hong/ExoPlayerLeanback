package com.nemustech.exoplayerleanback;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer.ExoPlaybackException;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.ExoPlayerLibraryInfo;
import com.google.android.exoplayer.FrameworkSampleSource;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.VideoSurfaceView;

import com.nemustech.exoplayerleanback.util.*;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by jhlee on 14. 8. 8.
 */
public class ExoplayerActivity extends Activity implements SurfaceHolder.Callback,
        ExoPlayer.Listener, MediaCodecVideoTrackRenderer.EventListener{

    private static final String TAG = "ExoPlayerActivity";

    private static final int HIDE_CONTROLLER_TIME = 5000;
    private static final int SEEKBAR_DELAY_TIME = 100;
    private static final int SEEKBAR_INTERVAL_TIME = 1000;
    private static final int MIN_SCRUB_TIME = 3000;
    private static final int SCRUB_SEGMENT_DIVISOR = 30;
    private static final double MEDIA_BAR_TOP_MARGIN = 0.8;
    private static final double MEDIA_BAR_RIGHT_MARGIN = 0.2;
    private static final double MEDIA_BAR_BOTTOM_MARGIN = 0.0;
    private static final double MEDIA_BAR_LEFT_MARGIN = 0.2;
    private static final double MEDIA_BAR_HEIGHT = 0.1;
    private static final double MEDIA_BAR_WIDTH = 0.9;

    private DisplayMetrics mMetrics;
    private VideoSurfaceView mSurfaceView;
    private TextView mStartText;
    private TextView mEndText;
    private SeekBar mSeekbar;
    private ImageView mPlayPause;
    private ProgressBar mLoading;
    private View mControllers;
    private Movie mSelectedMovie;
    private boolean mShouldStartPlayback;
    private PlaybackState mPlaybackState;
    private Timer mControllersTimer;
    private boolean mControllersVisible;
    private final Handler mHandler = new Handler();

    private Handler mainHandler;

    private MediaCodecVideoTrackRenderer videoRenderer;
    private MediaCodecAudioTrackRenderer audioRenderer;
    private ExoPlayer exoPlayer;
    private RendererBuilderCallback callback;
    private RendererBuilder builder;

    private Uri contentUri;
    private int contentType;
    private String contentId;

    public static final int TYPE_DASH_VOD = 0;
    public static final int TYPE_SS_VOD = 1;
    public static final int TYPE_OTHER = 2;

    /**
     * Builds renderers for the player.
     */
    public interface RendererBuilder {
        void buildRenderers(RendererBuilderCallback callback);
    }

    /* package */
    public final class RendererBuilderCallback {
        public void onRenderers(MediaCodecVideoTrackRenderer videoRenderer,
                                MediaCodecAudioTrackRenderer audioRenderer) {
            ExoplayerActivity.this.onRenderers(this, videoRenderer, audioRenderer);
        }

        public void onRenderersError(Exception e) {
            ExoplayerActivity.this.onRenderersError(this, e);
        }
    }

    private void onRenderers(RendererBuilderCallback rendererBuilderCallback, MediaCodecVideoTrackRenderer videoRenderer, MediaCodecAudioTrackRenderer audioRenderer) {
        if (this.callback != callback) {
            return;
        }
        this.callback = null;
        this.videoRenderer = videoRenderer;
        exoPlayer.prepare(videoRenderer, audioRenderer);

        Log.e(TAG, "onRenderers");
        startVideoPlayer();
    }

    private void onRenderersError(RendererBuilderCallback callback, Exception e) {
        if (this.callback != callback) {
            return;
        }
        this.callback = null;
        onError(e);
    }

    private void onError(Exception e) {
        Log.e(TAG, "Playback failed", e);
        Toast.makeText(this, "Playback failed", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.e(TAG, "surfaceCreated");
        startVideoPlayer();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {

    }

    @Override
    public void onVideoSizeChanged(int width, int height) {

    }

    @Override
    public void onDrawnToSurface(Surface surface) {

    }

    @Override
    public void onDecoderInitializationError(MediaCodecTrackRenderer.DecoderInitializationException e) {

    }

    @Override
    public void onCryptoError(MediaCodec.CryptoException e) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {

    }

    @Override
    public void onPlayWhenReadyCommitted() {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {

    }

    /*
     * List of various states that we can be in
     */
    public static enum PlaybackState {
        PLAYING, PAUSED, BUFFERING, IDLE;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.exoplayer_activity);

        mMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        mainHandler = new Handler(getMainLooper());

        contentType = getIntent().getIntExtra("content_type", 3);

        loadViews();
        setupController();
        setupControlsCallbacks();
        if(contentType == 3) prepareVideoPlayer();
        else {
            contentUri = getIntent().getData();
            contentId = getIntent().getStringExtra("content_id");

            builder = getRendererBuilder();
        }
        //startVideoPlayer();
        updateMetadata(true);
    }

    private void loadViews() {
        mSurfaceView = (VideoSurfaceView) findViewById(R.id.surface_view);
        mStartText = (TextView) findViewById(R.id.exo_startText);
        mEndText = (TextView) findViewById(R.id.exo_endText);
        mSeekbar = (SeekBar) findViewById(R.id.exo_seekBar);
        mPlayPause = (ImageView) findViewById(R.id.exo_playpause);
        mLoading = (ProgressBar) findViewById(R.id.exo_progressBar);
        mControllers = findViewById(R.id.exo_controllers);

//        mVideoView.setOnClickListener(mPlayPauseHandler);

        mSurfaceView.getHolder().addCallback(this);
    }

    private void setupController() {

        int w = (int) (mMetrics.widthPixels * MEDIA_BAR_WIDTH);
        int h = (int) (mMetrics.heightPixels * MEDIA_BAR_HEIGHT);
        int marginLeft = (int) (mMetrics.widthPixels * MEDIA_BAR_LEFT_MARGIN);
        int marginTop = (int) (mMetrics.heightPixels * MEDIA_BAR_TOP_MARGIN);
        int marginRight = (int) (mMetrics.widthPixels * MEDIA_BAR_RIGHT_MARGIN);
        int marginBottom = (int) (mMetrics.heightPixels * MEDIA_BAR_BOTTOM_MARGIN);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.setMargins(marginLeft, marginTop, marginRight, marginBottom);
        mControllers.setLayoutParams(lp);
        mStartText.setText(getResources().getString(R.string.init_text));
        mEndText.setText(getResources().getString(R.string.init_text));
    }

    private void setupControlsCallbacks() {
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Setup the player
        Log.e(TAG, "contentType = " + contentType);
        if(contentType != 3) {
            exoPlayer = ExoPlayer.Factory.newInstance(2, 1000, 5000);
            exoPlayer.addListener(this);
            // Request the renderers
            callback = new RendererBuilderCallback();
            builder.buildRenderers(callback);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Release the player
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        callback = null;
        videoRenderer = null;
    }

    private void prepareVideoPlayer() {
        Bundle b = getIntent().getExtras();
        mSelectedMovie = (Movie) getIntent().getSerializableExtra(
                getResources().getString(R.string.movie));
        if (null != b) {
            mShouldStartPlayback = b.getBoolean(getResources().getString(R.string.should_start));
            int startPosition = b.getInt(getResources().getString(R.string.start_position), 0);
            Uri uri = Uri.parse(mSelectedMovie.getVideoUrl());

            Log.e(TAG, "URI is " + uri.toString());
            final int numRenderers = 2;
            FrameworkSampleSource sampleSource =
                    new FrameworkSampleSource(this, uri, /* headers */ null, numRenderers);

            videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource,
                    MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    , 0, this.getMainHandler(), this, 50);
            audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);


            exoPlayer = ExoPlayer.Factory.newInstance(numRenderers);
            exoPlayer.prepare(videoRenderer, audioRenderer);

            startVideoPlayer();
        }
    }

    private void startVideoPlayer() {
        Log.e(TAG, "startVideoPlayer !!!!!");
        Surface surface = mSurfaceView.getHolder().getSurface();

        if (surface == null || !surface.isValid()) {
            // We're not ready yet.
            Log.e(TAG, "surface is not ready yet!!!!!");
            return;
        }

        if (videoRenderer == null)
        {
            Log.e(TAG, "videoRenderer is not ready yet!!!!!");
            return;
        }

        exoPlayer.sendMessage(videoRenderer, MediaCodecVideoTrackRenderer.MSG_SET_SURFACE, surface);
        //mVideoView.setVideoPath(mSelectedMovie.getVideoUrl());
        mShouldStartPlayback = true;
        if (mShouldStartPlayback) {
            mPlaybackState = PlaybackState.PLAYING;
            updatePlayButton(mPlaybackState);
//            if (startPosition > 0) {
//                //mVideoView.seekTo(startPosition);
//            }
            //mVideoView.start();
            exoPlayer.setPlayWhenReady(true);
            mPlayPause.requestFocus();
            startControllersTimer();
        } else {
            updatePlaybackLocation();
            mPlaybackState = PlaybackState.PAUSED;
            updatePlayButton(mPlaybackState);
        }
    }

    public Handler getMainHandler() {
        return mainHandler;
    }

    private void updatePlayButton(PlaybackState state) {
        switch (state) {
            case PLAYING:
                mLoading.setVisibility(View.INVISIBLE);
                mPlayPause.setVisibility(View.VISIBLE);
                mPlayPause.setImageDrawable(
                        getResources().getDrawable(R.drawable.ic_pause_playcontrol_normal));
                break;
            case PAUSED:
            case IDLE:
                mLoading.setVisibility(View.INVISIBLE);
                mPlayPause.setVisibility(View.VISIBLE);
                mPlayPause.setImageDrawable(
                        getResources().getDrawable(R.drawable.ic_play_playcontrol_normal));
                break;
            case BUFFERING:
                mPlayPause.setVisibility(View.INVISIBLE);
                mLoading.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }
    }

    private void updateMetadata(boolean visible) {
        //    mVideoView.invalidate();
    }

    private void startControllersTimer() {
        if (null != mControllersTimer) {
            mControllersTimer.cancel();
        }
        mControllersTimer = new Timer();
        mControllersTimer.schedule(new HideControllersTask(), HIDE_CONTROLLER_TIME);
    }

    private void stopControllersTimer() {
        if (null != mControllersTimer) {
            mControllersTimer.cancel();
        }
    }

    private void updatePlaybackLocation() {
        if (mPlaybackState == PlaybackState.PLAYING ||
                mPlaybackState == PlaybackState.BUFFERING) {
            startControllersTimer();
        } else {
            stopControllersTimer();
        }
    }

    private void updateControllersVisibility(boolean show) {
        if (show) {
            mControllers.setVisibility(View.VISIBLE);
        } else {
            mControllers.setVisibility(View.INVISIBLE);
        }
    }

    private RendererBuilder getRendererBuilder() {
        String userAgent = getUserAgent(this);
        Log.e(TAG, "getRendererBuilder " + "contentType " + contentType);
        switch (contentType) {
            case TYPE_SS_VOD:
                return new SmoothStreamingRendererBuilder(this, userAgent, contentUri.toString(),
                        contentId);
            case TYPE_DASH_VOD:
                return new DashVodRendererBuilder(this, userAgent, contentUri.toString(), contentId);
            default:
                return new DefaultRendererBuilder(this, contentUri);
        }
    }

    public static String getUserAgent(Context context) {
        String versionName;
        try {
            String packageName = context.getPackageName();
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            versionName = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "?";
        }
        return "ExoPlayerDemo/" + versionName + " (Linux;Android " + Build.VERSION.RELEASE +
                ") " + "ExoPlayerLib/" + ExoPlayerLibraryInfo.VERSION;
    }

    private class HideControllersTask extends TimerTask {
        @Override
        public void run() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    updateControllersVisibility(false);
                    mControllersVisible = false;
                }
            });

        }
    }
}

