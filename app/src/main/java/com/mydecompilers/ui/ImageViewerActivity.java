/*
 * Apk Decompiler Pro (Java / AIDE port)
 * Original Kotlin project: Copyright (C) 2026 Ameer Muawiya
 * Licensed under the GNU General Public License v3.0 or later (see LICENSE).
 */
package com.mydecompilers.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

import com.mydecompilers.Constants;
import com.mydecompilers.R;
import com.mydecompilers.utils.ShareUtils;

import java.io.File;

/** Image viewer with pinch-to-zoom and drag. */
public class ImageViewerActivity extends BaseActivity {

    private File file;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String path = getIntent().getStringExtra(Constants.EXTRA_PATH);
        if (path == null) {
            finish();
            return;
        }
        file = new File(path);
        setTitle(file.getName());

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int sample = 1;
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(path, opts);
        if (bmp == null) {
            toast("Could not decode image");
            finish();
            return;
        }
        ZoomImageView view = new ZoomImageView(this);
        view.setImageBitmap(bmp);
        view.setBackgroundColor(p.dark ? 0xff0b0f14 : 0xffe9edf4);
        setContentView(view);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Share")
                .setIcon(UiStyle.tinted(this, R.drawable.ic_share, 0xffffffff))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            ShareUtils.shareFile(this, file, file.getName());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** ImageView driven by a Matrix. */
    private static class ZoomImageView extends ImageView {
        private final Matrix matrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private float lastX, lastY;
        private boolean dragging;
        private boolean fitted = false;

        ZoomImageView(Context c) {
            super(c);
            setScaleType(ScaleType.MATRIX);
            scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    matrix.postScale(d.getScaleFactor(), d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                    setImageMatrix(matrix);
                    return true;
                }
            });
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (!fitted && getDrawable() != null && w > 0 && h > 0) {
                float s = Math.min((float) w / getDrawable().getIntrinsicWidth(), (float) h / getDrawable().getIntrinsicHeight());
                matrix.setScale(s, s);
                matrix.postTranslate((w - getDrawable().getIntrinsicWidth() * s) / 2f, (h - getDrawable().getIntrinsicHeight() * s) / 2f);
                setImageMatrix(matrix);
                fitted = true;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            scaleDetector.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = e.getX();
                    lastY = e.getY();
                    dragging = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (dragging && !scaleDetector.isInProgress() && e.getPointerCount() == 1) {
                        matrix.postTranslate(e.getX() - lastX, e.getY() - lastY);
                        setImageMatrix(matrix);
                    }
                    lastX = e.getX();
                    lastY = e.getY();
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    int idx = e.getActionIndex() == 0 ? 1 : 0;
                    lastX = e.getX(idx);
                    lastY = e.getY(idx);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    break;
            }
            return true;
        }
    }
}
