package com.android.barracuda.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import com.android.barracuda.MainActivity;
import com.android.barracuda.R;
import com.android.barracuda.data.StaticConfig;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Util;

public class VideoViewer extends MainActivity {

  private SimpleExoPlayerView simpleExoPlayerView;
  private SimpleExoPlayer player;

  private Timeline.Window window;
  private DataSource.Factory mediaDataSourceFactory;
  private DefaultTrackSelector trackSelector;
  private boolean shouldAutoPlay;
  private BandwidthMeter bandwidthMeter;
  private String contentUrl;

  private ImageView ivHideControllerButton;


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_video_player);


    shouldAutoPlay = true;
    bandwidthMeter = new DefaultBandwidthMeter();
    mediaDataSourceFactory = new DefaultDataSourceFactory(this, Util.getUserAgent(this, "mediaPlayerSample"), (TransferListener<? super DataSource>) bandwidthMeter);
    window = new Timeline.Window();
    ivHideControllerButton = (ImageView) findViewById(R.id.exo_controller);
    contentUrl = getIntent().getStringExtra(StaticConfig.VIDEO_URL);
  }

  private void initializePlayer() {

    simpleExoPlayerView = (SimpleExoPlayerView) findViewById(R.id.player_view);
    simpleExoPlayerView.requestFocus();

    TrackSelection.Factory videoTrackSelectionFactory =
      new AdaptiveTrackSelection.Factory(bandwidthMeter);

    trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

    player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);

    simpleExoPlayerView.setPlayer(player);

    player.setPlayWhenReady(shouldAutoPlay);
/*        MediaSource mediaSource = new HlsMediaSource(Uri.parse("https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8"),
                mediaDataSourceFactory, mainHandler, null);*/

    DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();

    MediaSource mediaSource = new ExtractorMediaSource(Uri.parse(this.contentUrl),
      mediaDataSourceFactory, extractorsFactory, null, null);

    player.prepare(mediaSource);

    if (ivHideControllerButton != null)
      ivHideControllerButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          simpleExoPlayerView.hideController();
        }
      });
  }

  private void releasePlayer() {
    if (player != null) {
      shouldAutoPlay = player.getPlayWhenReady();
      player.release();
      player = null;
      trackSelector = null;
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    if (Util.SDK_INT > 23) {
      initializePlayer();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    if ((Util.SDK_INT <= 23 || player == null)) {
      initializePlayer();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      releasePlayer();
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    if (Util.SDK_INT > 23) {
      releasePlayer();
    }
  }
}