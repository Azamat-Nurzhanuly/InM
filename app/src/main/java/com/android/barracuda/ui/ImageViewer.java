package com.android.barracuda.ui;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.barracuda.MainActivity;
import com.android.barracuda.R;
import com.android.barracuda.data.StaticConfig;
import com.bumptech.glide.Glide;

import java.util.Objects;

import uk.co.senab.photoview.PhotoViewAttacher;

public class ImageViewer extends MainActivity {

  private ImageView mImageView;
  private String url;
  private String name;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.image_view);

    mImageView = (ImageView) findViewById(R.id.image);
    url = getIntent().getStringExtra(StaticConfig.IMAGE_URL);
    name = getIntent().getStringExtra(StaticConfig.INTENT_KEY_CHAT_FRIEND);

    try {
      Objects.requireNonNull(getSupportActionBar()).setTitle(name);
      getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.black)));

      Glide.with(this)
        .load(url)
        .into(mImageView);

      PhotoViewAttacher pAttacher;
      pAttacher = new PhotoViewAttacher(mImageView);
      pAttacher.update();

    } catch (Exception e) {
      Log.getStackTraceString(e);
    }

  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_media, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {

    switch (item.getItemId()) {
      case R.id.about: {
        downLoadImage();
        break;
      }
    }

    return true;
  }

  private void downLoadImage() {

    Toast.makeText(this, "Сохранение завершено", Toast.LENGTH_LONG).show();
  }
}